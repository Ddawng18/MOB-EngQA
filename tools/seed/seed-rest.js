/*
 * EngQA seed over the Firestore / Identity Toolkit REST APIs.
 *
 * Why this exists: `firebase-admin` (used by seed.js) requires a service-account key or ADC.
 * This script instead reuses the OAuth session the Firebase CLI already stored, so seeding works
 * with nothing but `firebase login`.
 *
 * It reuses the exact same write plan as seed.js (buildPlan), so both writers stay in sync.
 *
 * Usage:
 *   node seed-rest.js --dry-run     # print the plan, touch nothing
 *   node seed-rest.js               # write the data
 *   node seed-rest.js --clear       # delete documents flagged isSample + the demo auth accounts
 *
 * Options:
 *   --project <id>    override the project (default: read from app/google-services.json)
 *   --token <token>   use an explicit access token (default: the Firebase CLI session)
 *
 * Environment:
 *   FIREBASE_ACCESS_TOKEN   alternative to --token
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const { buildPlan } = require('./seed');

const args = process.argv.slice(2);
const DRY_RUN = args.includes('--dry-run');
const CLEAR = args.includes('--clear');
const SKIP_AUTH = args.includes('--no-auth');

function argValue(flag) {
  const index = args.indexOf(flag);
  return index >= 0 ? args[index + 1] : undefined;
}

const projectArg = argValue('--project') || process.env.FIREBASE_PROJECT;
const tokenArg = argValue('--token') || process.env.FIREBASE_ACCESS_TOKEN;

const TOP_LEVEL_COLLECTIONS = ['questions', 'centers', 'categories', 'users', 'usernames', 'notifications', 'reports'];
const COLLECTION_GROUPS = ['answers', 'votes', 'courses', 'posts'];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// ------------------------------------------------------------------ project / token

function detectProjectId() {
  if (projectArg) {
    return projectArg;
  }
  const candidates = [
    path.resolve(__dirname, '..', '..', 'app', 'google-services.json'),
    path.resolve(__dirname, 'google-services.json'),
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      const json = JSON.parse(fs.readFileSync(candidate, 'utf8'));
      if (json.project_info && json.project_info.project_id) {
        return json.project_info.project_id;
      }
    }
  }
  throw new Error('Không tìm thấy project id. Truyền --project <id>.');
}

function loadAccessToken() {
  if (tokenArg) {
    return tokenArg;
  }
  const storePath = path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json');
  if (!fs.existsSync(storePath)) {
    throw new Error('Không thấy phiên Firebase CLI. Chạy "firebase login" trước.');
  }
  const store = JSON.parse(fs.readFileSync(storePath, 'utf8'));
  const tokens = store.tokens || {};
  const expiresAt = Number(tokens.expires_at || 0);
  if (expiresAt && Date.now() > expiresAt - 60_000) {
    throw new Error('Phiên Firebase CLI đã hết hạn. Chạy lại "firebase login" rồi thử lại.');
  }
  if (!tokens.access_token) {
    throw new Error('Không có access token trong phiên CLI. Chạy "firebase login".');
  }
  return tokens.access_token;
}

// ------------------------------------------------------------------ http

async function api(url, { method = 'GET', body, token } = {}) {
  let lastError;
  // Some networks route *.googleapis.com hosts through flaky address ranges; retry generously.
  for (let attempt = 1; attempt <= 10; attempt++) {
    try {
      const response = await fetch(url, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: body ? JSON.stringify(body) : undefined,
      });
      const text = await response.text();
      const json = text ? JSON.parse(text) : {};
      if (response.ok) {
        return json;
      }
      const error = new Error(`${response.status} ${(json.error && json.error.message) || text}`);
      error.status = response.status;
      error.body = json;
      // Retry only transient failures (5xx / 429 / network).
      if (response.status < 500 && response.status !== 429) {
        throw error;
      }
      lastError = error;
    } catch (error) {
      if (error.status && error.status < 500 && error.status !== 429) {
        throw error;
      }
      lastError = error;
    }
    await sleep(Math.min(1000 * attempt, 5000));
  }
  throw lastError;
}

// ------------------------------------------------------------------ value encoding

/** Converts a plain JS value (as produced by buildPlan) into a Firestore REST Value. */
function toValue(value) {
  if (value === null || value === undefined) {
    return { nullValue: null };
  }
  if (typeof value === 'string') {
    return { stringValue: value };
  }
  if (typeof value === 'boolean') {
    return { booleanValue: value };
  }
  if (typeof value === 'number') {
    return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  }
  if (Array.isArray(value)) {
    return { arrayValue: { values: value.map(toValue) } };
  }
  if (typeof value === 'object') {
    // firebase-admin Timestamp and the dry-run stub both expose `.seconds`.
    if (typeof value.seconds === 'number') {
      const millis = value.seconds * 1000 + Math.floor((value.nanoseconds || 0) / 1e6);
      return { timestampValue: new Date(millis).toISOString() };
    }
    const fields = {};
    for (const key of Object.keys(value)) {
      fields[key] = toValue(value[key]);
    }
    return { mapValue: { fields } };
  }
  throw new Error(`Unsupported value: ${value}`);
}

function toFields(data) {
  const fields = {};
  for (const key of Object.keys(data)) {
    fields[key] = toValue(data[key]);
  }
  return fields;
}

function documentName(projectId, documentPath) {
  return `projects/${projectId}/databases/(default)/documents/${documentPath}`;
}

// ------------------------------------------------------------------ writers

async function createAuthUser(projectId, token, authUser) {
  const url = `https://identitytoolkit.googleapis.com/v1/projects/${projectId}/accounts`;
  try {
    await api(url, {
      method: 'POST',
      token,
      body: {
        localId: authUser.uid,
        email: authUser.email,
        password: authUser.password,
        displayName: authUser.displayName,
        emailVerified: true,
      },
    });
    return true;
  } catch (error) {
    if (error.status === 409 || /DUPLICATE|EXISTS|already/i.test(error.message)) {
      return false;
    }
    throw error;
  }
}

async function commit(projectId, token, writes) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:commit`;
  for (let index = 0; index < writes.length; index += 400) {
    const chunk = writes.slice(index, index + 400);
    await api(url, { method: 'POST', token, body: { writes: chunk } });
  }
}

async function deleteAccount(projectId, token, uid) {
  const url = `https://identitytoolkit.googleapis.com/v1/projects/${projectId}/accounts:delete`;
  try {
    await api(url, { method: 'POST', token, body: { localId: uid } });
  } catch (error) {
    if (error.status !== 404) {
      throw error;
    }
  }
}

function sampleFilter() {
  return {
    fieldFilter: {
      field: { fieldPath: 'isSample' },
      op: 'EQUAL',
      value: { booleanValue: true },
    },
  };
}

async function querySampleDocuments(projectId, token, collectionId, allDescendants) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:runQuery`;
  const response = await api(url, {
    method: 'POST',
    token,
    body: {
      structuredQuery: {
        from: [{ collectionId, allDescendants }],
        where: sampleFilter(),
        limit: 300,
      },
    },
  });
  // runQuery returns an array of { document } entries (and an empty object when nothing matches).
  return (Array.isArray(response) ? response : [])
    .filter((entry) => entry && entry.document)
    .map((entry) => entry.document.name);
}

async function clearSampleData(projectId, token) {
  const writes = [];
  for (const collectionId of TOP_LEVEL_COLLECTIONS) {
    const names = await querySampleDocuments(projectId, token, collectionId, false);
    console.log(`  ${collectionId}: ${names.length} sample document(s)`);
    names.forEach((name) => writes.push({ delete: name }));
  }
  for (const collectionId of COLLECTION_GROUPS) {
    const names = await querySampleDocuments(projectId, token, collectionId, true);
    // answers/votes/courses/posts are already removed with their parent, but be thorough.
    console.log(`  ${collectionId} (group): ${names.length} sample document(s)`);
    names.forEach((name) => writes.push({ delete: name }));
  }
  if (writes.length) {
    await commit(projectId, token, writes);
  }
  const users = require('./data/users.json');
  for (const user of users) {
    await deleteAccount(projectId, token, user.uid);
  }
  return writes.length;
}

// ------------------------------------------------------------------ main

async function main() {
  const projectId = detectProjectId();

  if (CLEAR) {
    console.log(`Clear plan for project ${projectId}`);
    if (DRY_RUN) {
      console.log('  [dry-run] would delete sample documents + demo auth accounts.');
      return;
    }
    const token = loadAccessToken();
    const deleted = await clearSampleData(projectId, token);
    console.log(`Deleted ${deleted} document(s) and ${require('./data/users.json').length} auth account(s).`);
    return;
  }

  const { writes, counters } = buildPlan();

  console.log(`EngQA REST seed → project ${projectId}`);
  console.log(`  documents  : ${writes.length}`);
  console.log(`  auth users : ${writes.filter((w) => w.authUser).length}`);
  console.log('');
  for (const user of require('./data/users.json')) {
    const count = counters.get(user.uid) || {};
    console.log(`  ${user.username.padEnd(14)} ${user.email.padEnd(30)} ` +
      `q=${count.questionCount || 0} a=${count.answerCount || 0} accepted=${count.acceptedCount || 0}`);
  }
  console.log('');

  if (DRY_RUN) {
    console.log('[dry-run] no network calls were made.');
    return;
  }

  const token = loadAccessToken();

  let createdAccounts = 0;
  if (SKIP_AUTH) {
    console.log('  (--no-auth) skipping Firebase Auth account creation; writing Firestore data only.');
  } else {
    for (const write of writes) {
      if (write.authUser) {
        const created = await createAuthUser(projectId, token, write.authUser);
        createdAccounts += created ? 1 : 0;
        console.log(`  auth ${created ? 'created' : 'exists '} ${write.authUser.email}`);
      }
    }
  }

  await commit(projectId, token, writes.map((write) => ({
    update: {
      name: documentName(projectId, write.path),
      fields: toFields(write.data),
    },
  })));

  console.log('');
  console.log(`Done. Wrote ${writes.length} documents and created ${createdAccounts} auth account(s).`);
  console.log('Demo login (password "Engqa@1234"): ' +
    require('./data/users.json').map((user) => user.email).join(', '));
}

main().catch((error) => {
  console.error('REST seed failed:', error.message || error);
  process.exit(1);
});
