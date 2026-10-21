package com.example.engqa.data;

import com.example.engqa.model.Answer;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes {@code questions/{qid}/answers/{aid}}. */
public class AnswerRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ListenerRegistration observeAnswers(String questionId, Callback<List<Answer>> callback) {
        return db.collection(FirestoreRefs.QUESTIONS).document(questionId)
                .collection(FirestoreRefs.ANSWERS)
                .orderBy("isAccepted", Query.Direction.DESCENDING)
                .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Answer> answers = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Answer answer = document.toObject(Answer.class);
                            if (answer != null) {
                                if (answer.getId() == null) {
                                    answer.setId(document.getId());
                                }
                                if (answer.getQuestionId() == null) {
                                    answer.setQuestionId(questionId);
                                }
                                answers.add(answer);
                            }
                        }
                    }
                    callback.onSuccess(Collections.unmodifiableList(answers));
                });
    }

    /** Realtime list of a single author's answers across all questions (collection-group query). */
    public ListenerRegistration observeByAuthor(String uid, Callback<List<Answer>> callback) {
        return db.collectionGroup(FirestoreRefs.ANSWERS)
                .whereEqualTo("authorId", uid)
                .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Answer> answers = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Answer answer = document.toObject(Answer.class);
                            if (answer != null) {
                                if (answer.getId() == null) {
                                    answer.setId(document.getId());
                                }
                                if (answer.getQuestionId() == null && document.getReference().getParent().getParent() != null) {
                                    answer.setQuestionId(document.getReference().getParent().getParent().getId());
                                }
                                answers.add(answer);
                            }
                        }
                    }
                    callback.onSuccess(answers);
                });
    }

    /** Adds an answer and bumps the denormalised {@code answerCount} on the question atomically. */
    public void addAnswer(Answer answer, Callback<String> callback) {
        String questionId = answer.getQuestionId();
        DocumentReference questionRef = db.collection(FirestoreRefs.QUESTIONS).document(questionId);
        DocumentReference answerRef = questionRef.collection(FirestoreRefs.ANSWERS).document();

        Map<String, Object> data = new HashMap<>();
        data.put("questionId", questionId);
        data.put("authorId", answer.getAuthorId());
        data.put("authorName", answer.getAuthorName());
        data.put("body", answer.getBody());
        data.put("questionTitle", answer.getQuestionTitle());
        data.put("upCount", 0);
        data.put("downCount", 0);
        data.put("isAccepted", false);
        data.put("createdAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.set(answerRef, data);
        batch.update(questionRef, FirestoreRefs.FIELD_ANSWER_COUNT, FieldValue.increment(1));
        if (answer.getAuthorId() != null) {
            batch.update(db.collection(FirestoreRefs.USERS).document(answer.getAuthorId()),
                    "answerCount", FieldValue.increment(1));
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(answerRef.getId()))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Accepts one answer: marks the answer accepted, marks the question solved and rewards the
     * answer author's {@code acceptedCount} — all in a single batch.
     */
    public void accept(String questionId, String answerId, String answerAuthorId,
                       Callback<Void> callback) {
        DocumentReference questionRef = db.collection(FirestoreRefs.QUESTIONS).document(questionId);
        DocumentReference acceptedRef = questionRef.collection(FirestoreRefs.ANSWERS).document(answerId);

        WriteBatch batch = db.batch();
        batch.update(acceptedRef, "isAccepted", true);
        Map<String, Object> questionUpdates = new HashMap<>();
        questionUpdates.put("isSolved", true);
        questionUpdates.put("acceptedAnswerId", answerId);
        questionUpdates.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(questionRef, questionUpdates);
        if (answerAuthorId != null) {
            // Allowed by the rules only when acceptedCount grows by exactly one.
            batch.update(db.collection(FirestoreRefs.USERS).document(answerAuthorId),
                    "acceptedCount", FieldValue.increment(1));
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void delete(String questionId, String answerId, String answerAuthorId,
                       Callback<Void> callback) {
        DocumentReference questionRef = db.collection(FirestoreRefs.QUESTIONS).document(questionId);
        DocumentReference answerRef = questionRef.collection(FirestoreRefs.ANSWERS).document(answerId);
        WriteBatch batch = db.batch();
        batch.delete(answerRef);
        batch.update(questionRef, FirestoreRefs.FIELD_ANSWER_COUNT, FieldValue.increment(-1));
        if (answerAuthorId != null) {
            batch.update(db.collection(FirestoreRefs.USERS).document(answerAuthorId),
                    "answerCount", FieldValue.increment(-1));
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
