package com.example.engqa.data;

import com.example.engqa.model.AppNotification;
import com.example.engqa.model.Center;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Report;
import com.example.engqa.model.User;
import com.example.engqa.notification.ModerationNotifications;
import com.example.engqa.notification.ModerationNotifications.ContentKind;
import com.example.engqa.util.ModerationPolicy;
import com.example.engqa.util.ReportTargets;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
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
        }
        NotificationRepository.addToBatch(batch,
                ModerationNotifications.centerDecision(adminUid, center, true, null));
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
        NotificationRepository.addToBatch(batch,
                ModerationNotifications.centerDecision(adminUid, center, false, note));
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
        NotificationRepository.addToBatch(batch,
                ModerationNotifications.promoDecision(adminUid, promo, true, null));
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
        NotificationRepository.addToBatch(batch,
                ModerationNotifications.promoDecision(adminUid, promo, false, note));
        audit(batch, "PROMO_REJECTED", "promos/" + promo.getPromoId(), note);
        commit(batch, callback);
    }

    /**
     * Warns the author of the reported content.
     *
     * <p>Three warnings apply a 24h timeout and reset the warning counter; the third timeout marks
     * the profile as DELETED (a soft delete — the Auth user itself needs an Admin SDK job). Three
     * warnings on promos additionally revoke the CENTER role and delete the centre it manages
     * (its courses, posts and promos are cleaned up right after). The counters, target flag, report,
     * warning notification and audit entry commit in one transaction. A deletion notification is
     * instead written in the same batch as the actual content deletion, so it is never sent when
     * the deletion fails.
     */
    public void warnReport(Report report, Callback<Void> callback) {
        applyWarning(report, false, null, callback);
    }

    /** Deleting content also counts as one warning, so it reuses the same penalty transaction. */
    public void deleteReportedContent(Report report, Callback<Void> callback) {
        Target target = parseTarget(report);
        if (target == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin báo cáo"));
            return;
        }
        // Read the trusted facts before deleting, then delete first. The report/penalty transaction
        // only runs after the content is actually gone, so a failed cascade never closes a report
        // and never applies a penalty for content that still exists.
        target.ref.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                // The content may have been deleted by a previous attempt whose penalty step
                // failed. Close the report without applying a second penalty.
                resolveDeletedDuplicate(report, callback);
                return;
            }
            String authorUid = snapshot.getString(target.authorField);
            if (authorUid == null) {
                callback.onError(new IllegalStateException("Không xác định được tác giả"));
                return;
            }
            DeletedTarget deleted = new DeletedTarget(target, authorUid,
                    target.isPromo() ? snapshot.getString("title") : snapshot.getString("body"),
                    target.isAnswer() ? target.questionId : null,
                    null,
                    null,
                    target.isPromo() ? snapshot.getString(FirestoreRefs.FIELD_CENTER_ID) : null,
                    Boolean.TRUE.equals(snapshot.getBoolean("warned")));
            deleteTarget(target, authorUid, null, new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    if (deleted.alreadyWarned) {
                        resolveDeletedDuplicate(report, callback);
                    } else {
                        applyPenaltyAfterDelete(report, deleted, callback);
                    }
                }

                @Override
                public void onError(Exception e) {
                    callback.onError(e);
                }
            });
        }).addOnFailureListener(callback::onError);
    }

    /**
     * Applies the warning/timeout/centre-revocation penalty after the content deletion succeeded.
     * The target document no longer exists, so the trusted facts captured before deletion are used
     * and no write to the target is attempted.
     */
    private void applyPenaltyAfterDelete(Report report, DeletedTarget deleted,
                                         Callback<Void> callback) {
        String adminUid = adminUid();
        if (adminUid == null || report == null || report.getId() == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin báo cáo"));
            return;
        }
        final DocumentReference reportRef = db.collection(FirestoreRefs.REPORTS)
                .document(report.getId());
        final String[] centerToDelete = {null};

        db.runTransaction(transaction -> {
            DocumentReference userRef = db.collection(FirestoreRefs.USERS)
                    .document(deleted.authorUid);
            DocumentSnapshot userSnapshot = transaction.get(userRef);
            if (!userSnapshot.exists()) {
                throw new IllegalStateException("Không tìm thấy tài khoản tác giả");
            }

            String role = userSnapshot.getString("role");
            String centerId = userSnapshot.getString("centerId");
            String status = userSnapshot.getString("status");
            Date timeoutUntil = userSnapshot.getDate("timeoutUntil");

            boolean wasDeleted = User.STATUS_DELETED.equals(status);
            ModerationPolicy.Outcome outcome = ModerationPolicy.applyWarning(
                    longValue(userSnapshot, "warningCount"),
                    longValue(userSnapshot, "promoWarningCount"),
                    longValue(userSnapshot, "timeoutCount"),
                    wasDeleted, deleted.target.isPromo());
            long warnings = outcome.getWarningCount();
            long promoWarnings = outcome.getPromoWarningCount();
            long timeouts = outcome.getTimeoutCount();

            boolean revokeCenter = outcome.isCenterRevoked();
            String managedCenterId = null;
            if (revokeCenter) {
                managedCenterId = deleted.centerId;
                if (managedCenterId == null || managedCenterId.isEmpty()) {
                    managedCenterId = centerId;
                }
                role = User.ROLE_USER;
                centerId = null;
            }

            DocumentReference managedCenterRef = null;
            if (revokeCenter && managedCenterId != null && !managedCenterId.isEmpty()) {
                DocumentReference candidate = db.collection(FirestoreRefs.CENTERS)
                        .document(managedCenterId);
                if (transaction.get(candidate).exists()) {
                    managedCenterRef = candidate;
                }
            }

            if (outcome.isTimeoutApplied()) {
                if (outcome.isAccountDeleted()) {
                    status = User.STATUS_DELETED;
                    timeoutUntil = null;
                } else {
                    status = User.STATUS_TIMEOUT;
                    timeoutUntil = new Date(System.currentTimeMillis()
                            + ModerationPolicy.TIMEOUT_DURATION_MS);
                }
            }

            ContentKind contentKind = deleted.target.isPromo()
                    ? ContentKind.PROMO
                    : (deleted.target.isAnswer() ? ContentKind.ANSWER : ContentKind.QUESTION);
            AppNotification notification = ModerationNotifications.reportAction(
                    adminUid, report.getId(), deleted.authorUid, true, contentKind,
                    deleted.label, null, null, null,
                    revokeCenter ? null : deleted.centerId, report.getReason(), outcome);
            NotificationRepository.set(transaction, notification);

            Map<String, Object> userUpdate = new HashMap<>();
            userUpdate.put("warningCount", warnings);
            userUpdate.put("promoWarningCount", promoWarnings);
            userUpdate.put("timeoutCount", timeouts);
            if (revokeCenter) {
                userUpdate.put("role", role);
                userUpdate.put("centerId", centerId);
            }
            if (outcome.isTimeoutApplied()) {
                userUpdate.put("status", status);
                userUpdate.put("timeoutUntil", timeoutUntil);
            }
            transaction.update(userRef, userUpdate);

            if (managedCenterRef != null) {
                transaction.delete(managedCenterRef);
                centerToDelete[0] = managedCenterId;
            }

            Map<String, Object> reportUpdate = new HashMap<>();
            reportUpdate.put(FirestoreRefs.FIELD_STATUS, Report.STATUS_RESOLVED);
            reportUpdate.put("resolutionAction", "delete");
            reportUpdate.put(FIELD_RESOLUTION_NOTE, "Xoá nội dung");
            reportUpdate.put(FIELD_RESOLVED_BY, adminUid);
            reportUpdate.put(FIELD_RESOLVED_AT, FieldValue.serverTimestamp());
            transaction.update(reportRef, reportUpdate);

            transaction.set(db.collection(AUDIT_LOGS).document(),
                    auditData(adminUid, "REPORT_DELETED", report.getTargetPath(),
                            "Xoá nội dung" + (revokeCenter ? " · Xoá trung tâm" : "")));
            return centerToDelete[0];
        }).addOnSuccessListener(centerId -> {
            if (centerId != null) {
                cleanupRevokedCenter(centerId, () -> callback.onSuccess(null));
            } else {
                callback.onSuccess(null);
            }
        }).addOnFailureListener(callback::onError);
    }

    /** The content was already warned; deletion must not add another warning. */
    private void resolveDeletedDuplicate(Report report, Callback<Void> callback) {
        String adminUid = adminUid();
        if (adminUid == null || report == null || report.getId() == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin báo cáo"));
            return;
        }
        WriteBatch batch = db.batch();
        addReportResolution(batch, report, "delete",
                "Nội dung đã được xử lý trước đó", adminUid);
        audit(batch, "REPORT_DELETED", report.getTargetPath(), "Đã cảnh cáo trước đó");
        commit(batch, callback);
    }

    private void applyWarning(Report report, boolean delete, String targetAuthorId,
                              Callback<Void> callback) {
        String adminUid = adminUid();
        final Target target = parseTarget(report);
        if (adminUid == null || report == null || report.getId() == null || target == null) {
            callback.onError(new IllegalStateException("Thiếu thông tin báo cáo"));
            return;
        }
        final DocumentReference reportRef = db.collection(FirestoreRefs.REPORTS)
                .document(report.getId());
        final String[] centerToDelete = {null};
        final AppNotification[] deletionNotification = {null};
        final boolean[] deleteAfterDuplicate = {false};

        db.runTransaction(transaction -> {
            DocumentSnapshot targetSnapshot = transaction.get(target.ref);
            if (!targetSnapshot.exists()) {
                throw new IllegalStateException("Nội dung không còn tồn tại");
            }
            String authorUid = targetSnapshot.getString(target.authorField);
            if (authorUid == null) {
                throw new IllegalStateException("Không xác định được tác giả");
            }

            // One penalty per content: a second report on already-warned content only closes the
            // report (and still deletes it when asked), otherwise three reports on the same post
            // would fake three warnings.
            if (Boolean.TRUE.equals(targetSnapshot.getBoolean("warned"))) {
                Map<String, Object> duplicateUpdate = new HashMap<>();
                duplicateUpdate.put(FirestoreRefs.FIELD_STATUS, Report.STATUS_RESOLVED);
                duplicateUpdate.put("resolutionAction", delete ? "delete" : "warn");
                duplicateUpdate.put(FIELD_RESOLUTION_NOTE, delete
                        ? "Nội dung đã được cảnh cáo trước đó, xoá nội dung"
                        : "Nội dung đã được cảnh cáo trước đó");
                duplicateUpdate.put(FIELD_RESOLVED_BY, adminUid);
                duplicateUpdate.put(FIELD_RESOLVED_AT, FieldValue.serverTimestamp());
                transaction.update(reportRef, duplicateUpdate);
                transaction.set(db.collection(AUDIT_LOGS).document(),
                        auditData(adminUid, delete ? "REPORT_DELETED" : "REPORT_WARN_DUPLICATE",
                                report.getTargetPath(), "Đã cảnh cáo trước đó"));
                deleteAfterDuplicate[0] = delete;
                return false;
            }

            DocumentReference userRef = db.collection(FirestoreRefs.USERS).document(authorUid);
            DocumentSnapshot userSnapshot = transaction.get(userRef);
            if (!userSnapshot.exists()) {
                throw new IllegalStateException("Không tìm thấy tài khoản tác giả");
            }

            String role = userSnapshot.getString("role");
            String centerId = userSnapshot.getString("centerId");
            String status = userSnapshot.getString("status");
            Date timeoutUntil = userSnapshot.getDate("timeoutUntil");

            boolean wasDeleted = User.STATUS_DELETED.equals(status);
            ModerationPolicy.Outcome outcome = ModerationPolicy.applyWarning(
                    longValue(userSnapshot, "warningCount"),
                    longValue(userSnapshot, "promoWarningCount"),
                    longValue(userSnapshot, "timeoutCount"),
                    wasDeleted, target.isPromo());
            long warnings = outcome.getWarningCount();
            long promoWarnings = outcome.getPromoWarningCount();
            long timeouts = outcome.getTimeoutCount();

            boolean revokeCenter = outcome.isCenterRevoked();
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

            if (outcome.isTimeoutApplied()) {
                if (outcome.isAccountDeleted()) {
                    status = User.STATUS_DELETED;
                    timeoutUntil = null;
                } else {
                    status = User.STATUS_TIMEOUT;
                    timeoutUntil = new Date(System.currentTimeMillis()
                            + ModerationPolicy.TIMEOUT_DURATION_MS);
                }
            }

            // All reads are done; build the notification from the trusted target document. For a
            // deletion it is written in the deletion batch below, never before the content is gone.
            ContentKind contentKind;
            String contentLabel;
            String notificationQuestionId = null;
            String notificationAnswerId = null;
            String notificationPromoId = null;
            String notificationCenterId = null;
            if (target.isPromo()) {
                contentKind = ContentKind.PROMO;
                contentLabel = targetSnapshot.getString("title");
                notificationPromoId = (delete || revokeCenter) ? null : target.ref.getId();
                notificationCenterId = revokeCenter
                        ? null : targetSnapshot.getString(FirestoreRefs.FIELD_CENTER_ID);
            } else if (target.isAnswer()) {
                contentKind = ContentKind.ANSWER;
                contentLabel = targetSnapshot.getString("body");
                notificationQuestionId = target.questionId;
                notificationAnswerId = delete ? null : target.ref.getId();
            } else {
                contentKind = ContentKind.QUESTION;
                contentLabel = targetSnapshot.getString("title");
                notificationQuestionId = delete ? null : target.ref.getId();
            }
            AppNotification notification = ModerationNotifications.reportAction(
                    adminUid, report.getId(), authorUid, delete, contentKind,
                    contentLabel, notificationQuestionId, notificationAnswerId,
                    notificationPromoId, notificationCenterId, report.getReason(), outcome);
            if (delete) {
                deletionNotification[0] = notification;
            } else {
                NotificationRepository.set(transaction, notification);
            }

            Map<String, Object> userUpdate = new HashMap<>();
            userUpdate.put("warningCount", warnings);
            userUpdate.put("promoWarningCount", promoWarnings);
            userUpdate.put("timeoutCount", timeouts);
            if (revokeCenter) {
                userUpdate.put("role", role);
                userUpdate.put("centerId", centerId);
            }
            if (outcome.isTimeoutApplied()) {
                userUpdate.put("status", status);
                userUpdate.put("timeoutUntil", timeoutUntil);
            }
            transaction.update(userRef, userUpdate);

            if (managedCenterRef != null) {
                // Losing the CENTER role removes the centre itself. Its courses/posts and promos
                // are cleaned up right after the transaction commits.
                transaction.delete(managedCenterRef);
                centerToDelete[0] = managedCenterId;
            }

            transaction.update(target.ref,
                    "warned", true,
                    "warnedAt", FieldValue.serverTimestamp());

            Map<String, Object> reportUpdate = new HashMap<>();
            reportUpdate.put(FirestoreRefs.FIELD_STATUS, Report.STATUS_RESOLVED);
            reportUpdate.put("resolutionAction", delete ? "delete" : "warn");
            reportUpdate.put(FIELD_RESOLUTION_NOTE, delete ? "Xoá nội dung" : "Cảnh cáo");
            reportUpdate.put(FIELD_RESOLVED_BY, adminUid);
            reportUpdate.put(FIELD_RESOLVED_AT, FieldValue.serverTimestamp());
            transaction.update(reportRef, reportUpdate);

            transaction.set(db.collection(AUDIT_LOGS).document(),
                    auditData(adminUid, delete ? "REPORT_DELETED" : "REPORT_WARNED",
                            report.getTargetPath(),
                            (delete ? "Xoá nội dung" : "Cảnh cáo")
                                    + (revokeCenter ? " · Xoá trung tâm" : "")));
            return true;
        }).addOnSuccessListener(applied -> {
            if (!Boolean.TRUE.equals(applied)) {
                if (deleteAfterDuplicate[0]) {
                    // The content was already warned; deleting it does not add another warning or
                    // notification, but the admin must not be told "deleted" before it is gone.
                    deleteTarget(target, targetAuthorId, null, callback);
                } else {
                    // Already warned: the report is closed but no new penalty or notification.
                    callback.onSuccess(null);
                }
                return;
            }
            if (!delete) {
                // The warning notification committed with the warning/report in the transaction.
                callback.onSuccess(null);
                return;
            }
            final AppNotification notification = deletionNotification[0];
            Runnable finish = () -> deleteTarget(target, targetAuthorId, notification, callback);
            if (centerToDelete[0] != null) {
                cleanupRevokedCenter(centerToDelete[0], finish);
            } else {
                finish.run();
            }
        }).addOnFailureListener(callback::onError);
    }

    /**
     * Deletes the reported target and, when provided, commits its notification in the same batch.
     * A null notification is used when the content was already warned and only its removal remains.
     */
    private void deleteTarget(Target target, String targetAuthorId, AppNotification notification,
                              Callback<Void> callback) {
        if (target.isPromo()) {
            WriteBatch batch = db.batch();
            batch.delete(db.collection(FirestoreRefs.PROMOS).document(target.ref.getId()));
            NotificationRepository.addToBatch(batch, notification);
            batch.commit()
                    .addOnSuccessListener(v -> callback.onSuccess(null))
                    .addOnFailureListener(callback::onError);
        } else if (target.isAnswer()) {
            new AnswerRepository().delete(target.questionId, target.ref.getId(),
                    targetAuthorId, notification, callback);
        } else {
            new QuestionRepository().deleteQuestion(target.ref.getId(), targetAuthorId,
                    notification, callback);
        }
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

    /** Removes a revoked centre: sub-collections first, then its promos. */
    private void cleanupRevokedCenter(String centerId, Runnable done) {
        DocumentReference centerRef = db.collection(FirestoreRefs.CENTERS).document(centerId);
        deleteCollection(centerRef.collection("courses"), () ->
                deleteCollection(centerRef.collection("posts"), () ->
                        deleteCenterPromos(centerId, done)));
    }

    private void deleteCollection(CollectionReference collection, Runnable done) {
        collection.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                done.run();
                return;
            }
            deleteDocuments(task.getResult().getDocuments(), done);
        });
    }

    private void deleteCenterPromos(String centerId, Runnable done) {
        db.collection(FirestoreRefs.PROMOS).whereEqualTo("centerId", centerId).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        done.run();
                        return;
                    }
                    deleteDocuments(task.getResult().getDocuments(), done);
                });
    }

    private void deleteDocuments(List<DocumentSnapshot> documents, Runnable done) {
        if (documents == null || documents.isEmpty()) {
            done.run();
            return;
        }
        // Keep each batch under Firestore's 500-write limit.
        int end = Math.min(450, documents.size());
        WriteBatch batch = db.batch();
        for (int i = 0; i < end; i++) {
            batch.delete(documents.get(i).getReference());
        }
        List<DocumentSnapshot> rest = new ArrayList<>(documents.subList(end, documents.size()));
        batch.commit().addOnCompleteListener(task -> deleteDocuments(rest, done));
    }

    /** Facts captured before the target is deleted, used by the post-delete penalty transaction. */
    private static final class DeletedTarget {
        final Target target;
        final String authorUid;
        final String label;
        final String questionId;
        final String answerId;
        final String promoId;
        final String centerId;
        final boolean alreadyWarned;

        DeletedTarget(Target target, String authorUid, String label, String questionId,
                      String answerId, String promoId, String centerId, boolean alreadyWarned) {
            this.target = target;
            this.authorUid = authorUid;
            this.label = label;
            this.questionId = questionId;
            this.answerId = answerId;
            this.promoId = promoId;
            this.centerId = centerId;
            this.alreadyWarned = alreadyWarned;
        }
    }

    /** A report target: a question/promo ({@code authorId}/{@code authorUid}) or an answer. */
    private static final class Target {
        final DocumentReference ref;
        final ReportTargets.Type type;
        final String authorField;
        final String questionId;

        Target(DocumentReference ref, ReportTargets.Type type, String authorField,
               String questionId) {
            this.ref = ref;
            this.type = type;
            this.authorField = authorField;
            this.questionId = questionId;
        }

        boolean isPromo() {
            return type == ReportTargets.Type.PROMO;
        }

        boolean isAnswer() {
            return type == ReportTargets.Type.ANSWER;
        }
    }

    private Target parseTarget(Report report) {
        ReportTargets.Target parsed = ReportTargets.parse(
                report != null ? report.getTargetPath() : null);
        switch (parsed.getType()) {
            case PROMO:
                return new Target(db.collection(FirestoreRefs.PROMOS)
                        .document(parsed.getPromoId()), parsed.getType(), "authorUid", null);
            case ANSWER:
                return new Target(db.collection(FirestoreRefs.QUESTIONS)
                        .document(parsed.getQuestionId())
                        .collection(FirestoreRefs.ANSWERS)
                        .document(parsed.getAnswerId()),
                        parsed.getType(), "authorId", parsed.getQuestionId());
            case QUESTION:
                return new Target(db.collection(FirestoreRefs.QUESTIONS)
                        .document(parsed.getQuestionId()), parsed.getType(), "authorId", null);
            default:
                return null;
        }
    }

    private static long longValue(DocumentSnapshot snapshot, String field) {
        Long value = snapshot.getLong(field);
        return value != null ? value : 0L;
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
