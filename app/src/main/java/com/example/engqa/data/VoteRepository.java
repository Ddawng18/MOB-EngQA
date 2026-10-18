package com.example.engqa.data;

import com.example.engqa.model.Vote;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Upvote/downvote handling.
 *
 * <p>Each vote is one document {@code .../votes/{uid}}, so a user can only ever hold one vote per
 * answer. The whole change (vote document plus the answer counters plus the question score) runs in
 * a single Firestore transaction, which makes concurrent votes from several devices and rapid
 * double taps safe. The same deltas are re-checked by the security rules with {@code getAfter()}.
 */
public class VoteRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Result of a vote: the caller's new vote value and the answer's new counters. */
    public static final class VoteResult {
        public final int value;
        public final long upCount;
        public final long downCount;

        public VoteResult(int value, long upCount, long downCount) {
            this.value = value;
            this.upCount = upCount;
            this.downCount = downCount;
        }
    }

    public ListenerRegistration observeMyVote(String questionId, String answerId, String uid,
                                              Callback<Integer> callback) {
        if (uid == null) {
            return null;
        }
        return voteRef(questionId, answerId, uid).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            int value = 0;
            if (snapshot != null && snapshot.exists()) {
                Long stored = snapshot.getLong("value");
                value = stored != null ? stored.intValue() : 0;
            }
            callback.onSuccess(value);
        });
    }

    /**
     * Applies the user's desired vote. {@code newValue} is {@code +1}, {@code -1} or {@code 0}
     * (no vote). The transaction reads the current vote and the answer, then writes absolute
     * counter values so the result never drifts.
     */
    public void vote(String questionId, String answerId, String uid, int newValue,
                     Callback<VoteResult> callback) {
        final int requested = newValue == Vote.UP ? Vote.UP : newValue == Vote.DOWN ? Vote.DOWN : 0;

        DocumentReference questionRef = db.collection(FirestoreRefs.QUESTIONS).document(questionId);
        DocumentReference answerRef = questionRef.collection(FirestoreRefs.ANSWERS).document(answerId);
        DocumentReference voteRef = answerRef.collection(FirestoreRefs.VOTES).document(uid);

        db.runTransaction((Transaction.Function<VoteResult>) transaction -> {
            DocumentSnapshot voteSnapshot = transaction.get(voteRef);
            DocumentSnapshot answerSnapshot = transaction.get(answerRef);
            DocumentSnapshot questionSnapshot = transaction.get(questionRef);

            int oldValue = 0;
            if (voteSnapshot.exists()) {
                Long stored = voteSnapshot.getLong("value");
                oldValue = stored != null ? stored.intValue() : 0;
            }
            long up = answerSnapshot.getLong("upCount") != null
                    ? answerSnapshot.getLong("upCount") : 0L;
            long down = answerSnapshot.getLong("downCount") != null
                    ? answerSnapshot.getLong("downCount") : 0L;
            long score = questionSnapshot.getLong("score") != null
                    ? questionSnapshot.getLong("score") : 0L;

            if (oldValue == Vote.UP) {
                up--;
            } else if (oldValue == Vote.DOWN) {
                down--;
            }
            if (requested == Vote.UP) {
                up++;
            } else if (requested == Vote.DOWN) {
                down++;
            }
            long newScore = score + (requested - oldValue);

            transaction.update(answerRef, "upCount", up, "downCount", down);
            transaction.update(questionRef, "score", newScore);
            if (requested == 0) {
                transaction.delete(voteRef);
            } else {
                Map<String, Object> voteData = new HashMap<>();
                voteData.put("value", requested);
                transaction.set(voteRef, voteData);
            }
            return new VoteResult(requested, up, down);
        }).addOnSuccessListener(callback::onSuccess)
          .addOnFailureListener(callback::onError);
    }

    private DocumentReference voteRef(String questionId, String answerId, String uid) {
        return db.collection(FirestoreRefs.QUESTIONS).document(questionId)
                .collection(FirestoreRefs.ANSWERS).document(answerId)
                .collection(FirestoreRefs.VOTES).document(uid);
    }
}
