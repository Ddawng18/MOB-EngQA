package com.example.engqa.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Field and attachment validation shared by the auth and question forms.
 *
 * <p>This class is deliberately free of Android framework dependencies so it can be covered by
 * plain JVM unit tests.
 */
public final class Validators {

    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MAX_USERNAME_LENGTH = 24;
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    public static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private Validators() {
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isValidEmail(String email) {
        return !isBlank(email) && EMAIL.matcher(email.trim()).matches();
    }

    /** Returns an error message, or {@code null} when the password is acceptable. */
    public static String passwordError(String password) {
        if (isBlank(password)) {
            return "Vui lòng nhập mật khẩu";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Mật khẩu tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự";
        }
        return null;
    }

    public static boolean isValidTitle(String title) {
        return !isBlank(title) && title.trim().length() >= 10;
    }

    /** Returns an error message for a display name, or {@code null} when it is acceptable. */
    public static String usernameError(String username) {
        if (isBlank(username)) {
            return "Vui lòng nhập tên hiển thị";
        }
        String value = username.trim();
        if (value.length() < MIN_USERNAME_LENGTH || value.length() > MAX_USERNAME_LENGTH) {
            return "Tên hiển thị phải từ " + MIN_USERNAME_LENGTH + " đến "
                    + MAX_USERNAME_LENGTH + " ký tự";
        }
        if (!USERNAME.matcher(value).matches()) {
            return "Tên hiển thị chỉ được dùng chữ, số và . _ -";
        }
        return null;
    }

    public static boolean isValidUsername(String username) {
        return usernameError(username) == null;
    }

    /** Returns an error message when the confirmation does not match, or {@code null}. */
    public static String confirmPasswordError(String password, String confirmPassword) {
        if (isBlank(confirmPassword)) {
            return "Vui lòng xác nhận mật khẩu";
        }
        if (!confirmPassword.equals(password)) {
            return "Mật khẩu xác nhận không khớp";
        }
        return null;
    }

    /** Rough 0..3 strength score used to show a hint under the password field. */
    public static int passwordStrength(String password) {
        if (isBlank(password)) {
            return 0;
        }
        int score = 0;
        if (password.length() >= MIN_PASSWORD_LENGTH) {
            score++;
        }
        if (password.length() >= 10) {
            score++;
        }
        boolean hasLetter = password.matches(".*[A-Za-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSymbol = password.matches(".*[^A-Za-z0-9].*");
        if (hasLetter && hasDigit) {
            score++;
        }
        if (hasSymbol) {
            score++;
        }
        return Math.min(score, 3);
    }

    public static boolean isImageSizeAllowed(long bytes) {
        return bytes > 0 && bytes <= MAX_IMAGE_BYTES;
    }

    public static boolean isAudioSizeAllowed(long bytes) {
        return bytes > 0 && bytes <= MAX_AUDIO_BYTES;
    }

    public static boolean isAllowedImageType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String type = mimeType.toLowerCase(Locale.ROOT);
        return type.equals("image/jpeg") || type.equals("image/jpg") || type.equals("image/png");
    }

    public static boolean isAllowedAudioType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String type = mimeType.toLowerCase(Locale.ROOT);
        return type.equals("audio/mpeg") || type.equals("audio/mp3")
                || type.equals("audio/mp4") || type.equals("audio/m4a")
                || type.equals("audio/x-m4a") || type.equals("audio/aac")
                || type.equals("audio/wav") || type.equals("audio/x-wav")
                || type.equals("audio/3gpp");
    }
}
