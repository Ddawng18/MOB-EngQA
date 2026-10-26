package com.example.engqa.data;

import com.example.engqa.model.AppNotification;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Writes and observes {@code notifications/{id}} for the current user. */
public class NotificationRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void create(AppNotification notification, Callback<Void> callback) {
        if (!canDeliver(notification)) {
            if (callback != null) {
                callback.onSuccess(null);
            }
            return;
        }

        db.collection(FirestoreRefs.NOTIFICATIONS).add(data(notification))
                .addOnSuccessListener(ref -> {
                    if (callback != null) {
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onError(e);
                    }
                });
    }

    /** Adds a notification to an existing batch so it commits with the moderation change. */
    public static void addToBatch(WriteBatch batch, AppNotification notification) {
        if (batch == null || !canDeliver(notification)) {
            return;
        }
        batch.set(reference(notification), data(notification));
    }

    /** Adds a notification to an existing transaction so it commits with the moderation change. */
    public static void set(Transaction transaction, AppNotification notification) {
        if (transaction == null || !canDeliver(notification)) {
            return;
        }
        transaction.set(reference(notification), data(notification));
    }

    /** Firestore payload shared by the standalone, batch and transaction writers. */
    public static Map<String, Object> data(AppNotification notification) {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientId", notification.getRecipientId());
        data.put("actorId", notification.getActorId());
        data.put("actorName", notification.getActorName());
        data.put("type", notification.getType());
        putIfPresent(data, "questionId", notification.getQuestionId());
        putIfPresent(data, "answerId", notification.getAnswerId());
        putIfPresent(data, "reportId", notification.getReportId());
        putIfPresent(data, "centerId", notification.getCenterId());
        putIfPresent(data, "promoId", notification.getPromoId());
        data.put("title", notification.getTitle());
        data.put("body", notification.getBody());
        data.put("read", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    private static DocumentReference reference(AppNotification notification) {
        CollectionReference notifications = FirebaseFirestore.getInstance()
                .collection(FirestoreRefs.NOTIFICATIONS);
        return notification.getId() != null
                ? notifications.document(notification.getId())
                : notifications.document();
    }

    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isEmpty()) {
            data.put(key, value);
        }
    }

    private static boolean canDeliver(AppNotification notification) {
        return notification != null
                && notification.getRecipientId() != null
                && !notification.getRecipientId().equals(notification.getActorId());
    }

    public ListenerRegistration observeMine(String uid, Callback<List<AppNotification>> callback) {
        return db.collection(FirestoreRefs.NOTIFICATIONS)
                .whereEqualTo(FirestoreRefs.FIELD_RECIPIENT_ID, uid)
                .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        OfflineCache.get().loadNotifications(uid,
                                new Callback<List<AppNotification>>() {
                                    @Override
                                    public void onSuccess(List<AppNotification> result) {
                                        callback.onSuccess(result);
                                    }

                                    @Override
                                    public void onError(Exception cacheError) {
                                        callback.onError(error);
                                    }
                                });
                        return;
                    }
                    List<AppNotification> notifications = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            AppNotification notification = document.toObject(AppNotification.class);
                            if (notification != null) {
                                if (notification.getId() == null) {
                                    notification.setId(document.getId());
                                }
                                notifications.add(notification);
                            }
                        }
                    }
                    OfflineCache.get().cacheNotifications(uid, notifications);
                    callback.onSuccess(notifications);
                });
    }

    public void markRead(String notificationId, Callback<Void> callback) {
        db.collection(FirestoreRefs.NOTIFICATIONS).document(notificationId)
                .update("read", true)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void markAllRead(List<AppNotification> notifications, Callback<Void> callback) {
        WriteBatch batch = db.batch();
        for (AppNotification notification : notifications) {
            if (!notification.isRead() && notification.getId() != null) {
                batch.update(db.collection(FirestoreRefs.NOTIFICATIONS)
                        .document(notification.getId()), "read", true);
            }
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}
