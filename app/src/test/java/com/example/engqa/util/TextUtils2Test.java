package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** Unit tests for the search-keyword normalisation used by {@code array-contains} queries. */
public class TextUtils2Test {

    @Test
    public void stripDiacritics_removesVietnameseMarks() {
        assertEquals("ngu phap", TextUtils2.stripDiacritics("ngữ pháp"));
        assertEquals("Dang ky", TextUtils2.stripDiacritics("Đăng ký"));
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
    public void searchToken_returnsFirstNormalisedToken() {
        assertEquals("ngu", TextUtils2.searchToken("Ngữ pháp"));
        assertEquals("", TextUtils2.searchToken("   "));
        assertEquals("", TextUtils2.searchToken(null));
    }

    @Test
    public void buildSearchKeywords_mergesTitleBodyAndTags() {
        List<String> keywords = TextUtils2.buildSearchKeywords(
                "Have been", "Phân biệt", java.util.Collections.singletonList("Ngữ pháp"));
        assertTrue(keywords.contains("have"));
        assertTrue(keywords.contains("been"));
        assertTrue(keywords.contains("biet"));
        assertTrue(keywords.contains("phap"));
    }

    @Test
    public void passwordValidation_rejectsShortPasswords() {
        assertNull(Validators.passwordError("123456"));
        assertEquals("Mật khẩu tối thiểu 6 ký tự", Validators.passwordError("123"));
        assertEquals("Vui lòng nhập mật khẩu", Validators.passwordError(""));
    }
}
