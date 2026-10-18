package com.example.engqa.util;

import java.util.Locale;

/** Pure validation rules for promo posts (JVM-testable, no Android imports). */
public final class PromoValidator {

    public static final int TITLE_MIN = 5;
    public static final int TITLE_MAX = 120;
    public static final int BODY_MAX = 5000;
    public static final int PHONE_MIN_DIGITS = 9;
    public static final int PHONE_MAX_DIGITS = 11;

    public static final String ERROR_TITLE = "Tiêu đề phải từ 5 đến 120 ký tự";
    public static final String ERROR_BODY_EMPTY = "Vui lòng nhập nội dung";
    public static final String ERROR_BODY_LONG = "Nội dung tối đa 5000 ký tự";
    public static final String ERROR_PHONE = "Số điện thoại phải có 9 đến 11 chữ số";
    public static final String ERROR_URL = "Liên kết phải bắt đầu bằng http:// hoặc https://";
    public static final String ERROR_IMAGE_URL =
            "Ảnh phải là URL bắt đầu bằng http:// hoặc https://";

    private PromoValidator() {
    }

    public static String titleError(String title) {
        String value = title == null ? "" : title.trim();
        if (value.length() < TITLE_MIN || value.length() > TITLE_MAX) {
            return ERROR_TITLE;
        }
        return null;
    }

    public static String bodyError(String body) {
        String value = body == null ? "" : body.trim();
        if (value.isEmpty()) {
            return ERROR_BODY_EMPTY;
        }
        if (value.length() > BODY_MAX) {
            return ERROR_BODY_LONG;
        }
        return null;
    }

    public static boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() >= PHONE_MIN_DIGITS && digits.length() <= PHONE_MAX_DIGITS;
    }

    public static String phoneError(String phone) {
        return isValidPhone(phone) ? null : ERROR_PHONE;
    }

    public static boolean isHttpUrl(String url) {
        if (url == null) {
            return false;
        }
        String value = url.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://");
    }

    public static String urlError(String url) {
        return isHttpUrl(url) ? null : ERROR_URL;
    }

    /** The banner is optional, but when present it must be an http(s) URL. */
    public static String imageUrlError(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        return isHttpUrl(url) ? null : ERROR_IMAGE_URL;
    }
}
