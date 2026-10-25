package com.example.engqa.notification;

import android.content.Context;

import com.example.engqa.R;
import com.example.engqa.data.AdminNotifier;
import com.example.engqa.data.FirestoreRefs;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;

/**
 * One-shot safety net for the admin's bell.
 *
 * <p>New requests are announced by the requester (see {@link AdminNotifier}), but items that were
 * already pending before that code shipped — or requests whose announcement failed — would leave
 * the bell empty. On app start this checks the three queues and creates the missing notification
 * documents for the signed-in admin only. Deterministic ids mean it never duplicates an entry and
 * never clears the read flag of an existing one.
 */
public final class ModerationBackfill {

    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ModerationBackfill() {
    }

    public static void run(Context context, String uid) {
        if (uid == null) {
            return;
        }
        Context app = context.getApplicationContext();
        db.collection(FirestoreRefs.ADMINS).document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        return;
                    }
                    ensure(app, uid, "center",
                            db.collection(FirestoreRefs.CENTERS)
                                    .whereEqualTo(FirestoreRefs.FIELD_STATUS, "PENDING"),
                            R.string.moderation_center_title, R.string.moderation_center_body);
                    ensure(app, uid, "promo",
                            db.collection(FirestoreRefs.PROMOS)
                                    .whereEqualTo(FirestoreRefs.FIELD_STATUS, "PENDING"),
                            R.string.moderation_promo_title, R.string.moderation_promo_body);
                    ensure(app, uid, "report",
                            db.collection(FirestoreRefs.REPORTS)
                                    .whereEqualTo(FirestoreRefs.FIELD_STATUS, "pending"),
                            R.string.moderation_report_title, R.string.moderation_report_body);
                });
    }

    private static void ensure(Context context, String adminUid, String kind, Query query,
                               int titleRes, int bodyRes) {
        String title = context.getString(titleRes);
        String body = context.getString(bodyRes);
        query.get().addOnSuccessListener(snapshots -> {
            for (DocumentSnapshot document : snapshots.getDocuments()) {
                DocumentReference notification = db.collection(FirestoreRefs.NOTIFICATIONS)
                        .document(AdminNotifier.notificationId(kind, document.getId(), adminUid));
                notification.get(Source.SERVER).addOnSuccessListener(existing -> {
                    if (existing.exists()) {
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("recipientId", adminUid);
                    data.put("actorId", adminUid);
                    data.put("actorName", "Hệ thống kiểm duyệt");
                    data.put("type", "moderation_" + kind);
                    data.put("questionId", null);
                    data.put("answerId", null);
                    data.put("title", title);
                    data.put("body", body);
                    data.put("read", false);
                    data.put("createdAt", FieldValue.serverTimestamp());
                    notification.set(data);
                });
            }
        });
    }
}
