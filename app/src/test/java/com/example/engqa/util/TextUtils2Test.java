package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Unit tests for the search-keyword normalisation used by the Firestore candidate query. */
public class TextUtils2Test {

    @Test
    public void stripDiacritics_removesVietnameseMarks() {
        assertEquals("ngu phap", TextUtils2.stripDiacritics("ngữ pháp"));
        assertEquals("Dang ky", TextUtils2.stripDiacritics("Đăng ký"));
    }

    @Test
    public void normalize_handlesCaseWhitespacePunctuationAndDiacritics() {
        assertEquals("ngu phap tieng anh",
                TextUtils2.normalize("  NGỮ   PHÁP, tiếng-anh!! "));
        assertEquals("hoc tu vung", TextUtils2.normalize("  HOC   TU VUNG "));
        assertEquals("", TextUtils2.normalize(null));
        assertEquals("", TextUtils2.normalize("!!!"));
    }

    @Test
    public void tokenize_isLowercaseAccentFreeAndDeduped() {
        List<String> tokens = TextUtils2.tokenize("Ngữ pháp, ngữ pháp!");
        assertTrue(tokens.contains("ngu"));
        assertTrue(tokens.contains("phap"));
        assertEquals(2, tokens.size());
    }

    @Test
    public void tokenize_dropsShortTokens() {
        List<String> tokens = TextUtils2.tokenize("a to be");
        assertTrue(tokens.contains("to"));
        assertTrue(tokens.contains("be"));
        assertFalse(tokens.contains("a"));
    }

    @Test
    public void queryTokens_preservesOrderAndCapsLongInput() {
        assertEquals(Arrays.asList("ngu", "phap", "tieng", "anh"),
                TextUtils2.queryTokens("Ngữ pháp tiếng Anh"));
        StringBuilder longQuery = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longQuery.append("token").append(i).append(' ');
        }
        assertEquals(TextUtils2.MAX_QUERY_TOKENS,
                TextUtils2.queryTokens(longQuery.toString()).size());
    }

    @Test
    public void isSearchable_rejectsEmptyShortAndPunctuationOnlyQueries() {
        assertFalse(TextUtils2.isSearchable(""));
        assertFalse(TextUtils2.isSearchable("   "));
        assertFalse(TextUtils2.isSearchable("a"));
        assertFalse(TextUtils2.isSearchable("!!!"));
        assertTrue(TextUtils2.isSearchable("ab"));
        assertTrue(TextUtils2.isSearchable("  HỌC  "));
    }

    @Test
    public void retrievalTokens_keepQueryTokensAndAddTypoVariant() {
        List<String> tokens = TextUtils2.retrievalTokens("phapp");
        assertTrue(tokens.contains("phapp"));
        assertTrue(tokens.contains("phap"));
        assertTrue(tokens.size() <= TextUtils2.MAX_RETRIEVAL_TOKENS);
    }

    @Test
    public void retrievalTokens_includeShortPrefixForSubstitutionTypos() {
        List<String> tokens = TextUtils2.retrievalTokens("phop");
        assertTrue(tokens.contains("ph"));
    }

    @Test
    public void retrievalTokens_areBoundedForLongQueries() {
        List<String> tokens = TextUtils2.retrievalTokens(
                "ngu phap tieng anh hoc tu vung luyen thi ielts toeic listening speaking");
        assertTrue(tokens.size() <= TextUtils2.MAX_RETRIEVAL_TOKENS);
        assertTrue(tokens.contains("listening"));
        assertTrue(tokens.contains("speaking"));
    }

    @Test
    public void typoVariants_coverDeletionAndTransposition() {
        List<String> variants = TextUtils2.typoVariants("phapp", 9);
        assertTrue(variants.contains("phap"));
        assertTrue(variants.size() <= 9);
        assertTrue(TextUtils2.typoVariants("hoc", 9).contains("hco"));
        assertTrue(TextUtils2.typoVariants("ab", 9).isEmpty());
    }

    @Test
    public void editDistance_isCorrectAndBoundedByLength() {
        assertEquals(0, TextUtils2.editDistance("phap", "phap"));
        assertEquals(1, TextUtils2.editDistance("phapp", "phap"));
        assertEquals(1, TextUtils2.editDistance("phap", "php"));
        assertEquals(3, TextUtils2.editDistance("phap", "p"));
        assertEquals(0, TextUtils2.maxEditDistance(3));
        assertEquals(1, TextUtils2.maxEditDistance(4));
        assertEquals(1, TextUtils2.maxEditDistance(6));
        assertEquals(2, TextUtils2.maxEditDistance(7));
    }

    @Test
    public void searchToken_returnsFirstNormalisedToken() {
        assertEquals("ngu", TextUtils2.searchToken("Ngữ pháp"));
        assertEquals("", TextUtils2.searchToken("   "));
        assertEquals("", TextUtils2.searchToken(null));
    }

    @Test
    public void buildSearchKeywords_mergesFullTokensAndPrefixes() {
        List<String> keywords = TextUtils2.buildSearchKeywords(
                "Have been", "Phân biệt", Collections.singletonList("Ngữ pháp"));
        assertTrue(keywords.contains("have"));
        assertTrue(keywords.contains("been"));
        assertTrue(keywords.contains("biet"));
        assertTrue(keywords.contains("phap"));
        // Prefixes make "pha"/"ph" useful for a partial-word query.
        assertTrue(keywords.contains("pha"));
        assertTrue(keywords.contains("ph"));
        assertTrue(keywords.contains("ngu"));
        assertFalse(keywords.contains("h"));
    }

    @Test
    public void buildSearchKeywords_isBoundedForLargeDocuments() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            body.append("vocabulary").append(i).append(' ');
        }
        List<String> keywords = TextUtils2.buildSearchKeywords("Big title", body.toString(),
                Arrays.asList("grammar", "ielts"));
        assertTrue(keywords.size() <= TextUtils2.MAX_SEARCH_KEYWORDS);
    }

    @Test
    public void passwordValidation_rejectsShortPasswords() {
        assertNull(Validators.passwordError("123456"));
        assertEquals("Mật khẩu tối thiểu 6 ký tự", Validators.passwordError("123"));
        assertEquals("Vui lòng nhập mật khẩu", Validators.passwordError(""));
    }
}
