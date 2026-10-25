package com.example.engqa.data;

import com.example.engqa.model.Center;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Report;
import com.example.engqa.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moderation back-office data access.
 *
 * <p>Only {@code admins/{uid}} accounts pass {@code firestore.rules} for the reads and writes used
 * here. Approving a centre is a single batch that also promotes its owner to {@code CENTER} and
 * points the profile at the new centre id, because the promo rules require both. Every transition
 * writes an {@code auditLogs} entry and (when the recipient is someone else) a notification.
 */
public class AdminRepository {

    private static final String AUDIT_LOGS = "auditLogs";
    private static final String FIELD_OWNER_UID = "ownerUid";
    private static final String FIELD_REVIEWED_BY = "reviewedBy";
    private static final String FIELD_REVIEWED_AT = "reviewedAt";
    private static final String FIELD_REVIEW_NOTE = "reviewNote";
    private static final String FIELD_RESOLVED_BY = "resolvedBy";
    private static final String FIELD_RESOLVED_AT = "resolvedAt";
    private static final String FIELD_RESOLUTION_NOTE = "resolutionNote";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // --------------------------------------------------------------- reads

    /** True when the signed-in account owns an {@code admins/{uid}} document. */
    public void isAdmin(Callback<Boolean> callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onSuccess(false);
            return;
        }
        db.collection(FirestoreRefs.ADMINS).document(user.getUid()).get()
                .addOnSuccessListener(snapshot -> callback.onSuccess(snapshot.exists()))
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration observePendingCenters(Callback<List<Center>> callback) {
        return db.collection(FirestoreRefs.CENTERS)
                .whereEqualTo(FirestoreRefs.FIELD_STATUS, Center.STATUS_PENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Center> centers = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Center center = document.toObject(Center.class);
                            if (center != null) {
                                if (center.getId() == null) {
                                    center.setId(document.getId());
                                }
                                centers.add(center);
                            }
                        }
                    }
                    centers.sort((a, b) -> Long.compare(time(b.getCreatedAt()),
                            time(a.getCreatedAt())));
                    callback.onSuccess(centers);
                });
    }

    public ListenerRegistration observePendingPromos(Callback<List<Promo>> callback) {
        return db.collection(FirestoreRefs.PROMOS)
                .whereEqualTo(FirestoreRefs.FIELD_STATUS, Promo.STATUS_PENDING)
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
                    promos.sort((a, b) -> Long.compare(time(b.getCreatedAt()),
                            time(a.getCreatedAt())));
                    callback.onSuccess(promos);
                });
    }

    public ListenerRegistration observePendingReports(Callback<List<Report>> callback) {
        return db.collection(FirestoreRefs.REPORTS)
                .whereEqualTo(FirestoreRefs.FIELD_STATUS, Report.STATUS_PENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<Report> reports = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Report report = document.toObject(Report.class);
                            if (report != null) {
                                if (report.getId() == null) {
                                    report.setId(document.getId());
                                }
                                reports.add(report);
                            }
                        }
                    }
                    reports.sort((a, b) -> Long.compare(time(b.getCreatedAt()),
                            time(a.getCreatedAt())));
                    callback.onSuccess(reports);
                });
    }

    // ------------------------------------------------------------- actions

    /** Approves a centre and promotes its owner in the same batch. */
    public void approveCenter(Center center, Callback<Void> callback) {
        String adminUid = adminUid();
        if (adminUid == null || center == null || center.getId() == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin duyệt"));
            return;
        }
        WriteBatch batch = db.batch();
        DocumentReference centerRef = db.collection(FirestoreRefs.CENTERS)
                .document(center.getId());
        batch.update(centerRef,
                FirestoreRefs.FIELD_STATUS, Center.STATUS_APPROVED,
                "verified", true,
                FIELD_REVIEWED_BY, adminUid,
                FIELD_REVIEWED_AT, FieldValue.serverTimestamp());

        if (center.getOwnerUid() != null) {
            // Promo creation requires role CENTER *and* centerId (isCenterOwner in the rules).
            batch.update(db.collection(FirestoreRefs.USERS).document(center.getOwnerUid()),
                    "role", User.ROLE_CENTER,
                    FirestoreRefs.FIELD_CENTER_ID, center.getId());
            notify(batch, center.getOwnerUid(), "center_approved",
                    "Trung tâm đã được duyệt",
                    center.getName() + " đã được xác minh. Bạn có thể đăng bài quảng bá.");
        }
        audit(batch, "CENTER_APPROVED", "centers/" + center.getId(), null);
        commit(batch, callback);
    }

    public void rejectCenter(Center center, String note, Callback<Void> callback) {
        String adminUid = adminUid();
        if (adminUid == null || center == null || center.getId() == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin duyệt"));
            return;
        }
        WriteBatch batch = db.batch();
        batch.update(db.collection(FirestoreRefs.CENTERS).document(center.getId()),
                FirestoreRefs.FIELD_STATUS, Center.STATUS_REJECTED,
                FIELD_REVIEW_NOTE, note,
                FIELD_REVIEWED_BY, adminUid,
                FIELD_REVIEWED_AT, FieldValue.serverTimestamp());
        if (center.getOwnerUid() != null) {
            notify(batch, center.getOwnerUid(), "center_rejected",
                    "Đơn trung tâm bị từ chối",
                    note == null || note.isEmpty()
                            ? "Đơn đăng ký trung tâm của bạn chưa được duyệt."
                            : note);
        }
        audit(batch, "CENTER_REJECTED", "centers/" + center.getId(), note);
        commit(batch, callback);
    }

    public void approvePromo(Promo promo, Callback<Void> callback) {
        String adminUid = adminUid();
        if (adminUid == null || promo == null || promo.getPromoId() == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin duyệt"));
            return;
        }
        WriteBatch batch = db.batch();
        batch.update(db.collection(FirestoreRefs.PROMOS).document(promo.getPromoId()),
                FirestoreRefs.FIELD_STATUS, Promo.STATUS_APPROVED,
                FIELD_REVIEWED_BY, adminUid,
                FIELD_REVIEWED_AT, FieldValue.serverTimestamp());
        notify(batch, promo.getAuthorUid(), "promo_approved",
                "Bài quảng bá đã được duyệt",
                promo.getTitle() + " hiện đã xuất hiện trên bảng tin.");
        audit(batch, "PROMO_APPROVED", "promos/" + promo.getPromoId(), null);
        commit(batch, callback);
    }

    public void rejectPromo(Promo promo, String note, Callback<Void> callback) {
        String adminUid = adminUid();
        if (adminUid == null || promo == null || promo.getPromoId() == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin duyệt"));
            return;
        }
        WriteBatch batch = db.batch();
        batch.update(db.collection(FirestoreRefs.PROMOS).document(promo.getPromoId()),
                FirestoreRefs.FIELD_STATUS, Promo.STATUS_REJECTED,
                FIELD_REVIEW_NOTE, note,
                FIELD_REVIEWED_BY, adminUid,
                FIELD_REVIEWED_AT, FieldValue.serverTimestamp());
        notify(batch, promo.getAuthorUid(), "promo_rejected",
                "Bài quảng bá bị từ chối",
                note == null || note.isEmpty()
                        ? "Bài quảng bá của bạn chưa được duyệt."
                        : note);
        audit(batch, "PROMO_REJECTED", "promos/" + promo.getPromoId(), note);
        commit(batch, callback);
    }

    /**
     * Warns the author of the reported content.
     *
     * <p>Three warnings apply a 24h timeout and reset the warning counter; the third timeout marks
     * the profile as DELETED (a soft delete — the Auth user itself needs an Admin SDK job). Three
     * warnings on promos additionally revoke the CENTER role. Everything runs in one transaction so
     * the counters, the target flag, the report, the notification and the audit entry stay in sync.
     */
    public void warnReport(Report report, Callback<Void> callback) {
        String adminUid = adminUid();
        final Target target = parseTarget(report);
        if (adminUid == null || report == null || report.getId() == null || target == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin báo cáo"));
            return;
        }
        final DocumentReference reportRef = db.collection(FirestoreRefs.REPORTS)
                .document(report.getId());

        db.runTransaction(transaction -> {
            DocumentSnapshot targetSnapshot = transaction.get(target.ref);
            if (!targetSnapshot.exists()) {
                throw new IllegalStateException("Nội dung không còn tồn tại");
            }
            String authorUid = targetSnapshot.getString(target.authorField);
            if (authorUid == null) {
                throw new IllegalStateException("Không xác định được tác giả");
            }

            // One penalty per post: a second report on an already-warned post only closes the
            // report, otherwise three reports on the same post would fake three warnings.
            if (Boolean.TRUE.equals(targetSnapshot.getBoolean("warned"))) {
                Map<String, Object> duplicateUpdate = new HashMap<>();
                duplicateUpdate.put(FirestoreRefs.FIELD_STATUS, Report.STATUS_RESOLVED);
                duplicateUpdate.put("resolutionAction", "warn");
                duplicateUpdate.put(FIELD_RESOLUTION_NOTE, "Bài viết đã được cảnh cáo trước đó");
                duplicateUpdate.put(FIELD_RESOLVED_BY, adminUid);
                duplicateUpdate.put(FIELD_RESOLVED_AT, FieldValue.serverTimestamp());
                transaction.update(reportRef, duplicateUpdate);
                transaction.set(db.collection(AUDIT_LOGS).document(),
                        auditData(adminUid, "REPORT_WARN_DUPLICATE",
                                report.getTargetPath(), "Đã cảnh cáo trước đó"));
                return null;
            }

            DocumentReference userRef = db.collection(FirestoreRefs.USERS).document(authorUid);
            DocumentSnapshot userSnapshot = transaction.get(userRef);
            if (!userSnapshot.exists()) {
                throw new IllegalStateException("Không tìm thấy tài khoản tác giả");
            }

            long warnings = longValue(userSnapshot, "warningCount") + 1;
            long promoWarnings = longValue(userSnapshot, "promoWarningCount")
                    + (target.promo ? 1 : 0);
            long timeouts = longValue(userSnapshot, "timeoutCount");
            String role = userSnapshot.getString("role");
            String centerId = userSnapshot.getString("centerId");
            String status = userSnapshot.getString("status");
            Date timeoutUntil = userSnapshot.getDate("timeoutUntil");

            boolean revokeCenter = target.promo
                    && promoWarnings >= User.PROMO_WARNINGS_BEFORE_CENTER_REVOKE;
            String managedCenterId = null;
            if (revokeCenter) {
                managedCenterId = targetSnapshot.getString("centerId");
                if (managedCenterId == null || managedCenterId.isEmpty()) {
                    managedCenterId = centerId;
                }
                role = User.ROLE_USER;
                centerId = null;
            }

            // Read before the first write: Firestore transactions require every read up front.
            DocumentReference managedCenterRef = null;
            if (revokeCenter && managedCenterId != null && !managedCenterId.isEmpty()) {
                DocumentReference candidate = db.collection(FirestoreRefs.CENTERS)
                        .document(managedCenterId);
                if (transaction.get(candidate).exists()) {
                    managedCenterRef = candidate;
                }
            }

            boolean wasDeleted = User.STATUS_DELETED.equals(status);
            boolean timeout = warnings >= User.WARNINGS_PER_TIMEOUT;
            boolean deleted = false;
            if (timeout) {
                warnings = 0;
                timeouts += 1;
                if (wasDeleted || timeouts >= User.TIMEOUTS_BEFORE_DELETE) {
                    status = User.STATUS_DELETED;
                    timeoutUntil = null;
                    deleted = true;
                } else {
                    status = User.STATUS_TIMEOUT;
                    timeoutUntil = new Date(System.currentTimeMillis()
                            + User.TIMEOUT_DURATION_MS);
                }
            }

            Map<String, Object> userUpdate = new HashMap<>();
            userUpdate.put("warningCount", warnings);
            userUpdate.put("promoWarningCount", promoWarnings);
            userUpdate.put("timeoutCount", timeouts);
            if (revokeCenter) {
                userUpdate.put("role", role);
                userUpdate.put("centerId", centerId);
            }
            if (timeout) {
                userUpdate.put("status", status);
                userUpdate.put("timeoutUntil", timeoutUntil);
            }
            transaction.update(userRef, userUpdate);

            if (managedCenterRef != null) {
                // Revoking the role is not enough: also drop them from the centre's manager list
                // so they cannot keep editing the centre through the manager rules.
                transaction.update(managedCenterRef,
                        "managerUids", FieldValue.arrayRemove(authorUid));
            }

            transaction.update(target.ref,
                    "warned", true,
                    "warnedAt", FieldValue.serverTimestamp());

            Map<String, Object> reportUpdate = new HashMap<>();
            reportUpdate.put(FirestoreRefs.FIELD_STATUS, Report.STATUS_RESOLVED);
            reportUpdate.put("resolutionAction", "warn");
            reportUpdate.put(FIELD_RESOLUTION_NOTE, "Cảnh cáo");
            reportUpdate.put(FIELD_RESOLVED_BY, adminUid);
            reportUpdate.put(FIELD_RESOLVED_AT, FieldValue.serverTimestamp());
            transaction.update(reportRef, reportUpdate);

            if (!authorUid.equals(adminUid)) {
                String title;
                String body;
                if (deleted) {
                    title = "Tài khoản đã bị xoá";
                    body = "Tài khoản của bạn đã bị xoá sau 3 lần tạm khoá.";
                } else if (timeout) {
                    title = "Tài khoản bị tạm khoá 1 ngày";
                    body = "Bạn đã nhận đủ 3 cảnh cáo. Tài khoản bị tạm khoá 24 giờ.";
                } else if (revokeCenter) {
                    title = "Quyền trung tâm bị thu hồi";
                    body = "Bài quảng bá bị cảnh cáo 3 lần nên tài khoản không còn quyền trung tâm.";
                } else {
                    title = "Bài viết bị cảnh cáo";
                    body = "Bạn đã nhận " + warnings + "/" + User.WARNINGS_PER_TIMEOUT
                            + " cảnh cáo. Đủ 3 cảnh cáo sẽ bị tạm khoá 1 ngày.";
                }
                transaction.set(db.collection(FirestoreRefs.NOTIFICATIONS).document(),
                        notificationData(authorUid, "moderation_warning", title, body, adminUid));
            }
            transaction.set(db.collection(AUDIT_LOGS).document(),
                    auditData(adminUid, "REPORT_WARNED", report.getTargetPath(), "Cảnh cáo"));
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /** Removes the reported content and closes the report. */
    public void deleteReportedPost(Report report, Callback<Void> callback) {
        String adminUid = adminUid();
        Target target = parseTarget(report);
        if (adminUid == null || report == null || report.getId() == null || target == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin báo cáo"));
            return;
        }
        target.ref.get().addOnSuccessListener(snapshot -> {
            String authorUid = snapshot.exists() ? snapshot.getString(target.authorField) : null;
            if (target.promo) {
                WriteBatch batch = db.batch();
                if (snapshot.exists()) {
                    batch.delete(target.ref);
                }
                addReportResolution(batch, report, "delete", "Xoá nội dung", adminUid);
                addNotification(batch, authorUid, "moderation_deleted",
                        "Bài quảng bá đã bị xoá",
                        "Bài quảng bá của bạn đã bị xoá do vi phạm.", adminUid);
                addAudit(batch, adminUid, "REPORT_DELETED", report.getTargetPath(),
                        "Xoá bài quảng bá");
                commit(batch, callback);
                return;
            }

            String questionId = target.ref.getId();
            new QuestionRepository().deleteQuestion(questionId, authorUid,
                    new Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            WriteBatch batch = db.batch();
                            addReportResolution(batch, report, "delete", "Xoá câu hỏi",
                                    adminUid);
                            addNotification(batch, authorUid, "moderation_deleted",
                                    "Câu hỏi đã bị xoá",
                                    "Câu hỏi của bạn đã bị xoá do vi phạm.", adminUid);
                            addAudit(batch, adminUid, "REPORT_DELETED",
                                    report.getTargetPath(), "Xoá câu hỏi");
                            commit(batch, callback);
                        }

                        @Override
                        public void onError(Exception e) {
                            callback.onError(e);
                        }
                    });
        }).addOnFailureListener(callback::onError);
    }

    public void dismissReport(Report report, String note, Callback<Void> callback) {
        String adminUid = adminUid();
        if (adminUid == null || report == null || report.getId() == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin báo cáo"));
            return;
        }
        WriteBatch batch = db.batch();
        batch.update(db.collection(FirestoreRefs.REPORTS).document(report.getId()),
                FirestoreRefs.FIELD_STATUS, Report.STATUS_DISMISSED,
                FIELD_RESOLUTION_NOTE, note,
                FIELD_RESOLVED_BY, adminUid,
                FIELD_RESOLVED_AT, FieldValue.serverTimestamp());
        audit(batch, "REPORT_DISMISSED", report.getTargetPath(), note);
        commit(batch, callback);
    }

    // ------------------------------------------------------------- helpers

    /** A report target: a question ({@code authorId}) or a promo ({@code authorUid}). */
    private static final class Target {
        final DocumentReference ref;
        final boolean promo;
        final String authorField;

        Target(DocumentReference ref, boolean promo, String authorField) {
            this.ref = ref;
            this.promo = promo;
            this.authorField = authorField;
        }
    }

    private Target parseTarget(Report report) {
        if (report == null || report.getTargetPath() == null) {
            return null;
        }
        String[] parts = report.getTargetPath().split("/", 2);
        if (parts.length != 2 || parts[1].isEmpty()) {
            return null;
        }
        if ("promos".equals(parts[0])) {
            return new Target(db.collection(FirestoreRefs.PROMOS).document(parts[1]),
                    true, "authorUid");
        }
        if ("questions".equals(parts[0])) {
            return new Target(db.collection(FirestoreRefs.QUESTIONS).document(parts[1]),
                    false, "authorId");
        }
        return null;
    }

    private static long longValue(DocumentSnapshot snapshot, String field) {
        Long value = snapshot.getLong(field);
        return value != null ? value : 0L;
    }

    private void addNotification(WriteBatch batch, String recipientId, String type,
                                 String title, String body, String actorId) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }
        batch.set(db.collection(FirestoreRefs.NOTIFICATIONS).document(),
                notificationData(recipientId, type, title, body, actorId));
    }

    private Map<String, Object> notificationData(String recipientId, String type,
                                                 String title, String body, String actorId) {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientId", recipientId);
        data.put("actorId", actorId);
        data.put("actorName", "Ban quản trị");
        data.put("type", type);
        data.put("questionId", null);
        data.put("answerId", null);
        data.put("title", title);
        data.put("body", body);
        data.put("read", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    private void addAudit(WriteBatch batch, String actorId, String action,
                          String targetPath, String note) {
        batch.set(db.collection(AUDIT_LOGS).document(),
                auditData(actorId, action, targetPath, note));
    }

    private Map<String, Object> auditData(String actorId, String action,
                                          String targetPath, String note) {
        Map<String, Object> data = new HashMap<>();
        data.put("actorUid", actorId);
        data.put("action", action);
        data.put("targetPath", targetPath);
        data.put("note", note);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    private void addReportResolution(WriteBatch batch, Report report, String action,
                                     String note, String adminUid) {
        Map<String, Object> data = new HashMap<>();
        data.put(FirestoreRefs.FIELD_STATUS, Report.STATUS_RESOLVED);
        data.put("resolutionAction", action);
        data.put(FIELD_RESOLUTION_NOTE, note);
        data.put(FIELD_RESOLVED_BY, adminUid);
        data.put(FIELD_RESOLVED_AT, FieldValue.serverTimestamp());
        batch.update(db.collection(FirestoreRefs.REPORTS).document(report.getId()), data);
    }

    private void notify(WriteBatch batch, String recipientId, String type,
                        String title, String body) {
        String adminUid = adminUid();
        if (recipientId == null || recipientId.equals(adminUid)) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("recipientId", recipientId);
        data.put("actorId", adminUid);
        data.put("actorName", "Ban quản trị");
        data.put("type", type);
        data.put("questionId", null);
        data.put("answerId", null);
        data.put("title", title);
        data.put("body", body);
        data.put("read", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        batch.set(db.collection(FirestoreRefs.NOTIFICATIONS).document(), data);
    }

    private void audit(WriteBatch batch, String action, String targetPath, String note) {
        Map<String, Object> data = new HashMap<>();
        data.put("actorUid", adminUid());
        data.put("action", action);
        data.put("targetPath", targetPath);
        data.put("note", note);
        data.put("createdAt", FieldValue.serverTimestamp());
        batch.set(db.collection(AUDIT_LOGS).document(), data);
    }

    private void commit(WriteBatch batch, Callback<Void> callback) {
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private String adminUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private static long time(Date date) {
        return date != null ? date.getTime() : 0L;
    }
}
