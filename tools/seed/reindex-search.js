/*
 * Rebuilds questions.searchKeywords for existing documents.
 *
 * The app's keyword format gained bounded prefixes and richer normalisation. New questions get
 * this automatically; this script updates only the `searchKeywords` field on existing questions
 * (title/body/tags/content are never modified). It is additive and safe to re-run.
 *
 * Usage (from tools/seed):
 *   node reindex-search.js --dry-run   # default: report how many docs would change
 *   node reindex-search.js --apply     # write only searchKeywords
 *
 * Credentials: reuses the Firebase CLI session, like seed-rest.js. `--project <id>` and
 * `--token <token>` / FIREBASE_ACCESS_TOKEN are supported for CI.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const { searchKeywords } = require('./seed');

const args = process.argv.slice(2);
const APPLY = args.includes('--apply');
const projectArg = argValue('--project') || process.env.FIREBASE_PROJECT;
const tokenArg = argValue('--token') || process.env.FIREBASE_ACCESS_TOKEN;
const PAGE_SIZE = 300;

function argValue(name) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
}

function detectProjectId() {
  if (projectArg) {
    return projectArg;
  }
  const servicesPath = path.join(__dirname, '..', '..', 'app', 'google-services.json');
  const services = JSON.parse(fs.readFileSync(servicesPath, 'utf8'));
  const projectId = services.project_info && services.project_info.project_id;
  if (!projectId) {
    throw new Error('Không đọc được project_id từ app/google-services.json.');
  }
  return projectId;
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

async function api(url, { method = 'GET', body, token } = {}) {
  let lastError;
  for (let attempt = 1; attempt <= 6; attempt++) {
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
      if (!response.ok) {
        const error = new Error(`HTTP ${response.status}: ${text.slice(0, 500)}`);
        error.status = response.status;
        throw error;
      }
      return text ? JSON.parse(text) : {};
    } catch (error) {
      lastError = error;
      if (error.status && error.status < 500) {
        throw error;
      }
      await new Promise((resolve) => setTimeout(resolve, 400 * attempt));
    }
  }
  throw lastError;
}

function stringValue(value) {
  return value && value.stringValue ? value.stringValue : '';
}

function stringArray(value) {
  if (!value || !value.arrayValue || !value.arrayValue.values) {
    return [];
  }
  return value.arrayValue.values.map(stringValue).filter(Boolean);
}

async function fetchQuestionPage(projectId, token, offset) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:runQuery`;
  const result = await api(url, {
    method: 'POST',
    token,
    body: {
      structuredQuery: {
        from: [{ collectionId: 'questions' }],
        select: {
          fields: [
            { fieldPath: 'title' },
            { fieldPath: 'body' },
            { fieldPath: 'tags' },
            { fieldPath: 'searchKeywords' },
          ],
        },
        orderBy: [{ field: { fieldPath: '__name__' }, direction: 'ASCENDING' }],
        offset,
        limit: PAGE_SIZE,
      },
    },
  });
  return (Array.isArray(result) ? result : [])
    .map((entry) => entry.document)
    .filter(Boolean);
}

async function commit(projectId, token, writes) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:commit`;
  for (let index = 0; index < writes.length; index += 400) {
    await api(url, {
      method: 'POST',
      token,
      body: { writes: writes.slice(index, index + 400) },
    });
  }
}

function sameSet(left, right) {
  if (left.length !== right.length) {
    return false;
  }
  const set = new Set(left);
  if (set.size !== right.length) {
    return false;
  }
  return right.every((value) => set.has(value));
}

async function main() {
  const projectId = detectProjectId();
  const token = loadAccessToken();
  console.log(`Search reindex → project ${projectId} (${APPLY ? 'APPLY' : 'dry-run'})`);

  let offset = 0;
  let scanned = 0;
  let changed = 0;
  const writes = [];

  while (true) {
    const documents = await fetchQuestionPage(projectId, token, offset);
    if (!documents.length) {
      break;
    }
    for (const document of documents) {
      scanned++;
      const fields = document.fields || {};
      const title = stringValue(fields.title);
      const body = stringValue(fields.body);
      const tags = stringArray(fields.tags);
      const existing = stringArray(fields.searchKeywords);
      const computed = searchKeywords(title, body, tags);
      if (sameSet(existing, computed)) {
        continue;
      }
      changed++;
      if (APPLY) {
        writes.push({
          update: {
            name: document.name,
            fields: {
              searchKeywords: {
                arrayValue: {
                  values: computed.map((keyword) => ({ stringValue: keyword })),
                },
              },
            },
          },
          // Only search metadata is touched; title/body/tags/answers remain byte-for-byte intact.
          updateMask: { fieldPaths: ['searchKeywords'] },
        });
      }
    }
    offset += documents.length;
    if (documents.length < PAGE_SIZE) {
      break;
    }
  }

  console.log(`  questions scanned : ${scanned}`);
  console.log(`  keywords to update: ${changed}`);

  if (!APPLY) {
    console.log('[dry-run] no writes were made. Re-run with --apply to update searchKeywords.');
    return;
  }
  if (!writes.length) {
    console.log('Nothing to update.');
    return;
  }
  await commit(projectId, token, writes);
  console.log(`Updated searchKeywords on ${writes.length} question(s).`);
}

main().catch((error) => {
  console.error('Search reindex failed:', error.message || error);
  process.exit(1);
});
