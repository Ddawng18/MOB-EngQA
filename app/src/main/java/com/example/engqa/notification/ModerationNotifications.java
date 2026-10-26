package com.example.engqa.notification;

import android.content.Context;

import com.example.engqa.EngQAApp;
import com.example.engqa.R;
import com.example.engqa.model.AppNotification;
import com.example.engqa.model.Center;
import com.example.engqa.model.Promo;
import com.example.engqa.util.ModerationPolicy;
import com.example.engqa.util.NotificationIds;

import java.util.List;

/**
 * Builds the in-app notifications created by the moderation console.
 *
 * <p>Only the server-side action result should call these factories: a warning notification is
 * written in the same transaction as the warning, a deletion notification is written in the same
 * batch as the deletion, and center/promo decisions are written with their status change.
 *
 * <p>Every warning notification contains the committed warning/timeout counts and the remaining
 * events before the next penalty. A warning that triggers a timeout carries the timeout details in
 * the same document instead of producing a second, duplicate notification for one action.
 */
public final class ModerationNotifications {

    public enum ContentKind {
        QUESTION,
        ANSWER,
        PROMO
    }

    private static final int MAX_TITLE = 120;
    private static final int MAX_BODY = 300;
    private static final int MAX_CONTENT = 45;
    private static final int MAX_REASON = 50;
    private static final int MAX_NOTE = 100;
    private static final int MAX_NAME = 80;

    private ModerationNotifications() {
    }

    /**
     * Warning or deletion notification caused by a processed report.
     *
     * <p>{@code outcome} must be the result calculated in the transaction that committed the
     * counters; its counts are written into the notification so both refer to the same state.
     *
     * @param delete true only when the caller will delete the target in the same atomic operation
     *               as this notification.
     */
    public static AppNotification reportAction(String adminUid, String reportId,
                                               String recipientId, boolean delete, ContentKind kind,
                                               String contentLabel, String questionId,
                                               String answerId, String promoId, String centerId,
                                               String reason, ModerationPolicy.Outcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("Missing moderation outcome");
        }
        Context context = EngQAApp.getContext();
        String content = describeContent(context, kind, contentLabel);
        String why = clip(safe(reason, context.getString(R.string.notification_no_reason)),
                MAX_REASON);

        String title = title(context, kind, delete, outcome);
        String body = penalty(context, kind, outcome);
        if (delete) {
            body = context.getString(R.string.notification_deleted_prefix) + " " + body;
        }
        body = body + " " + context.getString(R.string.notification_body_tail, content, why);

        AppNotification notification = base(context, adminUid, recipientId,
                delete ? AppNotification.TYPE_MODERATION_DELETED
                        : AppNotification.TYPE_MODERATION_WARNING,
                title, clip(body, MAX_BODY));
        notification.setId(NotificationIds.reportAction(reportId,
                delete ? "deleted" : "warning", recipientId));
        notification.setReportId(reportId);
        notification.setQuestionId(questionId);
        notification.setAnswerId(answerId);
        notification.setPromoId(promoId);
        notification.setCenterId(centerId);
        return notification;
    }

    public static AppNotification centerDecision(String adminUid, Center center,
                                                 boolean approved, String note) {
        if (center == null) {
            return null;
        }
        String recipientId = centerRecipient(center);
        if (recipientId == null) {
            return null;
        }
        Context context = EngQAApp.getContext();
        String name = clip(safe(center.getName(),
                context.getString(R.string.notification_unknown_center_name)), MAX_NAME);
        String title = context.getString(approved
                ? R.string.notification_center_approved_title
                : R.string.notification_center_rejected_title);
        String body = approved
                ? context.getString(R.string.notification_center_approved_body, name)
                : context.getString(R.string.notification_center_rejected_body, name,
                        clip(safe(note, context.getString(R.string.notification_no_review_note)),
                                MAX_NOTE));

        AppNotification notification = base(context, adminUid, recipientId,
                approved ? AppNotification.TYPE_CENTER_APPROVED
                        : AppNotification.TYPE_CENTER_REJECTED,
                title, body);
        notification.setId(NotificationIds.centerDecision(center.getId(), approved, recipientId));
        notification.setCenterId(center.getId());
        return notification;
    }

    public static AppNotification promoDecision(String adminUid, Promo promo,
                                                boolean approved, String note) {
        if (promo == null || promo.getAuthorUid() == null) {
            return null;
        }
        String recipientId = promo.getAuthorUid();
        Context context = EngQAApp.getContext();
        String name = clip(safe(promo.getTitle(),
                context.getString(R.string.notification_unknown_promo)), MAX_NAME);
        String title = context.getString(approved
                ? R.string.notification_promo_approved_title
                : R.string.notification_promo_rejected_title);
        String body = approved
                ? context.getString(R.string.notification_promo_approved_body, name)
                : context.getString(R.string.notification_promo_rejected_body, name,
                        clip(safe(note, context.getString(R.string.notification_no_review_note)),
                                MAX_NOTE));

        AppNotification notification = base(context, adminUid, recipientId,
                approved ? AppNotification.TYPE_PROMO_APPROVED
                        : AppNotification.TYPE_PROMO_REJECTED,
                title, body);
        notification.setId(NotificationIds.promoDecision(promo.getPromoId(), approved,
                recipientId));
        notification.setPromoId(promo.getPromoId());
        notification.setCenterId(promo.getCenterId());
        return notification;
    }

    private static String title(Context context, ContentKind kind, boolean delete,
                                ModerationPolicy.Outcome outcome) {
        long number = outcome.getWarningNumber();
        if (kind == ContentKind.PROMO) {
            long promoNumber = outcome.getPromoWarningCount();
            long threshold = ModerationPolicy.PROMO_WARNINGS_BEFORE_CENTER_REVOKE;
            if (outcome.isCenterRevoked()) {
                return context.getString(R.string.notification_promo_revoked_title);
            }
            if (delete) {
                return context.getString(R.string.notification_promo_deleted_title);
            }
            return context.getString(R.string.notification_promo_warning_title, promoNumber,
                    threshold);
        }

        if (outcome.isAccountDeleted()) {
            return context.getString(R.string.notification_warning_account_deleted_title);
        }
        if (delete) {
            return context.getString(R.string.notification_deleted_title);
        }
        long threshold = ModerationPolicy.WARNINGS_PER_TIMEOUT;
        if (outcome.isTimeoutApplied()) {
            return context.getString(R.string.notification_warning_timeout_title, number, threshold);
        }
        return context.getString(R.string.notification_warning_progress_title, number, threshold);
    }

    private static String penalty(Context context, ContentKind kind,
                                  ModerationPolicy.Outcome outcome) {
        long number = outcome.getWarningNumber();
        if (kind == ContentKind.PROMO) {
            long promoNumber = outcome.getPromoWarningCount();
            long threshold = ModerationPolicy.PROMO_WARNINGS_BEFORE_CENTER_REVOKE;
            String text = outcome.isCenterRevoked()
                    ? context.getString(R.string.notification_promo_revoked_penalty, promoNumber,
                            threshold)
                    : context.getString(R.string.notification_promo_progress_penalty, promoNumber,
                            threshold, outcome.getRemainingPromoWarnings());
            if (outcome.isAccountDeleted()) {
                text = text + " " + context.getString(R.string.notification_center_deleted_suffix);
            } else if (outcome.isTimeoutApplied()) {
                text = text + " " + context.getString(R.string.notification_center_timeout_suffix,
                        outcome.getTimeoutDurationHours(), outcome.getTimeoutCount(),
                        ModerationPolicy.TIMEOUTS_BEFORE_DELETE, outcome.getRemainingTimeouts());
            }
            return text;
        }

        long threshold = ModerationPolicy.WARNINGS_PER_TIMEOUT;
        if (outcome.isAccountDeleted()) {
            return context.getString(R.string.notification_warning_account_deleted_penalty,
                    number, threshold);
        }
        if (outcome.isTimeoutApplied()) {
            return context.getString(R.string.notification_warning_timeout_penalty, number,
                    threshold, outcome.getTimeoutDurationHours(), outcome.getTimeoutCount(),
                    ModerationPolicy.TIMEOUTS_BEFORE_DELETE, outcome.getRemainingTimeouts());
        }
        return context.getString(R.string.notification_warning_progress_penalty, number,
                threshold, outcome.getRemainingWarnings(), outcome.getTimeoutDurationHours());
    }

    /** The owner is the authenticated center account; fall back to the first manager if needed. */
    private static String centerRecipient(Center center) {
        if (center.getOwnerUid() != null && !center.getOwnerUid().isEmpty()) {
            return center.getOwnerUid();
        }
        List<String> managers = center.getManagerUids();
        if (managers != null) {
            for (String manager : managers) {
                if (manager != null && !manager.isEmpty()) {
                    return manager;
                }
            }
        }
        return null;
    }

    private static AppNotification base(Context context, String adminUid, String recipientId,
                                        String type, String title, String body) {
        AppNotification notification = new AppNotification();
        notification.setRecipientId(recipientId);
        notification.setActorId(adminUid);
        notification.setActorName(context.getString(R.string.notification_actor_admin));
        notification.setType(type);
        notification.setTitle(clip(title, MAX_TITLE));
        notification.setBody(clip(body, MAX_BODY));
        notification.setRead(false);
        return notification;
    }

    private static String describeContent(Context context, ContentKind kind, String value) {
        String content = clip(safe(value, context.getString(R.string.notification_unknown_content)),
                MAX_CONTENT);
        int formatRes;
        switch (kind) {
            case ANSWER:
                formatRes = R.string.notification_content_answer;
                break;
            case PROMO:
                formatRes = R.string.notification_content_promo;
                break;
            case QUESTION:
            default:
                formatRes = R.string.notification_content_question;
                break;
        }
        return context.getString(formatRes, content);
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static String clip(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim().replaceAll("\\s+", " ");
        if (text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 1) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 1).trim() + "…";
    }
}
