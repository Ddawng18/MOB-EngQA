package com.example.engqa.data;

import com.example.engqa.model.Question;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@code questions/{qid}}.
 *
 * <p>Realtime reads return the {@link ListenerRegistration} so the caller (a ViewModel) can detach
 * it; rotation never creates a duplicate listener because the ViewModel survives the recreation.
 */
public class QuestionRepository {

    public static final String SORT_NEWEST = "newest";
    public static final String SORT_POPULAR = "popular";
    public static final String SORT_UNANSWERED = "unanswered";

    /**
     * Search fetches one bounded candidate set, ranks it locally, and pages through the ranked
     * list without additional Firestore reads. 60 is large enough for this app's feed while
     * keeping each search to a single cheap query.
     */
    public static final int SEARCH_CANDIDATE_LIMIT = 60;
    public static final String ALL_CATEGORIES = null;

    /** Firestore batches allow 500 writes; keep room for the question and counter updates. */
    private static final int BATCH_CHILD_LIMIT = 400;

    /** A page of the feed plus the cursor needed by {@link #loadMore}. */
    public static final class Page {
        public final List<Question> questions;
        public final DocumentSnapshot lastDocument;
        public final boolean fromCache;

        public Page(List<Question> questions, DocumentSnapshot lastDocument, boolean fromCache) {
            this.questions = questions;
            this.lastDocument = lastDocument;
            this.fromCache = fromCache;
        }
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ListenerRegistration observeLatest(String categoryId, String sort, int limit,
                                              Callback<Page> callback) {
        Query query = buildQuery(categoryId, sort, limit);
        return query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            callback.onSuccess(toPage(snapshots));
        });
    }

    public ListenerRegistration observeQuestion(String questionId, Callback<Question> callback) {        return db.collection(FirestoreRefs.QUESTIONS).document(questionId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    callback.onSuccess(snapshot != null ? snapshot.toObject(Question.class) : null);
                });
    }

    /** Realtime list of a single author's questions, used by the profile screen. */
    public ListenerRegistration observeByAuthor(String uid, Callback<List<Question>> callback) {
        return db.collection(FirestoreRefs.QUESTIONS)
                .whereEqualTo("authorId", uid)
                .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Question> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Question question = document.toObject(Question.class);
                            if (question != null) {
                                if (question.getId() == null) {
                                    question.setId(document.getId());
                                }
                                list.add(question);
                            }
                        }
                    }
                    callback.onSuccess(list);
                });
    }

    /** One-shot read used to fill the denormalised title for answers created before it existed. */
    public void getQuestionOnce(String questionId, Callback<Question> callback) {
        db.collection(FirestoreRefs.QUESTIONS).document(questionId).get()
                .addOnSuccessListener(document -> callback.onSuccess(
                        document != null && document.exists()
                                ? document.toObject(Question.class) : null))
                .addOnFailureListener(callback::onError);
    }

    public void loadMore(String categoryId, String sort, int limit, DocumentSnapshot after,
                         Callback<Page> callback) {
        Query query = buildQuery(categoryId, sort, limit);
        if (after != null) {
            query = query.startAfter(after);
        }
        query.get()
                .addOnSuccessListener(snapshots -> callback.onSuccess(toPage(snapshots)))
                .addOnFailureListener(callback::onError);
    }

    public void createQuestion(Question question, Callback<String> callback) {
        createQuestion(newQuestionId(), question, callback);
    }

    /**
     * Client-side id so that media can be uploaded to {@code questions/{uid}/{qid}/...} before the
     * question document itself is written.
     */
    public String newQuestionId() {
        return db.collection(FirestoreRefs.QUESTIONS).document().getId();
    }

    /** Writes the question and bumps the author's {@code questionCount} in one atomic batch. */
    public void createQuestion(String questionId, Question question, Callback<String> callback) {
        DocumentReference questionRef =
                db.collection(FirestoreRefs.QUESTIONS).document(questionId);
        WriteBatch batch = db.batch();
        batch.set(questionRef, toData(question));
        if (question.getAuthorId() != null) {
            batch.update(db.collection(FirestoreRefs.USERS).document(question.getAuthorId()),
                    "questionCount", FieldValue.increment(1));
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(questionId))
                .addOnFailureListener(callback::onError);
    }

    private static Map<String, Object> toData(Question question) {
        Map<String, Object> data = new HashMap<>();
        data.put("authorId", question.getAuthorId());
        data.put("authorName", question.getAuthorName());
        data.put("title", question.getTitle());
        data.put("body", question.getBody());
        data.put("categoryId", question.getCategoryId());
        data.put("categoryName", question.getCategoryName());
        data.put("tags", question.getTags());
        data.put("level", question.getLevel());
        data.put("type", question.getType());
        data.put("imageUrls", question.getImageUrls());
        data.put("audioUrl", question.getAudioUrl());
        data.put("thumbBase64", question.getThumbBase64());
        data.put("imageCount", question.getImageCount());
        data.put("hasAudio", question.isHasAudio());
        data.put("passageText", question.getPassageText());
        data.put("searchKeywords", question.getSearchKeywords());
        data.put("answerCount", 0);
        data.put("score", 0);
        data.put("isSolved", false);
        data.put("acceptedAnswerId", null);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        return data;
    }

    /**
     * Deletes the question together with its answers, their votes and its media documents. The
     * question document is deleted last, so a partial failure can be retried safely. Notifications
     * and reports that reference the question are left to the moderation backend, and the answer
     * authors' denormalised counters are not changed (the profile uses the live answer list).
     */
    public void deleteQuestion(String questionId, String authorId, Callback<Void> callback) {
        DocumentReference questionRef =
                db.collection(FirestoreRefs.QUESTIONS).document(questionId);
        CollectionReference answersRef = questionRef.collection(FirestoreRefs.ANSWERS);
        CollectionReference mediaRef = questionRef.collection(FirestoreRefs.MEDIA);

        answersRef.get()
                .addOnSuccessListener(answerSnapshots ->
                        collectVotesAndMedia(questionRef, mediaRef,
                                answerSnapshots, authorId, callback))
                .addOnFailureListener(callback::onError);
    }

    private void collectVotesAndMedia(DocumentReference questionRef,
                                      CollectionReference mediaRef, QuerySnapshot answerSnapshots,
                                      String authorId, Callback<Void> callback) {
        List<Task<QuerySnapshot>> voteTasks = new ArrayList<>();
        for (DocumentSnapshot answer : answerSnapshots.getDocuments()) {
            voteTasks.add(answer.getReference().collection(FirestoreRefs.VOTES).get());
        }
        mediaRef.get()
                .addOnSuccessListener(mediaSnapshots -> Tasks.whenAll(voteTasks)
                        .addOnSuccessListener(unused -> {
                            List<DocumentReference> children = new ArrayList<>();
                            // Votes first: if a later chunk fails, the answers are still present, so
                            // a retry can discover their vote documents again.
                            for (Task<QuerySnapshot> task : voteTasks) {
                                QuerySnapshot votes = task.getResult();
                                if (votes != null) {
                                    for (DocumentSnapshot vote : votes.getDocuments()) {
                                        children.add(vote.getReference());
                                    }
                                }
                            }
                            for (DocumentSnapshot answer : answerSnapshots.getDocuments()) {
                                children.add(answer.getReference());
                            }
                            for (DocumentSnapshot media : mediaSnapshots.getDocuments()) {
                                children.add(media.getReference());
                            }
                            deleteChildrenThenQuestion(questionRef, authorId, children, callback);
                        })
                        .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    private void deleteChildrenThenQuestion(DocumentReference questionRef, String authorId,
                                            List<DocumentReference> children,
                                            Callback<Void> callback) {
        if (children.size() <= BATCH_CHILD_LIMIT) {
            commitDeleteBatch(questionRef, authorId, children, callback);
            return;
        }
        deleteChildChunk(questionRef, authorId, children, 0, callback);
    }

    private void deleteChildChunk(DocumentReference questionRef, String authorId,
                                  List<DocumentReference> children, int start,
                                  Callback<Void> callback) {
        int end = Math.min(start + BATCH_CHILD_LIMIT, children.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            batch.delete(children.get(i));
        }
        final int next = end;
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    if (next < children.size()) {
                        deleteChildChunk(questionRef, authorId, children, next, callback);
                    } else {
                        commitDeleteBatch(questionRef, authorId,
                                Collections.emptyList(), callback);
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    private void commitDeleteBatch(DocumentReference questionRef, String authorId,
                                   List<DocumentReference> children, Callback<Void> callback) {
        WriteBatch batch = db.batch();
        for (DocumentReference child : children) {
            batch.delete(child);
        }
        batch.delete(questionRef);
        if (authorId != null) {
            batch.update(db.collection(FirestoreRefs.USERS).document(authorId),
                    "questionCount", FieldValue.increment(-1));
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /** Marks the question solved and stores the accepted answer id. */
    public void markSolved(String questionId, String answerId, Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("isSolved", true);
        updates.put("acceptedAnswerId", answerId);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(FirestoreRefs.QUESTIONS).document(questionId).update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Retrieves a bounded candidate set for a search. Firestore cannot do full-text search, so
     * {@code array-contains-any} matches any of the normalised query/typo tokens; the ViewModel
     * then ranks the candidates with {@link com.example.engqa.util.SearchRanker}. Category and
     * unanswered filters are part of the Firestore query, so unrelated documents never enter the
     * ranking set. The selected sort is used only to choose which candidates are fetched and as a
     * tie-breaker; relevance is applied locally.
     */
    public ListenerRegistration search(List<String> tokens, String categoryId, String sort,
                                       int limit, Callback<Page> callback) {
        Query query = buildSearchQuery(tokens, categoryId, sort, limit);
        return query.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                callback.onError(error);
                return;
            }
            callback.onSuccess(toPage(snapshots));
        });
    }

    private Query buildSearchQuery(List<String> tokens, String categoryId, String sort, int limit) {
        Query query = db.collection(FirestoreRefs.QUESTIONS)
                .whereArrayContainsAny(FirestoreRefs.FIELD_SEARCH_KEYWORDS, tokens);
        return applyFiltersAndSort(query, categoryId, sort, limit);
    }

    private Query buildQuery(String categoryId, String sort, int limit) {
        return applyFiltersAndSort(db.collection(FirestoreRefs.QUESTIONS), categoryId, sort, limit);
    }

    private Query applyFiltersAndSort(Query query, String categoryId, String sort, int limit) {
        if (categoryId != null && !categoryId.isEmpty()) {
            query = query.whereEqualTo(FirestoreRefs.FIELD_CATEGORY_ID, categoryId);
        }
        switch (sort == null ? SORT_NEWEST : sort) {
            case SORT_POPULAR:
                query = query.orderBy(FirestoreRefs.FIELD_SCORE, Query.Direction.DESCENDING)
                        .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING);
                break;
            case SORT_UNANSWERED:
                query = query.whereEqualTo(FirestoreRefs.FIELD_ANSWER_COUNT, 0)
                        .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING);
                break;
            case SORT_NEWEST:
            default:
                query = query.orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING);
                break;
        }
        return query.limit(limit);
    }

    private static Page toPage(QuerySnapshot snapshots) {
        if (snapshots == null) {
            return new Page(Collections.emptyList(), null, false);
        }
        List<Question> list = new ArrayList<>();
        for (DocumentSnapshot document : snapshots.getDocuments()) {
            Question question = document.toObject(Question.class);
            if (question != null) {
                if (question.getId() == null) {
                    question.setId(document.getId());
                }
                list.add(question);
            }
        }
        DocumentSnapshot last = snapshots.isEmpty()
                ? null
                : snapshots.getDocuments().get(snapshots.size() - 1);
        return new Page(list, last, snapshots.getMetadata().isFromCache());
    }
}
