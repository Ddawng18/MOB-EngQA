package com.example.engqa.util;

import com.example.engqa.model.Answer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Reddit-style ordering for answers.
 *
 * <p>Order: highest net score first (up votes minus down votes, so a heavily down-voted answer can
 * be negative and is pushed to the bottom), then the accepted answer, then the newest.
 */
public final class AnswerRanking {

    public static final Comparator<Answer> BY_SCORE = (a, b) -> {
        int byScore = Long.compare(b.netScore(), a.netScore());
        if (byScore != 0) {
            return byScore;
        }
        if (a.isAccepted() != b.isAccepted()) {
            return a.isAccepted() ? -1 : 1;
        }
        Date first = a.getCreatedAt();
        Date second = b.getCreatedAt();
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return 1;
        }
        if (second == null) {
            return -1;
        }
        return second.compareTo(first);
    };

    private AnswerRanking() {
    }

    public static List<Answer> sort(List<Answer> answers) {
        List<Answer> copy = new ArrayList<>(answers);
        copy.sort(BY_SCORE);
        return copy;
    }
}
