const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  addDoc,
} = require('firebase/firestore');

const PROJECT_ID = 'demo-engqa';
let testEnv;

const RULES = fs.readFileSync(path.resolve(__dirname, '..', 'firestore.rules'), 'utf8');

async function seed() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'users/active'), {
      username: 'active', status: 'ACTIVE', role: 'USER', questionCount: 0, answerCount: 0,
    });
    await setDoc(doc(db, 'users/timed'), {
      username: 'timed', status: 'TIMEOUT', role: 'USER',
      timeoutUntil: new Date(Date.now() + 24 * 60 * 60 * 1000),
    });
    await setDoc(doc(db, 'users/expired'), {
      username: 'expired', status: 'TIMEOUT', role: 'USER',
      timeoutUntil: new Date(Date.now() - 60 * 1000),
    });
    await setDoc(doc(db, 'users/deleted'), {
      username: 'deleted', status: 'DELETED', role: 'USER',
    });
    await setDoc(doc(db, 'users/owner'), {
      username: 'owner', status: 'ACTIVE', role: 'USER', questionCount: 1, answerCount: 0,
    });
    await setDoc(doc(db, 'users/answerer'), {
      username: 'answerer', status: 'ACTIVE', role: 'USER', questionCount: 0, answerCount: 1,
    });
    await setDoc(doc(db, 'users/stranger'), {
      username: 'stranger', status: 'ACTIVE', role: 'USER', questionCount: 0, answerCount: 0,
    });
    await setDoc(doc(db, 'admins/admin'), { role: 'ADMIN' });
    await setDoc(doc(db, 'questions/q1'), {
      authorId: 'owner', authorName: 'owner', title: 'Question title',
      body: 'Body', categoryId: 'grammar', categoryName: 'Grammar',
      tags: [], imageUrls: [], searchKeywords: ['question'],
      answerCount: 1, score: 0, isSolved: false,
      createdAt: new Date(), updatedAt: new Date(),
    });
    await setDoc(doc(db, 'questions/q1/answers/a1'), {
      authorId: 'answerer', authorName: 'answerer', body: 'Answer',
      questionTitle: 'Question title', upCount: 0, downCount: 0, isAccepted: false,
      createdAt: new Date(),
    });
    await setDoc(doc(db, 'questions/q1/answers/a1/votes/answerer'), {
      voterId: 'answerer', value: 1,
    });
    await setDoc(doc(db, 'centers/c1'), {
      ownerUid: 'owner', managerUids: ['owner'], status: 'APPROVED', verified: true,
    });
  });
}

function dbFor(uid) {
  return testEnv.authenticatedContext(uid).firestore();
}

describe('EngQA Firestore rules', () => {
  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: { rules: RULES },
    });
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await seed();
  });

  after(async () => {
    await testEnv.cleanup();
  });

  it('blocks a timed-out account from reading private content', async () => {
    const db = dbFor('timed');
    await assertFails(getDoc(doc(db, 'questions/q1')));
  });

  it('allows an expired timeout to read and participate again', async () => {
    const db = dbFor('expired');
    await assertSucceeds(getDoc(doc(db, 'questions/q1')));
  });

  it('blocks a soft-deleted account', async () => {
    const db = dbFor('deleted');
    await assertFails(getDoc(doc(db, 'questions/q1')));
  });

  it('rejects a question owner rewriting counters or authorId', async () => {
    const db = dbFor('owner');
    await assertFails(updateDoc(doc(db, 'questions/q1'), {
      authorId: 'stranger', score: 999,
    }));
  });

  it('allows a question owner to edit content', async () => {
    const db = dbFor('owner');
    await assertSucceeds(updateDoc(doc(db, 'questions/q1'), {
      title: 'A better title', updatedAt: new Date(),
    }));
  });

  it('rejects a spoofed answer notification from a stranger', async () => {
    const db = dbFor('stranger');
    await assertFails(addDoc(collection(db, 'notifications'), {
      actorId: 'stranger', recipientId: 'owner', type: 'answer',
      questionId: 'q1', answerId: 'a1', title: 'New answer', body: 'Spoof',
      read: false, createdAt: new Date(),
    }));
  });

  it('accepts a real answer notification from the answer author', async () => {
    const db = dbFor('answerer');
    await assertSucceeds(addDoc(collection(db, 'notifications'), {
      actorId: 'answerer', recipientId: 'owner', type: 'answer',
      questionId: 'q1', answerId: 'a1', title: 'New answer', body: 'Real',
      read: false, createdAt: new Date(),
    }));
  });

  it('requires a real upvote for a vote notification', async () => {
    const db = dbFor('stranger');
    await assertFails(addDoc(collection(db, 'notifications'), {
      actorId: 'stranger', recipientId: 'answerer', type: 'vote',
      questionId: 'q1', answerId: 'a1', title: 'Vote', body: 'No vote',
      read: false, createdAt: new Date(),
    }));
  });

  it('rejects a second centre request marker for the same account', async () => {
    const db = dbFor('stranger');
    await assertSucceeds(setDoc(doc(db, 'centerRequests/stranger'), {
      status: 'PENDING', centerId: 'c2',
    }));
    await assertFails(setDoc(doc(db, 'centerRequests/stranger'), {
      status: 'PENDING', centerId: 'c3',
    }));
  });

  it('rejects arbitrary profile counter/status changes', async () => {
    const db = dbFor('owner');
    await assertFails(updateDoc(doc(db, 'users/owner'), { questionCount: 999 }));
    await assertSucceeds(updateDoc(doc(db, 'users/owner'), { questionCount: 2 }));
    await assertFails(updateDoc(doc(db, 'users/owner'), {
      status: 'ACTIVE', timeoutUntil: new Date(),
    }));
  });

  it('only lets an admin resolve a report', async () => {
    const userDb = dbFor('owner');
    await assertSucceeds(setDoc(doc(userDb, 'reports/owner_abc123'), {
      reporterId: 'owner', targetPath: 'questions/q1', reason: 'spam', status: 'pending',
      createdAt: new Date(),
    }));
    await assertFails(updateDoc(doc(userDb, 'reports/owner_abc123'), {
      status: 'resolved',
    }));
    const adminDb = dbFor('admin');
    await assertSucceeds(updateDoc(doc(adminDb, 'reports/owner_abc123'), {
      status: 'resolved', resolutionAction: 'warn', resolutionNote: 'warned',
      resolvedBy: 'admin', resolvedAt: new Date(),
    }));
  });

  it('rejects report ids that are not tied to the reporter', async () => {
    const db = dbFor('owner');
    await assertFails(setDoc(doc(db, 'reports/other_abc'), {
      reporterId: 'owner', targetPath: 'questions/q1', reason: 'spam', status: 'pending',
      createdAt: new Date(),
    }));
  });
});
