package com.example.engqa.util;

public final class ImageRefs {

    public static final String PREFIX = "fsimg:";

    private ImageRefs() {
    }

    public static String toRef(String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith(PREFIX)) {
            return id;
        }
        return PREFIX + id;
    }

    public static boolean isRef(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static String idOf(String ref) {
        if (isRef(ref)) {
            return ref.substring(PREFIX.length());
        }
        return null;
    }
}
