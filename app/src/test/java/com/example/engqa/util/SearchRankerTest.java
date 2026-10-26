package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.engqa.model.Promo;
import com.example.engqa.model.Question;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** Unit tests for the deterministic relevance ranking used after Firestore candidate retrieval. */
public class SearchRankerTest {

    private static final long BASE_TIME = 1_700_000_000_000L;

    private static Question question(String id, String title, String body) {
        return question(id, title, body, 0, 0);
    }

    private static Question question(String id, String title, String body, long score,
                                     long ageMinutes) {
        Question question = new Question();
        question.setId(id);
        question.setTitle(title);
        question.setBody(body);
        question.setCategoryName("Ngữ pháp");
        question.setTags(Collections.singletonList("Ngữ pháp"));
        question.setLevel("Intermediate");
        question.setScore(score);
        question.setCreatedAt(new Date(BASE_TIME - ageMinutes * 60_000L));
        return question;
    }

    private static Promo promo(String id, String title, String center, String body) {
        Promo promo = new Promo();
        promo.setPromoId(id);
        promo.setTitle(title);
        promo.setCenterName(center);
        promo.setBody(body);
        promo.setLevel("B2");
        return promo;
    }

    private static List<String> ids(List<Question> questions) {
        List<String> ids = new ArrayList<>();
        for (Question question : questions) {
            ids.add(question.getId());
        }
        return ids;
    }

    private static List<String> promoIds(List<Promo> promos) {
        List<String> ids = new ArrayList<>();
        for (Promo promo : promos) {
            ids.add(promo.getPromoId());
        }
        return ids;
    }

    @Test
    public void exactTitle_beatsPrefixPhraseAndBodyMatches() {
        List<Question> ranked = SearchRanker.rankQuestions(Arrays.asList(
                question("body", "Một câu hỏi khác", "ngữ pháp tiếng anh"),
                question("prefix", "Ngữ pháp tiếng Anh cơ bản", "nội dung"),
                question("exact", "Ngữ pháp tiếng Anh", "nội dung")
        ), "ngữ pháp tiếng anh", null);

        assertEquals(Arrays.asList("exact", "prefix", "body"), ids(ranked));
    }

    @Test
    public void exactPhrase_beatsScatteredAllTermMatch() {
        List<Question> ranked = SearchRanker.rankQuestions(Arrays.asList(
                question("scattered", "Học nhanh và từ vựng", ""),
                question("phrase", "Cách học từ vựng", "")
        ), "học từ vựng", null);

        assertEquals(Arrays.asList("phrase", "scattered"), ids(ranked));
    }

    @Test
    public void allTermTitle_beatsSplitTitleAndBody() {
        List<Question> ranked = SearchRanker.rankQuestions(Arrays.asList(
                question("split", "Ngữ pháp", "tiếng anh"),
                question("allTitle", "Ngữ pháp tiếng Anh", "")
        ), "ngữ pháp tiếng anh", null);

        assertEquals(Arrays.asList("allTitle", "split"), ids(ranked));
    }

    @Test
    public void accentAndCaseDifferences_doNotAffectRanking() {
        List<Question> ranked = SearchRanker.rankQuestions(Collections.singletonList(
                question("accented", "NGỮ PHÁP TIẾNG ANH", "")
        ), "ngu phap tieng anh", null);

        assertEquals(Collections.singletonList("accented"), ids(ranked));
    }

    @Test
    public void partialWord_matchesStoredPrefix() {
        List<Question> ranked = SearchRanker.rankQuestions(Collections.singletonList(
                question("partial", "Ngữ pháp tiếng Anh", "")
        ), "pha", null);

        assertEquals(Collections.singletonList("partial"), ids(ranked));
    }

    @Test
    public void minorTypo_isToleratedOnBoundedCandidates() {
        List<Question> ranked = SearchRanker.rankQuestions(Collections.singletonList(
                question("typo", "Ngữ pháp tiếng Anh", "")
        ), "phapp", null);

        assertEquals(Collections.singletonList("typo"), ids(ranked));
    }

    @Test
    public void substitutionTypo_isToleratedOnBoundedCandidates() {
        List<Question> ranked = SearchRanker.rankQuestions(Collections.singletonList(
                question("substitution", "Ngữ pháp tiếng Anh", "")
        ), "phop", null);

        assertEquals(Collections.singletonList("substitution"), ids(ranked));
    }

    @Test
    public void singleTokenPartialMatch_isRejectedWhenCoverageIsInsufficient() {
        List<Question> ranked = SearchRanker.rankQuestions(Arrays.asList(
                question("onlyIelts", "IELTS", ""),
                question("allTerms", "Luyện thi IELTS", "")
        ), "luyen thi ielts", null);

        assertEquals(Collections.singletonList("allTerms"), ids(ranked));
    }

    @Test
    public void equalRelevance_isDeterministicById() {
        List<Question> ranked = SearchRanker.rankQuestions(Arrays.asList(
                question("b", "Ngữ pháp", ""),
                question("a", "Ngữ pháp", "")
        ), "ngu phap", (left, right) -> 0);

        assertEquals(Arrays.asList("a", "b"), ids(ranked));
    }

    @Test
    public void popularTieBreaker_usesScoreAfterRelevance() {
        Comparator<Question> popular = (left, right) ->
                Long.compare(right.getScore(), left.getScore());
        List<Question> ranked = SearchRanker.rankQuestions(Arrays.asList(
                question("low", "Ngữ pháp", "", 1, 1),
                question("high", "Ngữ pháp", "", 20, 10)
        ), "ngu phap", popular);

        assertEquals(Arrays.asList("high", "low"), ids(ranked));
    }

    @Test
    public void newestTieBreaker_usesCreationDateAfterRelevance() {
        Comparator<Question> newest = (left, right) ->
                right.getCreatedAt().compareTo(left.getCreatedAt());
        List<Question> ranked = SearchRanker.rankQuestions(Arrays.asList(
                question("old", "Ngữ pháp", "", 0, 30),
                question("new", "Ngữ pháp", "", 0, 1)
        ), "ngu phap", newest);

        assertEquals(Arrays.asList("new", "old"), ids(ranked));
    }

    @Test
    public void emptyQuery_returnsCandidatesInOriginalOrder() {
        List<Question> input = Arrays.asList(
                question("one", "A", ""), question("two", "B", ""));
        List<Question> ranked = SearchRanker.rankQuestions(input, "  ", null);

        assertEquals(Arrays.asList("one", "two"), ids(ranked));
    }

    @Test
    public void rankPromos_prefersTitleAndCenterMatches() {
        List<Promo> ranked = SearchRanker.rankPromos(Arrays.asList(
                promo("body", "Khác", "Khác", "luyện thi ielts"),
                promo("center", "Lớp học", "Trung tâm Ngữ pháp", ""),
                promo("title", "Ngữ pháp IELTS", "Khác", "")
        ), "ngu phap ielts");

        assertEquals("title", ranked.get(0).getPromoId());
        assertTrue(promoIds(ranked).contains("center"));
    }

    @Test
    public void rankPromos_supportsPartialAccentFreeQueries() {
        List<Promo> ranked = SearchRanker.rankPromos(Collections.singletonList(
                promo("partial", "Ngữ pháp tiếng Anh", "Sunrise", "")
        ), "phap");

        assertEquals(Collections.singletonList("partial"), promoIds(ranked));
    }

    @Test
    public void rankPromos_filtersIrrelevantPartialMatches() {
        List<Promo> ranked = SearchRanker.rankPromos(Arrays.asList(
                promo("irrelevant", "IELTS", "Khác", ""),
                promo("relevant", "Luyện thi IELTS", "Khác", "")
        ), "luyen thi ielts");

        assertEquals(Collections.singletonList("relevant"), promoIds(ranked));
    }

    @Test
    public void rankPromos_unsearchableQuery_returnsEmptyButBlankQueryReturnsAll() {
        List<Promo> promos = Arrays.asList(
                promo("a", "Ngữ pháp", "Sunrise", ""),
                promo("b", "IELTS", "Khác", ""));

        assertTrue(SearchRanker.rankPromos(promos, "a").isEmpty());
        assertTrue(SearchRanker.rankPromos(promos, "!!").isEmpty());
        assertEquals(2, SearchRanker.rankPromos(promos, "   ").size());
        assertEquals(2, SearchRanker.rankPromos(promos, "").size());
    }

    @Test
    public void rankPromos_equalScore_preservesIncomingOrder() {
        List<Promo> ranked = SearchRanker.rankPromos(Arrays.asList(
                promo("first", "Ngữ pháp", "Khác", ""),
                promo("second", "Ngữ pháp", "Khác", "")
        ), "ngu phap");

        assertEquals(Arrays.asList("first", "second"), promoIds(ranked));
    }
}
