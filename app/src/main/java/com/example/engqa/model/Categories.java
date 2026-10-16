package com.example.engqa.model;

/** The nine seeded question topics and the slug/name mapping used across the app and seed data. */
public final class Categories {

    public static final String[] SLUGS = {
            "grammar", "vocabulary", "listening", "reading",
            "writing", "speaking", "pronunciation", "ielts", "toeic"
    };

    public static final String[] NAMES = {
            "Ngữ pháp", "Từ vựng", "Nghe", "Đọc", "Viết", "Nói", "Phát âm", "IELTS", "TOEIC"
    };

    private Categories() {
    }

    /** Maps a display name ("Từ vựng") to its slug ("vocabulary"). */
    public static String slugForName(String name) {
        if (name != null) {
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equals(name)) {
                    return SLUGS[i];
                }
            }
        }
        return SLUGS[0];
    }

    public static String nameForSlug(String slug) {
        if (slug != null) {
            for (int i = 0; i < SLUGS.length; i++) {
                if (SLUGS[i].equals(slug)) {
                    return NAMES[i];
                }
            }
        }
        return NAMES[0];
    }
}
