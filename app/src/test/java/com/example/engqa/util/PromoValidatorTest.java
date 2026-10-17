package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the promo form rules. */
public class PromoValidatorTest {

    @Test
    public void title_isBetweenFiveAndOneHundredTwentyCharacters() {
        assertEquals(PromoValidator.ERROR_TITLE, PromoValidator.titleError("abcd"));
        assertNull(PromoValidator.titleError("abcde"));
        assertNull(PromoValidator.titleError(repeat('a', 120)));
        assertEquals(PromoValidator.ERROR_TITLE, PromoValidator.titleError(repeat('a', 121)));
        assertEquals(PromoValidator.ERROR_TITLE, PromoValidator.titleError(null));
    }

    @Test
    public void body_isRequiredAndCappedAtFiveThousandCharacters() {
        assertEquals(PromoValidator.ERROR_BODY_EMPTY, PromoValidator.bodyError(""));
        assertEquals(PromoValidator.ERROR_BODY_EMPTY, PromoValidator.bodyError(null));
        assertNull(PromoValidator.bodyError("Noi dung"));
        assertNull(PromoValidator.bodyError(repeat('a', 5000)));
        assertEquals(PromoValidator.ERROR_BODY_LONG,
                PromoValidator.bodyError(repeat('a', 5001)));
    }

    @Test
    public void phone_requiresNineToElevenDigits() {
        assertFalse(PromoValidator.isValidPhone("12345678"));
        assertTrue(PromoValidator.isValidPhone("0901234567"));
        assertTrue(PromoValidator.isValidPhone("090 123 4567"));
        assertTrue(PromoValidator.isValidPhone("09012345678"));
        assertFalse(PromoValidator.isValidPhone("123456789012"));
        assertFalse(PromoValidator.isValidPhone(null));
        assertEquals(PromoValidator.ERROR_PHONE, PromoValidator.phoneError("123"));
        assertNull(PromoValidator.phoneError("0901234567"));
    }

    @Test
    public void link_mustUseHttpOrHttps() {
        assertFalse(PromoValidator.isHttpUrl("example.com"));
        assertFalse(PromoValidator.isHttpUrl(null));
        assertTrue(PromoValidator.isHttpUrl("http://example.com"));
        assertTrue(PromoValidator.isHttpUrl("https://example.com"));
        assertEquals(PromoValidator.ERROR_URL, PromoValidator.urlError("ftp://example.com"));
        assertNull(PromoValidator.urlError("https://example.com"));
    }

    @Test
    public void banner_isOptionalButMustBeAUrl() {
        assertNull(PromoValidator.imageUrlError(""));
        assertNull(PromoValidator.imageUrlError(null));
        assertNull(PromoValidator.imageUrlError("https://cdn.example.com/banner.jpg"));
        assertEquals(PromoValidator.ERROR_IMAGE_URL,
                PromoValidator.imageUrlError("banner.jpg"));
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
