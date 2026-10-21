package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for form and attachment validation. */
public class ValidatorsTest {

    @Test
    public void isValidEmail_acceptsValidAddresses() {
        assertTrue(Validators.isValidEmail("user@example.com"));
        assertTrue(Validators.isValidEmail("a.b+tag@mail.co"));
    }

    @Test
    public void isValidEmail_rejectsInvalidAddresses() {
        assertFalse(Validators.isValidEmail(""));
        assertFalse(Validators.isValidEmail("no-at-sign"));
        assertFalse(Validators.isValidEmail("missing@domain"));
    }

    @Test
    public void isValidTitle_requiresAtLeastTenCharacters() {
        assertTrue(Validators.isValidTitle("Khi nào dùng have been"));
        assertFalse(Validators.isValidTitle("Ngắn"));
        assertFalse(Validators.isValidTitle("   "));
    }

    @Test
    public void imageAndAudioLimits_matchStorageRules() {
        assertTrue(Validators.isImageSizeAllowed(5L * 1024 * 1024));
        assertFalse(Validators.isImageSizeAllowed(5L * 1024 * 1024 + 1));
        assertTrue(Validators.isAudioSizeAllowed(10L * 1024 * 1024));
        assertFalse(Validators.isAudioSizeAllowed(0));
    }

    @Test
    public void mediaTypes_areRestricted() {
        assertTrue(Validators.isAllowedImageType("image/png"));
        assertTrue(Validators.isAllowedImageType("image/jpeg"));
        assertFalse(Validators.isAllowedImageType("image/gif"));
        assertTrue(Validators.isAllowedAudioType("audio/mpeg"));
        assertFalse(Validators.isAllowedAudioType("video/mp4"));
    }

    // ---------------------------------------------------------------- auth helpers

    @Test
    public void usernameError_acceptsValidDisplayNames() {
        assertNull(Validators.usernameError("minh_anh"));
        assertNull(Validators.usernameError("Lan.Teacher"));
        assertNull(Validators.usernameError("user-2026"));
        assertTrue(Validators.isValidUsername("abc"));
    }

    @Test
    public void usernameError_rejectsInvalidDisplayNames() {
        assertEquals("Vui lòng nhập tên hiển thị", Validators.usernameError(""));
        assertEquals("Vui lòng nhập tên hiển thị", Validators.usernameError("   "));
        assertEquals("Tên hiển thị phải từ 3 đến 24 ký tự", Validators.usernameError("ab"));
        assertTrue(Validators.usernameError("a".repeat(25)).contains("3 đến 24"));
        assertTrue(Validators.usernameError("tên có dấu").contains("chữ, số"));
        assertTrue(Validators.usernameError("bad name").contains("chữ, số"));
        assertTrue(Validators.usernameError("user#name").contains("chữ, số"));
        assertTrue(Validators.usernameError("user@domain").contains("chữ, số"));
    }

    @Test
    public void confirmPasswordError_matchesPasswords() {
        assertNull(Validators.confirmPasswordError("secret123", "secret123"));
        assertEquals("Vui lòng xác nhận mật khẩu",
                Validators.confirmPasswordError("secret123", ""));
        assertEquals("Mật khẩu xác nhận không khớp",
                Validators.confirmPasswordError("secret123", "secret124"));
    }

    @Test
    public void passwordStrength_scoresFromZeroToThree() {
        assertEquals(0, Validators.passwordStrength(""));
        assertEquals(1, Validators.passwordStrength("abcdef"));
        assertTrue(Validators.passwordStrength("abcdef12") >= 2);
        assertEquals(3, Validators.passwordStrength("Abcdef12!xyz"));
    }
}
