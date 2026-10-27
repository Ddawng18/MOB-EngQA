package com.example.engqa.util;

import com.example.engqa.model.User;

/**
 * Single source of truth for the penalties already defined by EngQA.
 *
 * <p>The thresholds come from {@link User}; this class only calculates the committed counters and
 * the remaining counts that moderation notifications display. It deliberately has no Android or
 * Firestore dependency so the same rules can be unit-tested and reused by the admin repository.
 */
public final class ModerationPolicy {

    public static final long WARNINGS_PER_TIMEOUT = User.WARNINGS_PER_TIMEOUT;
    public static final long TIMEOUTS_BEFORE_DELETE = User.TIMEOUTS_BEFORE_DELETE;
    public static final long PROMO_WARNINGS_BEFORE_CENTER_REVOKE =
            User.PROMO_WARNINGS_BEFORE_CENTER_REVOKE;
    public static final long TIMEOUT_DURATION_MS = User.TIMEOUT_DURATION_MS;

    private ModerationPolicy() {
    }

    /** Remaining warnings in the current cycle before the next timeout. Never negative. */
    public static long remainingWarningsBeforeTimeout(long warningCount) {
        return Math.max(0L, WARNINGS_PER_TIMEOUT - safeCount(warningCount));
    }

    /** Remaining timeout events before the account reaches the soft-delete threshold. */
    public static long remainingTimeoutsBeforeDeletion(long timeoutCount) {
        return Math.max(0L, TIMEOUTS_BEFORE_DELETE - safeCount(timeoutCount));
    }

    /** Remaining promo warnings before the centre loses its CENTER permission. */
    public static long remainingPromoWarningsBeforeRevocation(long promoWarningCount) {
        return Math.max(0L,
                PROMO_WARNINGS_BEFORE_CENTER_REVOKE - safeCount(promoWarningCount));
    }

    public static long timeoutDurationHours() {
        return TIMEOUT_DURATION_MS / (60L * 60L * 1000L);
    }

    /**
     * Applies one successful warning exactly as the moderation transaction does.
     *
     * <p>A promo violation increments both the account warning counter (the account can still be
     * timed out) and the separate promo warning counter (which controls centre revocation). A
     * non-promo violation never changes the promo counter.
     */
    public static Outcome applyWarning(long warningCount, long promoWarningCount,
                                       long timeoutCount, boolean wasDeleted,
                                       boolean promoViolation) {
        long warnings = safeCount(warningCount) + 1;
        long warningNumber = warnings;
        long promoWarnings = safeCount(promoWarningCount) + (promoViolation ? 1 : 0);
        long timeouts = safeCount(timeoutCount);
        long remainingWarnings = remainingWarningsBeforeTimeout(warnings);

        boolean timeoutApplied = warnings >= WARNINGS_PER_TIMEOUT;
        boolean accountDeleted = wasDeleted;
        if (timeoutApplied) {
            // The stored warning counter restarts after a timeout; the notification must not show
            // "0 warnings remaining" as if the timeout had not happened.
            warnings = 0;
            remainingWarnings = 0;
            timeouts += 1;
            accountDeleted = wasDeleted || timeouts >= TIMEOUTS_BEFORE_DELETE;
        }

        boolean centerRevoked = promoViolation
                && promoWarnings >= PROMO_WARNINGS_BEFORE_CENTER_REVOKE;

        return new Outcome(warningNumber, warnings, remainingWarnings, timeoutApplied, timeouts,
                remainingTimeoutsBeforeDeletion(timeouts), accountDeleted, promoWarnings,
                promoViolation ? remainingPromoWarningsBeforeRevocation(promoWarnings) : 0L,
                centerRevoked, promoViolation, timeoutDurationHours());
    }

    private static long safeCount(long value) {
        return Math.max(0L, value);
    }

    /** Immutable result of a committed warning action. */
    public static final class Outcome {

        private final long warningNumber;
        private final long warningCount;
        private final long remainingWarnings;
        private final boolean timeoutApplied;
        private final long timeoutCount;
        private final long remainingTimeouts;
        private final boolean accountDeleted;
        private final long promoWarningCount;
        private final long remainingPromoWarnings;
        private final boolean centerRevoked;
        private final boolean promoViolation;
        private final long timeoutDurationHours;

        private Outcome(long warningNumber, long warningCount, long remainingWarnings,
                        boolean timeoutApplied, long timeoutCount, long remainingTimeouts,
                        boolean accountDeleted, long promoWarningCount,
                        long remainingPromoWarnings, boolean centerRevoked, boolean promoViolation,
                        long timeoutDurationHours) {
            this.warningNumber = warningNumber;
            this.warningCount = warningCount;
            this.remainingWarnings = remainingWarnings;
            this.timeoutApplied = timeoutApplied;
            this.timeoutCount = timeoutCount;
            this.remainingTimeouts = remainingTimeouts;
            this.accountDeleted = accountDeleted;
            this.promoWarningCount = promoWarningCount;
            this.remainingPromoWarnings = remainingPromoWarnings;
            this.centerRevoked = centerRevoked;
            this.promoViolation = promoViolation;
            this.timeoutDurationHours = timeoutDurationHours;
        }

        /** The ordinal of the warning event that just happened (before any timeout reset). */
        public long getWarningNumber() {
            return warningNumber;
        }

        /** The warning counter stored after the action (0 when a timeout was just applied). */
        public long getWarningCount() {
            return warningCount;
        }

        public long getRemainingWarnings() {
            return remainingWarnings;
        }

        public boolean isTimeoutApplied() {
            return timeoutApplied;
        }

        public long getTimeoutCount() {
            return timeoutCount;
        }

        public long getRemainingTimeouts() {
            return remainingTimeouts;
        }

        public boolean isAccountDeleted() {
            return accountDeleted;
        }

        public long getPromoWarningCount() {
            return promoWarningCount;
        }

        public long getRemainingPromoWarnings() {
            return remainingPromoWarnings;
        }

        public boolean isCenterRevoked() {
            return centerRevoked;
        }

        public boolean isPromoViolation() {
            return promoViolation;
        }

        public long getTimeoutDurationHours() {
            return timeoutDurationHours;
        }
    }
}
