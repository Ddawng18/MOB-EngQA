/*
 * Registers an Android signing certificate (SHA-1) with the Firebase Android app and downloads the
 * refreshed google-services.json. Useful when the debug keystore changes (new machine / fresh
 * ~/.android/debug.keystore), which otherwise makes Google Sign-In fail with DEVELOPER_ERROR (10).
 *
 * Usage:
 *   node tools/fix-google-signin.js 0fac9154902993d7d5099f35468faa4fbc322672
 *
 * The first argument is the SHA-1 as 40 lowercase hex characters (no colons). Get it with:
 *   keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android
 *
 * Requires a recent `firebase login` session (the access token is read from the CLI's configstore).
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const PACKAGE_NAME = 'com.example.engqa';
const API = 'https://firebase.googleapis.com/v1beta1';

function loadToken() {
  const storePath = path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json');
  const store = JSON.parse(fs.readFileSync(storePath, 'utf8'));
  const token = store.tokens && store.tokens.access_token;
  if (!token) {
    throw new Error('Không có access token. Chạy "firebase login" trước.');
  }
  return token;
}

function projectId() {
  const gsPath = path.resolve(__dirname, '..', 'app', 'google-services.json');
  return JSON.parse(fs.readFileSync(gsPath, 'utf8')).project_info.project_id;
}

async function call(url, token, options = {}) {
  const response = await fetch(url, {
    method: options.method || 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  const text = await response.text();
  const json = text ? JSON.parse(text) : {};
  if (!response.ok && response.status !== 409) {
    throw new Error(`${response.status} ${(json.error && json.error.message) || text}`);
  }
  return { status: response.status, json };
}

async function main() {
  const sha = (process.argv[2] || '').replace(/[^0-9a-fA-F]/g, '').toLowerCase();
  if (sha.length !== 40) {
    console.error('Cần SHA-1 dạng 40 ký tự hex (bỏ dấu ":").');
    process.exit(1);
  }

  const token = loadToken();
  const project = projectId();
  console.log(`Project: ${project}`);
  console.log(`SHA-1  : ${sha}`);

  const apps = await call(`${API}/projects/${project}/androidApps`, token);
  const app = (apps.json.apps || []).find((item) => item.packageName === PACKAGE_NAME);
  if (!app) {
    throw new Error(`Không tìm thấy Android app cho package ${PACKAGE_NAME}`);
  }
  console.log(`App    : ${app.appId}`);
  console.log(`Đã có SHA: ${(app.sha1Hashes || []).join(', ') || '(chưa có)'}`);

  const add = await call(
      `${API}/projects/${project}/androidApps/${app.appId}/sha`,
      token,
      { method: 'POST', body: { shaHash: sha, certType: 'SHA_1' } });
  console.log(add.status === 409 ? 'SHA-1 đã tồn tại, bỏ qua.' : 'Đã thêm SHA-1.');

  // Give the config a moment to include the new certificate.
  await new Promise((resolve) => setTimeout(resolve, 4000));

  const config = await call(
      `${API}/projects/${project}/androidApps/${app.appId}/config`, token);
  const contents = config.json.configFileContents;
  if (!contents) {
    throw new Error('Không tải được config: ' + JSON.stringify(config.json).slice(0, 300));
  }
  const decoded = Buffer.from(contents, 'base64').toString('utf8');
  const outPath = path.resolve(__dirname, '..', 'app', 'google-services.json');
  fs.writeFileSync(outPath, decoded);
  console.log(`Đã ghi ${outPath}`);

  const updated = JSON.parse(decoded);
  const client = (updated.client || [])[0] || {};
  const oauth = client.oauth_client || [];
  const androidHashes = oauth
      .filter((entry) => entry.android_info)
      .map((entry) => entry.android_info.certificate_hash);
  const webClient = oauth.find((entry) => entry.client_type === 3);
  console.log('SHA-1 trong config :', androidHashes.join(', '));
  console.log('Web client id      :', webClient ? webClient.client_id : '(không có)');
  console.log('=> Nếu web client id khác res/values/strings.xml, cập nhật default_web_client_id.');
}

main().catch((error) => {
  console.error('Lỗi:', error.message || error);
  process.exit(1);
});
