package com.example.engqa.util;

/**
 * Stable document ids for moderation notifications.
 *
 * <p>Using a deterministic id turns a retried moderation action into an idempotent write: the same
 * report/action/recipient or center/promo/outcome/recipient always maps to one notification instead
 * of creating duplicates. Different reports or different outcomes get different ids, so history is
 * not collapsed across unrelated events.
 */
public final class NotificationIds {

    private NotificationIds() {
    }

    public static String reportAction(String reportId, String action, String recipientId) {
        return "moderation_" + safe(action) + "_" + safe(reportId) + "_" + safe(recipientId);
    }

    public static String centerDecision(String centerId, boolean approved, String recipientId) {
        return "moderation_center_" + (approved ? "approved" : "rejected")
                + "_" + safe(centerId) + "_" + safe(recipientId);
    }

    public static String promoDecision(String promoId, boolean approved, String recipientId) {
        return "moderation_promo_" + (approved ? "approved" : "rejected")
                + "_" + safe(promoId) + "_" + safe(recipientId);
    }

    private static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "unknown";
        }
        // Firestore document ids cannot contain '/'.
        return value.replace("/", "_");
    }
}
