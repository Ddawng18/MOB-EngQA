package com.example.engqa.notification;


import com.example.engqa.EngQAApp;
import com.example.engqa.data.FirestoreRefs;
import com.example.engqa.model.AppNotification;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

/**
 * Watches {@code notifications} addressed to the signed-in user and shows a system notification for
 * each newly added document.
 *
 * <p>The first snapshot is skipped so the whole backlog is not re-announced, and cached snapshots
 * are ignored while deciding to notify. Because this relies on a Firestore listener, new items are
 * only delivered while the app process is alive — see the project report for that limitation.
 */
public final class NotificationWatcher {

    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static ListenerRegistration registration;
    private static boolean firstSnapshot = true;

    private NotificationWatcher() {
    }

    public static void start(String uid) {
        stop();
        if (uid == null) {
            return;
        }
        firstSnapshot = true;
        registration = db.collection(FirestoreRefs.NOTIFICATIONS)
                .whereEqualTo(FirestoreRefs.FIELD_RECIPIENT_ID, uid)
                .orderBy(FirestoreRefs.FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        return;
                    }
                    // Ignore cached callbacks entirely. The first *server* snapshot is the
                    // baseline: it contains the backlog, which must not be re-announced.
                    if (snapshots.getMetadata().isFromCache()) {
                        return;
                    }
                    if (firstSnapshot) {
                        firstSnapshot = false;
                        return;
                    }
                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        if (change.getType() != DocumentChange.Type.ADDED) {
                            continue;
                        }
                        AppNotification notification =
                                change.getDocument().toObject(AppNotification.class);
                        if (notification == null) {
                            continue;
                        }
                        NotificationHelper.show(
                                EngQAApp.getContext(),
                                (int) (System.currentTimeMillis() & 0x7FFFFFFF),
                                notification.getTitle(),
                                notification.getBody(),
                                notification.getQuestionId());
                    }
                });
    }

    public static void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
