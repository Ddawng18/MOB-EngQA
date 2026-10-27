/*
 * One-time, additive migration for the unique-username index.
 *
 * Registration now reserves a display name by creating `usernames/{username}` atomically with the
 * profile. Existing profiles predate that index; this script creates the missing reservation
 * documents without changing any `users` content. It never overwrites an existing reservation.
 *
 * Usage (from tools/seed):
 *   node backfill-usernames.js --dry-run   # default
 *   node backfill-usernames.js --apply
 *
 * Token:
 *   FIREBASE_ACCESS_TOKEN=... node backfill-usernames.js --apply
 *   or --token <token>
 * The Firebase CLI's normal login token cannot be read from a plain configstore once expired; the
 * runner used in this environment refreshes it via firebase-tools before invoking this script.
 */
const fs = require('fs');
const path = require('path');

const args = process.argv.slice(2);
const APPLY = args.includes('--apply');
const projectArg = argValue('--project') || process.env.FIREBASE_PROJECT;
const tokenArg = argValue('--token') || process.env.FIREBASE_ACCESS_TOKEN;
const PAGE_SIZE = 300;
const USERNAME = /^[A-Za-z0-9._-]{3,24}$/;

function argValue(name) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
}

function detectProjectId() {
  if (projectArg) return projectArg;
  const servicesPath = path.join(__dirname, '..', '..', 'app', 'google-services.json');
  const services = JSON.parse(fs.readFileSync(servicesPath, 'utf8'));
  const projectId = services.project_info && services.project_info.project_id;
  if (!projectId) throw new Error('Không đọc được project_id từ google-services.json.');
  return projectId;
}

function requireToken() {
  if (!tokenArg) {
    throw new Error('Thiếu FIREBASE_ACCESS_TOKEN / --token. Đăng nhập Firebase CLI trước.');
  }
  return tokenArg;
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
        const error = new Error(`HTTP ${response.status}: ${text.slice(0, 400)}`);
        error.status = response.status;
        throw error;
      }
      return text ? JSON.parse(text) : {};
    } catch (error) {
      lastError = error;
      if (error.status && error.status < 500) throw error;
      await new Promise((resolve) => setTimeout(resolve, 400 * attempt));
    }
  }
  throw lastError;
}

function stringValue(value) {
  return value && value.stringValue ? value.stringValue : '';
}

async function fetchUsers(projectId, token, offset) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:runQuery`;
  const result = await api(url, {
    method: 'POST',
    token,
    body: {
      structuredQuery: {
        from: [{ collectionId: 'users' }],
        select: { fields: [{ fieldPath: 'username' }] },
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

async function usernameExists(projectId, token, username) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/usernames/${username}`;
  try {
    const doc = await api(url, { token });
    return doc && doc.name ? doc : null;
  } catch (error) {
    if (error.status === 404) return null;
    throw error;
  }
}

async function createReservations(projectId, token, entries) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents:commit`;
  for (let index = 0; index < entries.length; index += 400) {
    const writes = entries.slice(index, index + 400).map((entry) => ({
      update: {
        name: `projects/${projectId}/databases/(default)/documents/usernames/${entry.username}`,
        fields: {
          uid: { stringValue: entry.uid },
          username: { stringValue: entry.username },
        },
      },
      currentDocument: { exists: false },
    }));
    await api(url, { method: 'POST', token, body: { writes } });
  }
}

async function main() {
  const projectId = detectProjectId();
  const token = requireToken();
  console.log(`Username backfill → project ${projectId} (${APPLY ? 'APPLY' : 'dry-run'})`);

  let offset = 0;
  let scanned = 0;
  let invalid = 0;
  const byName = new Map();
  const duplicates = [];

  while (true) {
    const users = await fetchUsers(projectId, token, offset);
    if (!users.length) break;
    for (const user of users) {
      scanned++;
      const uid = user.name.split('/').pop();
      const username = stringValue((user.fields || {}).username).trim();
      if (!USERNAME.test(username)) {
        invalid++;
        continue;
      }
      if (byName.has(username)) {
        duplicates.push({ username, kept: byName.get(username), ignored: uid });
        continue;
      }
      byName.set(username, uid);
    }
    offset += users.length;
    if (users.length < PAGE_SIZE) break;
  }

  let created = 0;
  let conflicts = 0;
  const toCreate = [];
  for (const [username, uid] of byName.entries()) {
    const existing = await usernameExists(projectId, token, username);
    if (existing) {
      const owner = existing.fields && existing.fields.uid
        ? existing.fields.uid.stringValue : null;
      if (owner !== uid) {
        conflicts++;
        console.log(`  conflict: ${username} already reserved by another uid`);
      }
      continue;
    }
    created++;
    toCreate.push({ username, uid });
  }

  console.log(`  profiles scanned : ${scanned}`);
  console.log(`  invalid names    : ${invalid}`);
  console.log(`  duplicate names  : ${duplicates.length}`);
  console.log(`  reserved missing : ${created}`);
  console.log(`  existing conflicts: ${conflicts}`);
  for (const duplicate of duplicates) {
    console.log(`  duplicate: ${duplicate.username} (kept ${duplicate.kept}, ignored ${duplicate.ignored})`);
  }

  if (!APPLY) {
    console.log('[dry-run] no writes were made. Re-run with --apply to create reservations.');
    return;
  }
  if (!toCreate.length) {
    console.log('Nothing to create.');
    return;
  }
  await createReservations(projectId, token, toCreate);
  console.log(`Created ${toCreate.length} username reservation(s).`);
}

main().catch((error) => {
  console.error('Username backfill failed:', error.message || error);
  process.exit(1);
});
