package com.example.engqa.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Text helpers shared by keyword generation and query analysis.
 *
 * <p>Firestore has no full-text search, so questions store a normalised token list in
 * {@code searchKeywords}. Full words are stored for title, body and tags; title/tag/body tokens
 * also contribute short prefixes so a partial word such as "pha" can find "pháp". The same
 * normalisation is used when a query is analysed, and the seed/reindex tooling mirrors it.
 */
public final class TextUtils2 {

    public static final int MIN_TOKEN_LENGTH = 2;
    /** Keeps a document's keyword array comfortably below Firestore's document-size limit. */
    public static final int MAX_SEARCH_KEYWORDS = 900;
    /** Firestore's {@code array-contains-any} accepts at most ten values. */
    public static final int MAX_RETRIEVAL_TOKENS = 10;
    /** Query tokens used for scoring; more than this is unlikely to be intentional. */
    public static final int MAX_QUERY_TOKENS = 16;

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

    /**
     * Canonical search text: accent-free, lower-case, punctuation collapsed to single spaces.
     * Original content is never stored this way; it is only used for matching and ranking.
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return stripDiacritics(input).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    /** Normalised, de-duplicated tokens of at least {@link #MIN_TOKEN_LENGTH} characters. */
    public static List<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        String normalized = normalize(text);
        if (!normalized.isEmpty()) {
            for (String token : normalized.split(" ")) {
                if (token.length() >= MIN_TOKEN_LENGTH) {
                    tokens.add(token);
                }
            }
        }
        return new ArrayList<>(tokens);
    }

    /**
     * Full tokens for title/body/tags plus bounded prefixes for all fields. Prefixes make partial
     * words searchable without a separate full-text service.
     */
    public static List<String> buildSearchKeywords(String title, String body, List<String> tags) {
        Set<String> keywords = new LinkedHashSet<>();
        List<String> titleTokens = tokenize(title);
        List<String> bodyTokens = tokenize(body);
        List<String> tagTokens = new ArrayList<>();
        if (tags != null) {
            for (String tag : tags) {
                tagTokens.addAll(tokenize(tag));
            }
        }

        addFullTokens(keywords, titleTokens);
        addFullTokens(keywords, bodyTokens);
        addFullTokens(keywords, tagTokens);
        addPrefixes(keywords, titleTokens);
        addPrefixes(keywords, tagTokens);
        addPrefixes(keywords, bodyTokens);

        List<String> values = new ArrayList<>(keywords);
        if (values.size() <= MAX_SEARCH_KEYWORDS) {
            return values;
        }
        return new ArrayList<>(values.subList(0, MAX_SEARCH_KEYWORDS));
    }

    /** Tokens used for scoring. Very long pastes keep their first {@link #MAX_QUERY_TOKENS}. */
    public static List<String> queryTokens(String query) {
        List<String> tokens = tokenize(query);
        if (tokens.size() <= MAX_QUERY_TOKENS) {
            return tokens;
        }
        return new ArrayList<>(tokens.subList(0, MAX_QUERY_TOKENS));
    }

    /**
     * Values for the Firestore {@code array-contains-any} candidate query: the most selective
     * query tokens first, then a few single-edit variants to tolerate minor typos. Always bounded
     * by {@link #MAX_RETRIEVAL_TOKENS}.
     */
    public static List<String> retrievalTokens(String query) {
        List<String> tokens = queryTokens(query);
        if (tokens.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> selective = new ArrayList<>(tokens);
        final List<String> order = new ArrayList<>(tokens);
        selective.sort((left, right) -> {
            int byLength = Integer.compare(right.length(), left.length());
            return byLength != 0 ? byLength : Integer.compare(order.indexOf(left),
                    order.indexOf(right));
        });

        Set<String> values = new LinkedHashSet<>();
        for (String token : selective) {
            if (values.size() >= MAX_RETRIEVAL_TOKENS) {
                break;
            }
            values.add(token);
        }
        // A short prefix of the most selective token catches substitution typos such as
        // "phop" -> "phap" as long as the first characters are intact.
        int prefixSlots = 2;
        for (String token : selective) {
            int longest = Math.min(3, token.length() - 1);
            for (int length = MIN_TOKEN_LENGTH; length <= longest && prefixSlots > 0
                    && values.size() < MAX_RETRIEVAL_TOKENS - 2; length++) {
                if (values.add(token.substring(0, length))) {
                    prefixSlots--;
                }
            }
        }
        for (String token : selective) {
            int remaining = MAX_RETRIEVAL_TOKENS - values.size();
            if (remaining <= 0) {
                break;
            }
            for (String variant : typoVariants(token, remaining)) {
                if (values.size() >= MAX_RETRIEVAL_TOKENS) {
                    break;
                }
                values.add(variant);
            }
        }
        return new ArrayList<>(values);
    }

    /** True when the query contains at least one token the index can match. */
    public static boolean isSearchable(String query) {
        return !tokenize(query).isEmpty();
    }

    /** Accent-free query phrase used for exact-phrase ranking. */
    public static String phrase(String query) {
        return String.join(" ", tokenize(query));
    }

    /**
     * A few likely corrections for an over-typed word: one deletion and adjacent transposition.
     * This is deliberately small because each variant costs a slot in the bounded candidate query.
     */
    public static List<String> typoVariants(String token, int max) {
        List<String> variants = new ArrayList<>();
        if (token == null || token.length() < 3 || max <= 0) {
            return variants;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < token.length() && variants.size() < max; i++) {
            String candidate = token.substring(0, i) + token.substring(i + 1);
            addVariant(variants, seen, candidate, token);
        }
        for (int i = 0; i < token.length() - 1 && variants.size() < max; i++) {
            char[] chars = token.toCharArray();
            char tmp = chars[i];
            chars[i] = chars[i + 1];
            chars[i + 1] = tmp;
            addVariant(variants, seen, new String(chars), token);
        }
        return variants;
    }

    private static void addVariant(List<String> variants, Set<String> seen, String candidate,
                                   String original) {
        if (candidate.length() >= MIN_TOKEN_LENGTH
                && !candidate.equals(original) && seen.add(candidate)) {
            variants.add(candidate);
        }
    }

    /** Levenshtein distance, used for bounded typo ranking on the candidate set. */
    public static int editDistance(String left, String right) {
        if (left == null || right == null) {
            return Integer.MAX_VALUE;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    /** Max edits tolerated for a token of this length; short tokens stay exact. */
    public static int maxEditDistance(int length) {
        if (length >= 7) {
            return 2;
        }
        if (length >= 4) {
            return 1;
        }
        return 0;
    }

    /** Legacy single-token helper retained for callers/tests that only need the first token. */
    public static String searchToken(String query) {
        List<String> tokens = tokenize(query);
        return tokens.isEmpty() ? "" : tokens.get(0);
    }

    private static void addFullTokens(Set<String> keywords, List<String> tokens) {
        for (String token : tokens) {
            if (keywords.size() >= MAX_SEARCH_KEYWORDS) {
                return;
            }
            keywords.add(token);
        }
    }

    private static void addPrefixes(Set<String> keywords, List<String> tokens) {
        for (String token : tokens) {
            for (int length = MIN_TOKEN_LENGTH; length < token.length(); length++) {
                if (keywords.size() >= MAX_SEARCH_KEYWORDS) {
                    return;
                }
                keywords.add(token.substring(0, length));
            }
        }
    }
}
