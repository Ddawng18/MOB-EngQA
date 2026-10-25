package com.example.engqa.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Drops a moderation request into every admin's in-app notification list.
 *
 * <p>The admin's own device cannot notify itself (the rules only allow notifying someone else), and
 * a watcher on the admin device misses everything while the app is closed. The requester already
 * knows the target id, so it writes one deterministic document per admin here
 * ({@code mod_<kind>_<targetId>_<adminUid>}). That document powers the bell, the unread badge and
 * (through {@code NotificationWatcher}) the system notification. The admin console deletes the
 * acting admin's copy when the request is processed.
 *
 * <p>This needs read access to the {@code admins} collection for signed-in users (see
 * firestore.rules); it exposes only admin uids, never credentials.
 */
public final class AdminNotifier {

    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private AdminNotifier() {
    }

    /**
     * @param kind     "center", "promo" or "report"; also becomes the notification type suffix
     * @param targetId the created centre/promo/report document id
     */
    public static void notifyAdmins(String kind, String targetId, String title, String body) {
        FirebaseUser actor = FirebaseAuth.getInstance().getCurrentUser();
        if (actor == null || kind == null || targetId == null || targetId.isEmpty()) {
            return;
        }
        db.collection(FirestoreRefs.ADMINS).get().addOnSuccessListener(snapshots -> {
            WriteBatch batch = db.batch();
            boolean any = false;
            for (DocumentSnapshot document : snapshots.getDocuments()) {
                String adminUid = document.getId();
                if (adminUid.equals(actor.getUid())) {
                    continue;
                }
                Map<String, Object> data = new HashMap<>();
                data.put("recipientId", adminUid);
                data.put("actorId", actor.getUid());
                data.put("actorName", "Người dùng");
                data.put("type", "moderation_" + kind);
                data.put("questionId", null);
                data.put("answerId", null);
                data.put("title", title);
                data.put("body", body);
                data.put("read", false);
                data.put("createdAt", FieldValue.serverTimestamp());
                batch.set(notificationRef(kind, targetId, adminUid), data);
                any = true;
            }
            if (any) {
                batch.commit();
            }
        });
    }

    /** Deterministic id shared with the moderation console's cleanup. */
    public static String notificationId(String kind, String targetId, String adminUid) {
        return "mod_" + kind + "_" + targetId + "_" + adminUid;
    }

    private static com.google.firebase.firestore.DocumentReference notificationRef(
            String kind, String targetId, String adminUid) {
        return db.collection(FirestoreRefs.NOTIFICATIONS).document(notificationId(
                kind, targetId, adminUid));
    }
}
