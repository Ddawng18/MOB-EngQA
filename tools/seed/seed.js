/*
 * EngQA Firestore seed.
 *
 * Reads the JSON datasets in ./data and writes:
 *   - categories/{slug}                      (9 topics)
 *   - users/{uid}                            (+ Firebase Auth accounts for the demo users)
 *   - questions/{qid}                        (+ answers and votes sub-collections)
 *   - centers/{cid}                          (+ courses and posts sub-collections)
 *
 * Every seeded document carries `isSample: true` so it can be told apart from real data and removed
 * again with `npm run seed:clear`.
 *
 * The searchKeywords are produced by the *same* normalisation the app uses (util/TextUtils2):
 * lower-case, Vietnamese diacritics stripped, full tokens plus bounded prefixes of at least
 * 2 characters.
 *
 * Usage:
 *   cd tools/seed
 *   npm install
 *   set GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\serviceAccount.json
 *   npm run seed          # write the data
 *   npm run seed:dry      # print the plan without writing anything
 *   npm run seed:clear    # delete previously seeded (isSample) documents
 *
 * The service account must belong to the same Firebase project as app/google-services.json.
 */
const fs = require('fs');
const path = require('path');

const args = process.argv.slice(2);
const DRY_RUN = args.includes('--dry-run');
const CLEAR = args.includes('--clear');

// firebase-admin is only needed for the Admin-SDK path. `--dry-run` and the REST seeder
// (seed-rest.js) reuse buildPlan() before `npm install`, so the dependency is optional here.
let admin;
let hasAdmin = true;
try {
  admin = require('firebase-admin');
} catch (error) {
  hasAdmin = false;
  const firestoreStub = () => ({
    doc: () => ({}),
    collection: () => ({
      where: () => ({ get: async () => ({ empty: true, docs: [], size: 0 }) }),
    }),
    recursiveDelete: async () => {},
  });
  firestoreStub.Timestamp = {
    fromDate: (date) => ({ seconds: Math.floor(date.getTime() / 1000) }),
  };
  admin = {
    initializeApp: () => {},
    credential: { applicationDefault: () => null },
    firestore: firestoreStub,
    auth: () => ({}),
  };
}

let db;
let auth;
if (hasAdmin && !DRY_RUN) {
  admin.initializeApp({ credential: admin.credential.applicationDefault() });
  db = admin.firestore();
  auth = admin.auth();
}

const DATA_DIR = path.join(__dirname, 'data');

function readData(file) {
  return JSON.parse(fs.readFileSync(path.join(DATA_DIR, file), 'utf8'));
}

const categories = readData('categories.json');
const users = readData('users.json');
const questions = readData('questions.json');
const centers = readData('centers.json');
const promos = readData('promos.json');

const categoryBySlug = new Map(categories.map((category) => [category.slug, category]));
const usernameByUid = new Map(users.map((user) => [user.uid, user.username]));
const centerById = new Map(centers.map((center) => [center.id, center]));
const sampleProfileCreatedAtHours = 200;

// --------------------------------------------------------------------- text

function stripDiacritics(input) {
  return input
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D');
}

function tokenize(text) {
  const cleaned = stripDiacritics(String(text || '')).toLowerCase().replace(/[^a-z0-9\s]/g, ' ');
  const seen = new Set();
  for (const token of cleaned.split(/\s+/)) {
    if (token.length >= 2) {
      seen.add(token);
    }
  }
  return [...seen];
}

const MAX_SEARCH_KEYWORDS = 900;

function addFullTokens(keywords, tokens) {
  for (const token of tokens) {
    if (keywords.size >= MAX_SEARCH_KEYWORDS) return;
    keywords.add(token);
  }
}

function addPrefixes(keywords, tokens) {
  for (const token of tokens) {
    for (let length = 2; length < token.length; length++) {
      if (keywords.size >= MAX_SEARCH_KEYWORDS) return;
      keywords.add(token.slice(0, length));
    }
  }
}

// Must stay in sync with Android util/TextUtils2: full tokens + bounded prefixes for
// title/body/tags, so partial words such as "pha" can find "pháp".
function searchKeywords(title, body, tags) {
  const titleTokens = tokenize(title);
  const bodyTokens = tokenize(body);
  const tagTokens = [];
  for (const tag of tags || []) {
    tagTokens.push(...tokenize(tag));
  }
  const keywords = new Set();
  addFullTokens(keywords, titleTokens);
  addFullTokens(keywords, bodyTokens);
  addFullTokens(keywords, tagTokens);
  addPrefixes(keywords, titleTokens);
  addPrefixes(keywords, tagTokens);
  addPrefixes(keywords, bodyTokens);
  return [...keywords].slice(0, MAX_SEARCH_KEYWORDS);
}

function timestampFromOffsetHours(hours) {
  return admin.firestore.Timestamp.fromDate(new Date(Date.now() - hours * 3600 * 1000));
}

function answerIdFor(questionId, index) {
  return `${questionId}-a${index + 1}`;
}

// --------------------------------------------------------------------- plan

/**
 * Builds the complete write plan in memory so the dry run and the real run share one code path.
 */
function buildPlan() {
  const counters = new Map();
  const bump = (uid, field) => {
    if (!counters.has(uid)) {
      counters.set(uid, { questionCount: 0, answerCount: 0, acceptedCount: 0 });
    }
    counters.get(uid)[field] += 1;
  };

  for (const question of questions) {
    bump(question.authorUid, 'questionCount');
    (question.answers || []).forEach((answer, index) => {
      bump(answer.authorUid, 'answerCount');
      if (question.acceptedAnswerIndex === index) {
        bump(answer.authorUid, 'acceptedCount');
      }
    });
  }

  const writes = [];

  categories.forEach((category) => {
    writes.push({
      path: `categories/${category.slug}`,
      data: { name: category.name, order: category.order, isSample: true },
    });
  });

  users.forEach((user) => {
    const count = counters.get(user.uid) || { questionCount: 0, answerCount: 0, acceptedCount: 0 };
    writes.push({
      path: `users/${user.uid}`,
      data: {
        username: user.username,
        email: user.email,
        level: user.level,
        avatarUrl: user.avatarUrl || null,
        role: user.role || 'USER',
        centerId: user.centerId || null,
        questionCount: count.questionCount,
        answerCount: count.answerCount,
        acceptedCount: count.acceptedCount,
        isSample: true,
        createdAt: timestampFromOffsetHours(sampleProfileCreatedAtHours),
      },
      authUser: {
        uid: user.uid,
        email: user.email,
        password: user.password,
        displayName: user.username,
      },
    });
  });

  questions.forEach((question) => {
    const category = categoryBySlug.get(question.categorySlug);
    if (!category) {
      throw new Error(`Unknown categorySlug "${question.categorySlug}" in questions.json`);
    }
    const answers = question.answers || [];
    const tags = [category.name];

    let score = 0;
    answers.forEach((answer) => {
      (answer.votes || []).forEach((vote) => {
        score += vote.value;
      });
    });

    const acceptedAnswerId =
      question.acceptedAnswerIndex != null && answers[question.acceptedAnswerIndex]
        ? answerIdFor(question.id, question.acceptedAnswerIndex)
        : null;

    const createdAt = timestampFromOffsetHours(question.createdAtOffsetHours || 0);
    writes.push({
      path: `questions/${question.id}`,
      data: {
        authorId: question.authorUid,
        authorName: usernameByUid.get(question.authorUid) || 'user',
        title: question.title,
        body: question.body,
        categoryId: category.slug,
        categoryName: category.name,
        tags,
        level: question.level,
        type: category.slug,
        imageUrls: question.imageUrls || [],
        audioUrl: question.audioUrl || null,
        passageText: question.passageText || '',
        searchKeywords: searchKeywords(question.title, question.body, tags),
        answerCount: answers.length,
        score,
        isSolved: Boolean(question.isSolved && acceptedAnswerId),
        acceptedAnswerId,
        isSample: true,
        createdAt,
        updatedAt: createdAt,
      },
    });

    answers.forEach((answer, index) => {
      const answerId = answerIdFor(question.id, index);
      const votes = answer.votes || [];
      const upCount = votes.filter((vote) => vote.value === 1).length;
      const downCount = votes.filter((vote) => vote.value === -1).length;
      const isAccepted = question.acceptedAnswerIndex === index;

      writes.push({
        path: `questions/${question.id}/answers/${answerId}`,
        data: {
          questionId: question.id,
          authorId: answer.authorUid,
          authorName: usernameByUid.get(answer.authorUid) || 'user',
          body: answer.body,
          upCount,
          downCount,
          isAccepted,
          isSample: true,
          createdAt: timestampFromOffsetHours(answer.createdAtOffsetHours || 0),
        },
      });

      votes.forEach((vote) => {
        writes.push({
          path: `questions/${question.id}/answers/${answerId}/votes/${vote.uid}`,
          data: { value: vote.value, isSample: true },
        });
      });
    });
  });

  centers.forEach((center) => {
    const createdAt = timestampFromOffsetHours(center.createdAtOffsetHours || 0);
    writes.push({
      path: `centers/${center.id}`,
      data: {
        name: center.name,
        logoUrl: center.logoUrl || null,
        description: center.description,
        address: center.address,
        city: center.city || '',
        website: center.website || '',
        phone: center.phone,
        email: center.email,
        ownerUid: center.ownerUid || null,
        verified: Boolean(center.verified),
        managerUids: center.managerUids || [],
        status: center.status,
        isSample: true,
        createdAt,
      },
    });
    (center.courses || []).forEach((course) => {
      writes.push({
        path: `centers/${center.id}/courses/${course.id}`,
        data: {
          title: course.title,
          description: course.description,
          level: course.level,
          schedule: course.schedule,
          isSample: true,
          createdAt,
        },
      });
    });
    (center.posts || []).forEach((post) => {
      writes.push({
        path: `centers/${center.id}/posts/${post.id}`,
        data: {
          title: post.title,
          body: post.body,
          imageUrl: post.imageUrl || null,
          isSample: true,
          createdAt,
        },
      });
    });
  });

  promos.forEach((promo) => {
    const center = centerById.get(promo.centerId);
    if (!center) {
      throw new Error(`Unknown centerId "${promo.centerId}" in promos.json`);
    }
    const leads = promo.leads || [];
    const leadCount = leads.length > 0 ? leads.length : (promo.leadCount || 0);
    const createdAt = timestampFromOffsetHours(promo.createdAtOffsetHours || 0);
    const endAt = promo.endAtInDays != null
        ? admin.firestore.Timestamp.fromDate(
            new Date(Date.now() + promo.endAtInDays * 24 * 3600 * 1000))
        : null;

    writes.push({
      path: `promos/${promo.id}`,
      data: {
        centerId: promo.centerId,
        centerName: center.name,
        centerVerified: Boolean(center.verified),
        authorUid: promo.authorUid,
        title: promo.title,
        body: promo.body,
        type: promo.type,
        level: promo.level || '',
        fee: promo.fee || '',
        schedule: promo.schedule || '',
        contactPhone: promo.contactPhone || '',
        linkUrl: promo.linkUrl || '',
        imageUrl: promo.imageUrl || null,
        status: promo.status,
        viewCount: promo.viewCount || 0,
        clickCount: promo.clickCount || 0,
        leadCount,
        isSample: true,
        createdAt,
        endAt,
      },
    });

    leads.forEach((lead) => {
      writes.push({
        path: `promos/${promo.id}/leads/${lead.uid}`,
        data: {
          userUid: lead.uid,
          name: lead.name,
          phone: lead.phone,
          note: lead.note || '',
          status: lead.status || 'NEW',
          isSample: true,
          createdAt: timestampFromOffsetHours(lead.createdAtOffsetHours || 0),
        },
      });
    });
  });

  return { writes, counters };
}

// --------------------------------------------------------------------- apply

async function writeDocument(write) {
  if (DRY_RUN) {
    return;
  }
  await db.doc(write.path).set(write.data, { merge: true });
}

async function ensureAuthUser(authUser) {
  if (DRY_RUN) {
    console.log(`[dry-run] would ensure auth account ${authUser.email}`);
    return true;
  }
  try {
    await auth.getUser(authUser.uid);
    return false;
  } catch (error) {
    if (error.code !== 'auth/user-not-found') {
      throw error;
    }
  }
  await auth.createUser({
    uid: authUser.uid,
    email: authUser.email,
    password: authUser.password,
    displayName: authUser.displayName,
    emailVerified: true,
  });
  return true;
}

async function clearSampleData() {
  if (DRY_RUN) {
    console.log(`[dry-run] would delete sample documents in: ` +
      'questions, centers, categories, users, notifications, reports');
    console.log(`[dry-run] would delete ${users.length} demo auth account(s).`);
    return;
  }
  const collections = ['questions', 'centers', 'categories', 'users', 'notifications', 'reports'];
  for (const name of collections) {
    const snapshot = await db.collection(name).where('isSample', '==', true).get();
    if (snapshot.empty) {
      continue;
    }
    console.log(`Clearing ${snapshot.size} sample document(s) from ${name}...`);
    for (const document of snapshot.docs) {
      // recursiveDelete also removes the answers/votes/courses/posts sub-collections.
      await db.recursiveDelete(document.ref);
    }
  }
  for (const user of users) {
    try {
      await auth.deleteUser(user.uid);
    } catch (error) {
      if (error.code !== 'auth/user-not-found') {
        throw error;
      }
    }
  }
}

async function main() {
  if (!DRY_RUN && !hasAdmin) {
    console.error('Missing dependency "firebase-admin". Run "npm install", or use "npm run seed:rest".');
    process.exit(1);
  }
  if (CLEAR) {
    console.log(DRY_RUN ? '[dry-run] clear plan' : 'Clearing previous sample data...');
    await clearSampleData();
    if (!DRY_RUN) {
      console.log('Clear complete.');
    }
    return;
  }

  const { writes, counters } = buildPlan();

  console.log('EngQA seed plan');
  console.log(`  categories : ${categories.length}`);
  console.log(`  users      : ${users.length}`);
  console.log(`  questions  : ${questions.length}`);
  console.log(`  centers    : ${centers.length}`);
  console.log(`  promos     : ${promos.length}`);
  console.log(`  documents  : ${writes.length}`);
  console.log(`  auth users : ${users.length}`);
  console.log('');

  for (const user of users) {
    const count = counters.get(user.uid) || {};
    console.log(`  ${user.username.padEnd(14)} ${user.email.padEnd(30)} ` +
      `q=${count.questionCount || 0} a=${count.answerCount || 0} accepted=${count.acceptedCount || 0}`);
  }
  console.log('');

  let createdAccounts = 0;
  for (const write of writes) {
    if (write.authUser) {
      const created = await ensureAuthUser(write.authUser);
      createdAccounts += created ? 1 : 0;
    }
    await writeDocument(write);
  }

  if (DRY_RUN) {
    console.log('[dry-run] no documents were written.');
  } else {
    console.log(`Done. Wrote ${writes.length} documents and created ${createdAccounts} auth account(s).`);
    console.log('');
    console.log('Demo login (all accounts use the password "Engqa@1234"):');
    users.forEach((user) => console.log(`  ${user.email}  ->  ${user.username}`));
    console.log('');
    console.log('Remember: admin status is granted by creating admins/{uid} in the Firebase Console.');
  }
}

module.exports = { buildPlan, searchKeywords, tokenize, stripDiacritics };

if (require.main === module) {
  main().catch((error) => {
    console.error('Seed failed:', error);
    process.exit(1);
  });
}
