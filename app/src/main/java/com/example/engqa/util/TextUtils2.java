package com.example.engqa.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Text helpers for the Firestore keyword search.
 *
 * <p>Firestore has no full-text search, so a title and its tags are turned into a normalised,
 * accent-free, lower-case token list stored in {@code searchKeywords}. The feed then matches a
 * single typed word with {@code array-contains}.
 */
public final class TextUtils2 {

    private static final int MIN_TOKEN_LENGTH = 2;

    private TextUtils2() {
    }

    /** Removes Vietnamese diacritics so "ngữ pháp" and "ngu phap" produce the same tokens. */
    public static String stripDiacritics(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }

    public static List<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text != null) {
            String cleaned = stripDiacritics(text).toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9\\s]", " ");
            for (String token : cleaned.split("\\s+")) {
                if (token.length() >= MIN_TOKEN_LENGTH) {
                    tokens.add(token);
                }
            }
        }
        return new ArrayList<>(tokens);
    }

    public static List<String> buildSearchKeywords(String title, String body, List<String> tags) {
        Set<String> keywords = new LinkedHashSet<>(tokenize(title));
        keywords.addAll(tokenize(body));
        if (tags != null) {
            for (String tag : tags) {
                keywords.addAll(tokenize(tag));
            }
        }
        return new ArrayList<>(keywords);
    }

    /** Normalises a raw query into the single token used by {@code array-contains}. */
    public static String searchToken(String query) {
        if (query == null) {
            return "";
        }
        List<String> tokens = tokenize(query);
        return tokens.isEmpty() ? "" : tokens.get(0);
    }
}
