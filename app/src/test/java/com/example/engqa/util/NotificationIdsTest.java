package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/** Unit tests for the deterministic ids that prevent duplicate moderation notifications. */
public class NotificationIdsTest {

    @Test
    public void reportAction_isStableAndDistinguishesOutcomeAndRecipient() {
        String warning = NotificationIds.reportAction("r1", "warning", "u1");
        assertEquals(warning, NotificationIds.reportAction("r1", "warning", "u1"));
        assertNotEquals(warning, NotificationIds.reportAction("r1", "deleted", "u1"));
        assertNotEquals(warning, NotificationIds.reportAction("r1", "warning", "u2"));
        assertNotEquals(warning, NotificationIds.reportAction("r2", "warning", "u1"));
    }

    @Test
    public void centerDecision_isStableAndDistinguishesOutcome() {
        String approved = NotificationIds.centerDecision("c1", true, "u1");
        assertEquals(approved, NotificationIds.centerDecision("c1", true, "u1"));
        assertNotEquals(approved, NotificationIds.centerDecision("c1", false, "u1"));
        assertNotEquals(approved, NotificationIds.centerDecision("c2", true, "u1"));
    }

    @Test
    public void promoDecision_isStableAndDistinguishesOutcome() {
        String rejected = NotificationIds.promoDecision("p1", false, "u1");
        assertEquals(rejected, NotificationIds.promoDecision("p1", false, "u1"));
        assertNotEquals(rejected, NotificationIds.promoDecision("p1", true, "u1"));
    }

    @Test
    public void ids_neverContainPathSeparator() {
        String id = NotificationIds.reportAction("reports/r1", "warning", "users/u1");
        assertEquals(-1, id.indexOf('/'));
    }
}
