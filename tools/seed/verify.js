/*
 * Prints a quick health check of the seeded Firestore data (read-only).
 *
 * Usage: node verify.js
 */
const fs = require('fs');
const os = require('os');
const path = require('path');

const storePath = path.join(os.homedir(), '.config', 'configstore', 'firebase-tools.json');
const store = JSON.parse(fs.readFileSync(storePath, 'utf8'));
const token = store.tokens.access_token;

const gsPath = path.resolve(__dirname, '..', '..', 'app', 'google-services.json');
const projectId = JSON.parse(fs.readFileSync(gsPath, 'utf8')).project_info.project_id;
const base = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/`;

async function list(collection, pageSize = 100) {
  const response = await fetch(`${base}${collection}?pageSize=${pageSize}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const json = await response.json();
  if (json.error) {
    throw new Error(`${collection}: ${json.error.message}`);
  }
  return json.documents || [];
}

function field(document, name) {
  const value = document.fields && document.fields[name];
  if (!value) {
    return undefined;
  }
  if (value.stringValue !== undefined) return value.stringValue;
  if (value.booleanValue !== undefined) return value.booleanValue;
  if (value.integerValue !== undefined) return Number(value.integerValue);
  return undefined;
}

async function main() {
  console.log(`Project: ${projectId}`);

  const [categories, questions, centers, users, promos] = await Promise.all([
    list('categories'),
    list('questions'),
    list('centers'),
    list('users'),
    list('promos'),
  ]);

  console.log(`categories : ${categories.length}`);
  console.log(`questions  : ${questions.length}`);
  console.log(`users      : ${users.length}`);
  console.log(`centers    : ${centers.length}`);
  console.log(`promos     : ${promos.length}`);

  console.log('');
  console.log('Promo statuses:');
  promos.forEach((promo) => {
    const id = promo.name.split('/promos/')[1];
    console.log(`  ${id.padEnd(36)} ${field(promo, 'status')}  views=${field(promo, 'viewCount')} leads=${field(promo, 'leadCount')}`);
  });

  console.log('');
  console.log('Centre statuses:');
  centers.forEach((center) => {
    const id = center.name.split('/centers/')[1];
    console.log(`  ${id.padEnd(28)} ${field(center, 'status')}  verified=${field(center, 'verified')}`);
  });

  console.log('');
  const manager = users.find((user) => user.name.endsWith('/demo-center'));
  if (manager) {
    console.log(`demo-center role=${field(manager, 'role')} centerId=${field(manager, 'centerId')}`);
  }

  const approved = promos.filter((promo) => field(promo, 'status') === 'APPROVED');
  const pending = promos.filter((promo) => field(promo, 'status') === 'PENDING');
  console.log(`APPROVED promos: ${approved.length} | PENDING promos: ${pending.length}`);

  const samplePromo = approved[0];
  if (samplePromo) {
    const promoId = samplePromo.name.split('/promos/')[1];
    const leads = await list(`promos/${promoId}/leads`);
    console.log(`leads of ${promoId}: ${leads.length}`);
  }
}

main().catch((error) => {
  console.error('Verify failed:', error.message || error);
  process.exit(1);
});
