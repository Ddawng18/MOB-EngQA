package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.engqa.model.FeedItem;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Question;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Unit tests for the feed interleaving, expiry filtering and promo search. */
public class FeedMixerTest {

    private static final long NOW = 1_700_000_000_000L;

    private static Question question(String id) {
        Question question = new Question();
        question.setId(id);
        question.setTitle("Question " + id);
        return question;
    }

    private static Promo promo(String id, Date endAt) {
        Promo promo = new Promo();
        promo.setPromoId(id);
        promo.setTitle("Promo " + id);
        promo.setCenterName("Sunrise English Center");
        promo.setLevel("A2 - B1");
        promo.setEndAt(endAt);
        return promo;
    }

    private static List<Question> questions(int count) {
        List<Question> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(question("q" + i));
        }
        return list;
    }

    private static List<String> keys(List<FeedItem> items) {
        List<String> keys = new ArrayList<>();
        for (FeedItem item : items) {
            keys.add(item.key());
        }
        return keys;
    }

    @Test
    public void mix_interleavesOnePromoAfterEveryTwoQuestions() {
        List<FeedItem> items = FeedMixer.mix(
                questions(4),
                Arrays.asList(promo("p1", null), promo("p2", null)),
                FeedMixer.FILTER_ALL, 2, NOW);

        assertEquals(6, items.size());
        assertEquals(Arrays.asList("question:q1", "question:q2", "promo:p1",
                "question:q3", "question:q4", "promo:p2"), keys(items));
        assertEquals(FeedItem.TYPE_QUESTION, items.get(0).getType());
        assertEquals(FeedItem.TYPE_PROMO, items.get(2).getType());
        assertTrue(items.get(2).isPromo());
    }

    @Test
    public void mix_withoutPromos_returnsQuestionsOnly() {
        List<FeedItem> items = FeedMixer.mix(questions(3), Collections.emptyList(),
                FeedMixer.FILTER_ALL, 2, NOW);
        assertEquals(3, items.size());
        for (FeedItem item : items) {
            assertEquals(FeedItem.TYPE_QUESTION, item.getType());
        }
    }

    @Test
    public void centerFilter_returnsOnlyPromos() {
        List<FeedItem> items = FeedMixer.mix(
                questions(4),
                Arrays.asList(promo("p1", null), promo("p2", null)),
                FeedMixer.FILTER_CENTER, 2, NOW);

        assertEquals(2, items.size());
        for (FeedItem item : items) {
            assertEquals(FeedItem.TYPE_PROMO, item.getType());
        }
    }

    @Test
    public void expiredPromos_areRemoved_andNullDeadlineIsKept() {
        Promo expired = promo("expired", new Date(NOW - 1000));
        Promo noDeadline = promo("noDeadline", null);

        List<FeedItem> all = FeedMixer.mix(questions(2), Arrays.asList(expired, noDeadline),
                FeedMixer.FILTER_ALL, 2, NOW);
        assertEquals(Arrays.asList("question:q1", "question:q2", "promo:noDeadline"),
                keys(all));

        List<FeedItem> center = FeedMixer.mix(Collections.emptyList(),
                Arrays.asList(expired, noDeadline), FeedMixer.FILTER_CENTER, 2, NOW);
        assertEquals(Collections.singletonList("promo:noDeadline"), keys(center));
    }

    @Test
    public void questionsAndPromosEmpty_returnsEmpty() {
        List<FeedItem> items = FeedMixer.mix(Collections.emptyList(), Collections.emptyList(),
                FeedMixer.FILTER_ALL, 2, NOW);
        assertTrue(items.isEmpty());
    }

    @Test
    public void filterByKeyword_matchesCenterTitleAndLevel_withoutDiacritics() {
        Promo byCenter = promo("a", null);
        byCenter.setCenterName("Trung tâm Sunrise");

        Promo byTitle = promo("b", null);
        byTitle.setCenterName("Khác");
        byTitle.setTitle("Luyện thi IELTS cấp tốc");

        Promo byLevel = promo("c", null);
        byLevel.setCenterName("Khác");
        byLevel.setTitle("Lớp giao tiếp");
        byLevel.setLevel("B2");

        List<Promo> all = Arrays.asList(byCenter, byTitle, byLevel);

        assertEquals(1, FeedMixer.filterByKeyword(all, "sunrise").size());
        assertEquals("b", FeedMixer.filterByKeyword(all, "ielts").get(0).getPromoId());
        assertEquals("c", FeedMixer.filterByKeyword(all, "b2").get(0).getPromoId());
        assertEquals("a", FeedMixer.filterByKeyword(all, "trung tam").get(0).getPromoId());
        assertTrue(FeedMixer.filterByKeyword(all, "khong ton tai").isEmpty());
        assertEquals(3, FeedMixer.filterByKeyword(all, "").size());
    }

    @Test
    public void filterByKeyword_unsearchableQuery_returnsEmpty() {
        List<Promo> all = Arrays.asList(promo("a", null), promo("b", null));
        assertTrue(FeedMixer.filterByKeyword(all, "a").isEmpty());
        assertTrue(FeedMixer.filterByKeyword(all, "!!").isEmpty());
        assertEquals(2, FeedMixer.filterByKeyword(all, "   ").size());
    }

    @Test
    public void mix_appendsPromosLeftAfterTheLastQuestion() {
        List<FeedItem> items = FeedMixer.mix(
                questions(3),
                Arrays.asList(promo("p1", null), promo("p2", null), promo("p3", null)),
                FeedMixer.FILTER_ALL, 2, NOW);

        assertEquals(Arrays.asList("question:q1", "question:q2", "promo:p1",
                "question:q3", "promo:p2", "promo:p3"), keys(items));
    }

    @Test
    public void onlyPromosWithQuestionsEmpty_areShown() {
        List<FeedItem> items = FeedMixer.mix(Collections.emptyList(),
                Arrays.asList(promo("p1", null), promo("p2", null)),
                FeedMixer.FILTER_ALL, 2, NOW);
        assertEquals(2, items.size());
        assertFalse(items.get(0).isPromo() == false);
    }
}
