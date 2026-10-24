package com.example.engqa.util;

import com.example.engqa.model.FeedItem;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure functions that build the home feed (no Android imports, so they are unit-testable).
 *
 * <p>Questions and promos come from two independent streams. {@link #mix} interleaves them at a
 * fixed cadence (default: one promo after every two questions) and hides promos whose
 * {@code endAt} has passed. The "Trung tâm" filter shows promos only; a category filter shows
 * questions only; "Tất cả" mixes both.
 */
public final class FeedMixer {

    /** Synthetic filter id used by the "Trung tâm" chip. */
    public static final String FILTER_CENTER = "CENTER";
    public static final String FILTER_ALL = "ALL";
    public static final int DEFAULT_CADENCE = 2;

    private FeedMixer() {
    }

    /** Drops promos whose deadline is in the past; a {@code null} deadline is kept. */
    public static List<Promo> removeExpired(List<Promo> promos, long nowMillis) {
        List<Promo> active = new ArrayList<>();
        if (promos == null) {
            return active;
        }
        for (Promo promo : promos) {
            if (promo != null && !promo.isExpired(nowMillis)) {
                active.add(promo);
            }
        }
        return active;
    }

    /** Case- and accent-insensitive search over centre name, title and level. */
    public static List<Promo> filterByKeyword(List<Promo> promos, String keyword) {
        List<Promo> out = new ArrayList<>();
        if (promos == null) {
            return out;
        }
        String needle = TextUtils2.stripDiacritics(keyword == null ? "" : keyword)
                .toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) {
            out.addAll(promos);
            return out;
        }
        for (Promo promo : promos) {
            if (promo == null) {
                continue;
            }
            String haystack = TextUtils2.stripDiacritics(
                    safe(promo.getCenterName()) + " " + safe(promo.getTitle()) + " "
                            + safe(promo.getLevel()))
                    .toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) {
                out.add(promo);
            }
        }
        return out;
    }

    /** Interleaves the two streams; see the class comment for the filter behaviour. */
    public static List<FeedItem> mix(List<Question> questions, List<Promo> promos, String filter,
                                     int cadence) {
        return mix(questions, promos, filter, cadence, System.currentTimeMillis());
    }

    public static List<FeedItem> mix(List<Question> questions, List<Promo> promos, String filter,
                                     int cadence, long nowMillis) {
        List<FeedItem> items = new ArrayList<>();
        List<Promo> active = removeExpired(promos, nowMillis);

        if (FILTER_CENTER.equals(filter)) {
            for (Promo promo : active) {
                items.add(FeedItem.of(promo));
            }
            return items;
        }

        List<Question> questionList = questions != null ? questions : Collections.emptyList();
        boolean mixPromos = filter == null || filter.isEmpty() || FILTER_ALL.equals(filter);

        int promoIndex = 0;
        for (int i = 0; i < questionList.size(); i++) {
            items.add(FeedItem.of(questionList.get(i)));
            boolean shouldInsert = mixPromos && cadence > 0
                    && (i + 1) % cadence == 0 && promoIndex < active.size();
            if (shouldInsert) {
                items.add(FeedItem.of(active.get(promoIndex)));
                promoIndex++;
            }
        }

        // Do not silently drop approved promos when the question list ends before their slot;
        // append the remainder (this also handles an empty question list).
        if (mixPromos) {
            for (int i = promoIndex; i < active.size(); i++) {
                items.add(FeedItem.of(active.get(i)));
            }
        }
        return items;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
