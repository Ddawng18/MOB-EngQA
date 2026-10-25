package com.example.engqa.data;

import com.example.engqa.model.Lead;
import com.example.engqa.model.Promo;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes {@code promos/{promoId}} and its {@code leads} sub-collection.
 *
 * <p>Only {@link Promo#STATUS_APPROVED} posts are exposed to the feed. Counters are incremented by
 * exactly one and the rules enforce the same bound server-side.
 */
public class PromoRepository {

    /** Number of newest approved promos loaded for the feed. */
    public static final int FEED_LIMIT = 30;

    private static final List<String> COUNTER_FIELDS =
            Arrays.asList("viewCount", "clickCount", "leadCount");

    /** Thrown when the learner already sent a consultation request for this promo. */
    public static final class LeadExistsException extends RuntimeException {
        public LeadExistsException() {
            super("Bạn đã đăng ký bài này rồi");
        }
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ------------------------------------------------------------------ reads

    public ListenerRegistration observeApproved(Callback<List<Promo>> callback) {
        // No orderBy: documents without createdAt would silently disappear from the feed. Sort
        // client-side instead so every approved promo is visible.
        return db.collection(FirestoreRefs.PROMOS)
                .whereEqualTo(FirestoreRefs.FIELD_STATUS, Promo.STATUS_APPROVED)
                .limit(FEED_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Promo> promos = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Promo promo = document.toObject(Promo.class);
                            if (promo != null) {
                                if (promo.getPromoId() == null) {
                                    promo.setPromoId(document.getId());
                                }
                                promos.add(promo);
                            }
                        }
                    }
                    callback.onSuccess(promos);
                });
    }

    /** Promos authored by a centre, in every status (pending/approved/rejected/hidden). */
    public ListenerRegistration observeMine(String uid, Callback<List<Promo>> callback) {
        return db.collection(FirestoreRefs.PROMOS)
                .whereEqualTo(FirestoreRefs.FIELD_AUTHOR_UID, uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Promo> promos = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Promo promo = document.toObject(Promo.class);
                            if (promo != null) {
                                if (promo.getPromoId() == null) {
                                    promo.setPromoId(document.getId());
                                }
                                promos.add(promo);
                            }
                        }
                    }
                    promos.sort((a, b) -> {
                        long left = a.getCreatedAt() != null ? a.getCreatedAt().getTime() : 0L;
                        long right = b.getCreatedAt() != null ? b.getCreatedAt().getTime() : 0L;
                        return Long.compare(right, left);
                    });
                    callback.onSuccess(promos);
                });
    }

    public ListenerRegistration observeById(String promoId, Callback<Promo> callback) {
        return db.collection(FirestoreRefs.PROMOS).document(promoId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    callback.onSuccess(snapshot != null ? snapshot.toObject(Promo.class) : null);
                });
    }

    public void loadById(String promoId, Callback<Promo> callback) {
        db.collection(FirestoreRefs.PROMOS).document(promoId).get()
                .addOnSuccessListener(snapshot -> callback.onSuccess(
                        snapshot != null && snapshot.exists()
                                ? snapshot.toObject(Promo.class) : null))
                .addOnFailureListener(callback::onError);
    }

    // ------------------------------------------------------------------ writes

    /** Creates a promo in {@code PENDING} state; an administrator must approve it. */
    public void createPromo(Promo promo, Callback<String> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("centerId", promo.getCenterId());
        data.put("centerName", promo.getCenterName());
        data.put("centerVerified", promo.isCenterVerified());
        data.put("authorUid", promo.getAuthorUid());
        data.put("title", promo.getTitle());
        data.put("body", promo.getBody());
        data.put("type", promo.getType());
        data.put("level", promo.getLevel());
        data.put("fee", promo.getFee());
        data.put("schedule", promo.getSchedule());
        data.put("contactPhone", promo.getContactPhone());
        data.put("linkUrl", promo.getLinkUrl());
        data.put("imageUrl", promo.getImageUrl());
        data.put("status", Promo.STATUS_PENDING);
        data.put("viewCount", 0);
        data.put("clickCount", 0);
        data.put("leadCount", 0);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("endAt", promo.getEndAt() != null
                ? new Timestamp(promo.getEndAt()) : null);

        db.collection(FirestoreRefs.PROMOS).add(data)
                .addOnSuccessListener(ref -> callback.onSuccess(ref.getId()))
                .addOnFailureListener(callback::onError);
    }

    /** Increments one counter by exactly one. */
    public void bump(String promoId, String field, Callback<Void> callback) {
        if (!COUNTER_FIELDS.contains(field)) {
            callback.onError(new IllegalArgumentException("Không thể tăng trường " + field));
            return;
        }
        db.collection(FirestoreRefs.PROMOS).document(promoId)
                .update(field, FieldValue.increment(1))
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Stores the learner's consultation request at {@code leads/{uid}} and bumps {@code leadCount}.
     * A transaction guarantees "one request per learner per promo" and keeps the counter exact.
     */
    public void submitLead(String promoId, Lead lead, Callback<Void> callback) {
        DocumentReference promoRef = db.collection(FirestoreRefs.PROMOS).document(promoId);
        DocumentReference leadRef = promoRef.collection(FirestoreRefs.LEADS)
                .document(lead.getUid());

        db.runTransaction(transaction -> {
            DocumentSnapshot existingLead = transaction.get(leadRef);
            if (existingLead.exists()) {
                throw new LeadExistsException();
            }
            DocumentSnapshot promoSnapshot = transaction.get(promoRef);
            Long current = promoSnapshot.getLong("leadCount");
            long next = (current != null ? current : 0L) + 1;

            Map<String, Object> leadData = new HashMap<>();
            leadData.put("userUid", lead.getUserUid() != null
                    ? lead.getUserUid() : lead.getUid());
            leadData.put("name", lead.getName());
            leadData.put("phone", lead.getPhone());
            leadData.put("note", lead.getNote());
            leadData.put("status", Lead.STATUS_NEW);
            leadData.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(leadRef, leadData);
            transaction.update(promoRef, "leadCount", next);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess(null))
          .addOnFailureListener(callback::onError);
    }
}
