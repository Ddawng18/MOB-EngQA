package com.example.engqa.util;

import com.example.engqa.model.Promo;
import com.example.engqa.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic relevance ranking over a bounded candidate set retrieved from Firestore.
 *
 * <p>Firestore supplies candidates that contain at least one query token (or a typo variant).
 * This class then applies the actual information-retrieval policy: exact title/phrase first,
 * then title prefix/all-term, then body/tag/partial/fuzzy matches. The user's chosen feed sort is
 * used only as a tie-breaker so relevance is never silently replaced by creation date or score.
 */
public final class SearchRanker {

    private SearchRanker() {
    }

    /**
     * Ranks questions by relevance. {@code tieBreaker} should express the selected feed sort
     * (newest/popular) and is applied after the relevance score.
     */
    public static List<Question> rankQuestions(List<Question> candidates, String query,
                                               Comparator<Question> tieBreaker) {
        List<Question> result = new ArrayList<>();
        if (candidates == null) {
            return result;
        }
        List<String> queryTokens = TextUtils2.queryTokens(query);
        if (queryTokens.isEmpty()) {
            if (query == null || query.trim().isEmpty()) {
                result.addAll(candidates);
            }
            return result;
        }
        String phrase = String.join(" ", queryTokens);
        int minCoverage = minCoverage(queryTokens.size());

        List<ScoredQuestion> scored = new ArrayList<>();
        for (Question question : candidates) {
            if (question == null) {
                continue;
            }
            QuestionScore score = scoreQuestion(question, queryTokens, phrase);
            if (score.coverage < minCoverage) {
                continue;
            }
            scored.add(new ScoredQuestion(question, score.value));
        }
        scored.sort((left, right) -> {
            int byScore = Integer.compare(right.score, left.score);
            if (byScore != 0) {
                return byScore;
            }
            if (tieBreaker != null) {
                int byTieBreaker = tieBreaker.compare(left.question, right.question);
                if (byTieBreaker != 0) {
                    return byTieBreaker;
                }
            }
            return stableKey(left.question).compareTo(stableKey(right.question));
        });
        for (ScoredQuestion item : scored) {
            result.add(item.question);
        }
        return result;
    }

    /**
     * Ranks promos for the "Trung tâm" feed. The incoming list is already in Firestore order, so a
     * stable sort by relevance preserves that order for equal scores.
     */
    public static List<Promo> rankPromos(List<Promo> promos, String query) {
        List<Promo> result = new ArrayList<>();
        if (promos == null) {
            return result;
        }
        List<String> queryTokens = TextUtils2.queryTokens(query);
        if (queryTokens.isEmpty()) {
            // Empty/whitespace means "no filter"; a non-empty query such as "a" or "!!" cannot
            // match tokens and must not fall back to showing every promo.
            if (query == null || query.trim().isEmpty()) {
                result.addAll(promos);
            }
            return result;
        }
        String phrase = String.join(" ", queryTokens);
        int minCoverage = minCoverage(queryTokens.size());

        List<ScoredPromo> scored = new ArrayList<>();
        for (Promo promo : promos) {
            if (promo == null) {
                continue;
            }
            int score = scorePromo(promo, queryTokens, phrase);
            if (score < 0) {
                continue;
            }
            scored.add(new ScoredPromo(promo, score));
        }
        scored.sort((left, right) -> Integer.compare(right.score, left.score));
        for (ScoredPromo item : scored) {
            result.add(item.promo);
        }
        return result;
    }

    private static QuestionScore scoreQuestion(Question question, List<String> queryTokens,
                                               String phrase) {
        String title = TextUtils2.normalize(question.getTitle());
        String body = TextUtils2.normalize(question.getBody());
        String tags = TextUtils2.normalize(join(question.getTags()));
        String category = TextUtils2.normalize(question.getCategoryName());
        String level = TextUtils2.normalize(question.getLevel());

        List<String> titleTokens = tokens(title);
        List<String> bodyTokens = tokens(body);
        List<String> tagTokens = tokens(tags);
        List<String> categoryTokens = tokens(category + " " + level);

        int score = 0;
        if (title.equals(phrase)) {
            score += 3000;
        } else if (title.startsWith(phrase)) {
            score += 1800;
        } else if (title.contains(phrase)) {
            score += 1200;
        }
        if (body.contains(phrase)) {
            score += 600;
        }

        int coverage = 0;
        int titleCoverage = 0;
        for (String token : queryTokens) {
            int titleQuality = bestQuality(token, titleTokens);
            int tagQuality = bestQuality(token, tagTokens);
            int bodyQuality = bestQuality(token, bodyTokens);
            int categoryQuality = bestQuality(token, categoryTokens);
            if (titleQuality > 0) {
                titleCoverage++;
            }
            if (titleQuality > 0 || tagQuality > 0 || bodyQuality > 0 || categoryQuality > 0) {
                coverage++;
            }
            score += qualityScore(titleQuality, 200, 120, 40);
            score += qualityScore(tagQuality, 120, 70, 25);
            score += qualityScore(bodyQuality, 60, 35, 10);
            score += qualityScore(categoryQuality, 25, 15, 5);
        }
        if (titleCoverage == queryTokens.size()) {
            score += 800;
        }
        if (coverage == queryTokens.size()) {
            score += 500;
        }
        return new QuestionScore(score, coverage);
    }

    /** Returns -1 when the promo does not meet the minimum query coverage. */
    private static int scorePromo(Promo promo, List<String> queryTokens, String phrase) {
        String title = TextUtils2.normalize(promo.getTitle());
        String center = TextUtils2.normalize(promo.getCenterName());
        String body = TextUtils2.normalize(promo.getBody());
        String level = TextUtils2.normalize(promo.getLevel());
        String type = TextUtils2.normalize(promo.getType());
        String fee = TextUtils2.normalize(promo.getFee());
        String schedule = TextUtils2.normalize(promo.getSchedule());

        List<String> titleTokens = tokens(title);
        List<String> centerTokens = tokens(center);
        List<String> bodyTokens = tokens(body);
        List<String> metaTokens = tokens(level + " " + type + " " + fee + " " + schedule);

        int score = 0;
        if (title.equals(phrase)) {
            score += 2200;
        } else if (title.startsWith(phrase)) {
            score += 1400;
        } else if (title.contains(phrase)) {
            score += 900;
        }
        if (center.equals(phrase)) {
            score += 1400;
        } else if (center.startsWith(phrase)) {
            score += 900;
        } else if (center.contains(phrase)) {
            score += 600;
        }
        if (body.contains(phrase)) {
            score += 400;
        }

        int coverage = 0;
        int titleCoverage = 0;
        for (String token : queryTokens) {
            int titleQuality = bestQuality(token, titleTokens);
            int centerQuality = bestQuality(token, centerTokens);
            int bodyQuality = bestQuality(token, bodyTokens);
            int metaQuality = bestQuality(token, metaTokens);
            if (titleQuality > 0) {
                titleCoverage++;
            }
            if (titleQuality > 0 || centerQuality > 0 || bodyQuality > 0 || metaQuality > 0) {
                coverage++;
            }
            score += qualityScore(titleQuality, 180, 100, 35);
            score += qualityScore(centerQuality, 140, 80, 25);
            score += qualityScore(bodyQuality, 40, 22, 8);
            score += qualityScore(metaQuality, 40, 22, 8);
        }
        if (titleCoverage == queryTokens.size()) {
            score += 500;
        }
        if (coverage == queryTokens.size()) {
            score += 300;
        }
        return coverage < minCoverage(queryTokens.size()) ? -1 : score;
    }

    /**
     * Exact = 3, prefix = 2, bounded one/two-character typo = 1, no match = 0. Prefixes let a
     * partial query such as "pha" find "phap"; fuzzy matching is only considered after exact and
     * prefix checks, and short tokens stay exact to avoid noise.
     */
    private static int bestQuality(String queryToken, List<String> documentTokens) {
        boolean prefix = false;
        int maxDistance = TextUtils2.maxEditDistance(queryToken.length());
        for (String documentToken : documentTokens) {
            if (documentToken.equals(queryToken)) {
                return 3;
            }
            if (documentToken.length() > queryToken.length()
                    && documentToken.startsWith(queryToken)) {
                prefix = true;
            }
        }
        if (prefix) {
            return 2;
        }
        if (maxDistance == 0) {
            return 0;
        }
        for (String documentToken : documentTokens) {
            if (documentToken.isEmpty() || queryToken.isEmpty()
                    || documentToken.charAt(0) != queryToken.charAt(0)
                    || Math.abs(documentToken.length() - queryToken.length()) > maxDistance) {
                continue;
            }
            if (TextUtils2.editDistance(queryToken, documentToken) <= maxDistance) {
                return 1;
            }
        }
        return 0;
    }

    private static int qualityScore(int quality, int exact, int prefix, int fuzzy) {
        if (quality == 3) {
            return exact;
        }
        if (quality == 2) {
            return prefix;
        }
        if (quality == 1) {
            return fuzzy;
        }
        return 0;
    }

    private static int minCoverage(int tokenCount) {
        if (tokenCount <= 2) {
            return tokenCount;
        }
        return Math.max(2, (int) Math.ceil(tokenCount * 0.6));
    }

    private static List<String> tokens(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split(" ")) {
            if (token.length() >= TextUtils2.MIN_TOKEN_LENGTH) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static String stableKey(Question question) {
        if (question.getId() != null) {
            return question.getId();
        }
        return TextUtils2.normalize(question.getTitle());
    }

    private static final class QuestionScore {
        final int value;
        final int coverage;

        QuestionScore(int value, int coverage) {
            this.value = value;
            this.coverage = coverage;
        }
    }

    private static final class ScoredQuestion {
        final Question question;
        final int score;

        ScoredQuestion(Question question, int score) {
            this.question = question;
            this.score = score;
        }
    }

    private static final class ScoredPromo {
        final Promo promo;
        final int score;

        ScoredPromo(Promo promo, int score) {
            this.promo = promo;
            this.score = score;
        }
    }
}
