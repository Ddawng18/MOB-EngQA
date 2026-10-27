package com.example.engqa.util;

import static org.junit.Assert.assertEquals;

import com.example.engqa.model.Answer;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/** Unit tests for the Reddit-style answer ordering. */
public class AnswerRankingTest {

    private static Answer answer(String id, long up, long down, boolean accepted, long createdAt) {
        Answer answer = new Answer();
        answer.setId(id);
        answer.setUpCount(up);
        answer.setDownCount(down);
        answer.setAccepted(accepted);
        answer.setCreatedAt(new Date(createdAt));
        return answer;
    }

    private static List<String> ids(List<Answer> answers) {
        return Arrays.asList(
                answers.get(0).getId(),
                answers.get(1).getId(),
                answers.get(2).getId(),
                answers.get(3).getId());
    }

    @Test
    public void sortsByNetScoreDescending() {
        List<Answer> sorted = AnswerRanking.sort(Arrays.asList(
                answer("low", 1, 0, false, 100),
                answer("high", 9, 1, false, 100),
                answer("mid", 3, 1, false, 100),
                answer("zero", 0, 0, false, 100)));

        assertEquals(Arrays.asList("high", "mid", "low", "zero"), ids(sorted));
    }

    @Test
    public void negativeScoresArePushedToTheBottom() {
        List<Answer> sorted = AnswerRanking.sort(Arrays.asList(
                answer("negative", 1, 4, false, 100),
                answer("positive", 2, 0, false, 100),
                answer("neutral", 1, 1, false, 100),
                answer("veryNegative", 0, 5, false, 100)));

        assertEquals(Arrays.asList("positive", "neutral", "negative", "veryNegative"),
                ids(sorted));
        assertEquals(-3, sorted.get(2).netScore());
    }

    @Test
    public void acceptedAnswerBreaksScoreTies() {
        List<Answer> sorted = AnswerRanking.sort(Arrays.asList(
                answer("normal", 2, 0, false, 100),
                answer("accepted", 2, 0, true, 50),
                answer("lower", 1, 0, false, 100),
                answer("zero", 0, 0, false, 100)));

        assertEquals("accepted", sorted.get(0).getId());
    }

    @Test
    public void newestBreaksRemainingTies() {
        List<Answer> sorted = AnswerRanking.sort(Arrays.asList(
                answer("old", 1, 0, false, 100),
                answer("new", 1, 0, false, 300),
                answer("middle", 1, 0, false, 200),
                answer("zero", 0, 0, false, 400)));

        assertEquals(Arrays.asList("new", "middle", "old", "zero"), ids(sorted));
    }

    @Test
    public void netScoreIsUpMinusDown() {
        assertEquals(5, answer("a", 7, 2, false, 0).netScore());
        assertEquals(-1, answer("b", 0, 1, false, 0).netScore());
        assertEquals(0, answer("c", 3, 3, false, 0).netScore());
    }
}
