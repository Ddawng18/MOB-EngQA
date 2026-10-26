package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the project's existing warning, timeout and centre-revocation policy. */
public class ModerationPolicyTest {

    @Test
    public void firstAndSecondWarningsShowRemainingBeforeTimeout() {
        ModerationPolicy.Outcome first = ModerationPolicy.applyWarning(0, 0, 0, false, false);
        assertEquals(1, first.getWarningNumber());
        assertEquals(1, first.getWarningCount());
        assertEquals(2, first.getRemainingWarnings());
        assertFalse(first.isTimeoutApplied());
        assertFalse(first.isAccountDeleted());

        ModerationPolicy.Outcome second = ModerationPolicy.applyWarning(1, 0, 0, false, false);
        assertEquals(2, second.getWarningNumber());
        assertEquals(2, second.getWarningCount());
        assertEquals(1, second.getRemainingWarnings());
        assertFalse(second.isTimeoutApplied());
    }

    @Test
    public void thirdWarningAppliesTimeoutAndResetsWarningCounter() {
        ModerationPolicy.Outcome outcome = ModerationPolicy.applyWarning(2, 0, 0, false, false);

        assertEquals(3, outcome.getWarningNumber());
        assertEquals(0, outcome.getWarningCount());
        assertEquals(0, outcome.getRemainingWarnings());
        assertTrue(outcome.isTimeoutApplied());
        assertEquals(1, outcome.getTimeoutCount());
        assertEquals(2, outcome.getRemainingTimeouts());
        assertFalse(outcome.isAccountDeleted());
        assertEquals(24, outcome.getTimeoutDurationHours());
    }

    @Test
    public void thirdTimeoutSoftDeletesAccountWithNoNegativeRemainingCount() {
        ModerationPolicy.Outcome outcome = ModerationPolicy.applyWarning(2, 0, 2, false, false);

        assertTrue(outcome.isTimeoutApplied());
        assertTrue(outcome.isAccountDeleted());
        assertEquals(3, outcome.getTimeoutCount());
        assertEquals(0, outcome.getRemainingTimeouts());
        assertEquals(0, outcome.getRemainingWarnings());
    }

    @Test
    public void promoWarningUsesSeparateCentreCounter() {
        ModerationPolicy.Outcome outcome = ModerationPolicy.applyWarning(0, 1, 0, false, true);

        assertEquals(1, outcome.getWarningCount());
        assertEquals(2, outcome.getPromoWarningCount());
        assertEquals(1, outcome.getRemainingPromoWarnings());
        assertFalse(outcome.isCenterRevoked());
    }

    @Test
    public void thirdPromoWarningRevokesCentre() {
        ModerationPolicy.Outcome outcome = ModerationPolicy.applyWarning(0, 2, 0, false, true);

        assertEquals(3, outcome.getPromoWarningCount());
        assertEquals(0, outcome.getRemainingPromoWarnings());
        assertTrue(outcome.isCenterRevoked());
    }

    @Test
    public void userWarningNeverChangesPromoCounter() {
        ModerationPolicy.Outcome outcome = ModerationPolicy.applyWarning(1, 0, 0, false, false);

        assertEquals(0, outcome.getPromoWarningCount());
        assertEquals(0, outcome.getRemainingPromoWarnings());
        assertFalse(outcome.isCenterRevoked());
    }

    @Test
    public void countsAreClampedAtZeroAndRemainingNeverNegative() {
        assertEquals(3, ModerationPolicy.remainingWarningsBeforeTimeout(-5));
        assertEquals(0, ModerationPolicy.remainingWarningsBeforeTimeout(99));
        assertEquals(3, ModerationPolicy.remainingTimeoutsBeforeDeletion(-1));
        assertEquals(0, ModerationPolicy.remainingTimeoutsBeforeDeletion(99));
        assertEquals(3, ModerationPolicy.remainingPromoWarningsBeforeRevocation(-1));
        assertEquals(0, ModerationPolicy.remainingPromoWarningsBeforeRevocation(99));
    }

    @Test
    public void promoViolationAlsoCountsTowardAccountTimeout() {
        ModerationPolicy.Outcome outcome = ModerationPolicy.applyWarning(2, 0, 0, false, true);

        assertTrue(outcome.isTimeoutApplied());
        assertEquals(1, outcome.getTimeoutCount());
        assertEquals(1, outcome.getPromoWarningCount());
        assertFalse(outcome.isCenterRevoked());
    }
}
