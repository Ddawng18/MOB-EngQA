package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Unit tests for the report target paths shared by reporting and moderation. */
public class ReportTargetsTest {

    @Test
    public void questionPath_roundTrips() {
        ReportTargets.Target target = ReportTargets.parse(
                ReportTargets.questionPath("q1"));
        assertEquals(ReportTargets.Type.QUESTION, target.getType());
        assertEquals("q1", target.getQuestionId());
        assertNull(target.getAnswerId());
        assertNull(target.getPromoId());
    }

    @Test
    public void answerPath_keepsQuestionAndAnswerIds() {
        ReportTargets.Target target = ReportTargets.parse(
                ReportTargets.answerPath("q1", "a1"));
        assertEquals(ReportTargets.Type.ANSWER, target.getType());
        assertEquals("q1", target.getQuestionId());
        assertEquals("a1", target.getAnswerId());
        assertNull(target.getPromoId());
    }

    @Test
    public void promoPath_roundTrips() {
        ReportTargets.Target target = ReportTargets.parse(
                ReportTargets.promoPath("p1"));
        assertEquals(ReportTargets.Type.PROMO, target.getType());
        assertEquals("p1", target.getPromoId());
        assertNull(target.getQuestionId());
        assertNull(target.getAnswerId());
    }

    @Test
    public void answerPath_isNotParsedAsQuestion() {
        ReportTargets.Target target = ReportTargets.parse("questions/q1/answers/a1");
        assertEquals(ReportTargets.Type.ANSWER, target.getType());
        assertEquals("q1", target.getQuestionId());
        assertEquals("a1", target.getAnswerId());
    }

    @Test
    public void malformedPaths_areUnknown() {
        assertEquals(ReportTargets.Type.UNKNOWN, ReportTargets.parse(null).getType());
        assertEquals(ReportTargets.Type.UNKNOWN, ReportTargets.parse("").getType());
        assertEquals(ReportTargets.Type.UNKNOWN, ReportTargets.parse("questions/").getType());
        assertEquals(ReportTargets.Type.UNKNOWN,
                ReportTargets.parse("questions/q1/answers/").getType());
        assertEquals(ReportTargets.Type.UNKNOWN,
                ReportTargets.parse("questions//answers/a1").getType());
        assertEquals(ReportTargets.Type.UNKNOWN,
                ReportTargets.parse("answers/a1").getType());
        assertEquals(ReportTargets.Type.UNKNOWN,
                ReportTargets.parse("questions/q1/answers/a1/extra").getType());
    }
}
