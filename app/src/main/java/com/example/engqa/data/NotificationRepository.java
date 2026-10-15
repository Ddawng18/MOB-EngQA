package com.example.engqa.data;

import com.example.engqa.model.AppNotification;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Writes and observes {@code notifications/{id}} for the current user. */
public class NotificationRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void create(AppNotification notification, Callback<Void> callback) {
        if (notification.getRecipientId() != null
                && notification.getRecipientId().equals(notification.getActorId())) {
            // Never notify yourself.
            if (callback != null) {
                callback.onSuccess(null);
            }
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("recipientId", notification.getRecipientId());
        data.put("actorId", notification.getActorId());
        data.put("actorName", notification.getActorName());
        data.put("type", notification.getType());
        data.put("questionId", notification.getQuestionId());
        data.put("answerId", notification.getAnswerId());
        data.put("title", notification.getTitle());
        data.put("body", notification.getBody());
        data.put("read", false);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection(FirestoreRefs.NOTIFICATIONS).add(data)
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

    public ListenerRegistration observeMine(String uid, Callback<List<AppNotification>> callback) {
        return db.collection(FirestoreRefs.NOTIFICATIONS)
                .whereEqualTo(FirestoreRefs.FIELD_RECIPIENT_ID, uid)
                .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
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
