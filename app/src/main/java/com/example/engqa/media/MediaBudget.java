package com.example.engqa.media;

/**
 * Size budget for media stored directly in Firestore.
 *
 * <p>A Firestore document is limited to 1 MiB and Base64 adds ~33%, so binary payloads must stay
 * below {@link #MAX_BINARY_BYTES}. Images are downscaled/compressed to fit; audio is capped by
 * duration and bitrate.
 */
public final class MediaBudget {

    /** Max Base64 characters we allow in one media document (safe margin under 1 MiB). */
    public static final int MAX_BASE64_CHARS = 600_000;
    /** Binary budget that maps to {@link #MAX_BASE64_CHARS}. */
    public static final int MAX_BINARY_BYTES = 450_000;

    public static final int MAX_IMAGE_DIMENSION = 1024;
    public static final int MAX_THUMB_DIMENSION = 320;
    public static final int IMAGE_QUALITY = 65;
    public static final int THUMB_QUALITY = 60;
    public static final int MAX_IMAGES = 5;

    /** AAC mono at 32 kbps for two minutes is roughly 480 KB. */
    public static final int MAX_AUDIO_BYTES = 450_000;
    public static final int MAX_AUDIO_SECONDS = 120;

    private MediaBudget() {
    }

    /** Exact Base64 length (with padding) for a binary payload. */
    public static int base64Length(int byteCount) {
        if (byteCount <= 0) {
            return 0;
        }
        return ((byteCount + 2) / 3) * 4;
    }

    public static boolean fitsDocument(int byteCount) {
        return byteCount > 0 && base64Length(byteCount) <= MAX_BASE64_CHARS;
    }

    public static boolean isAudioWithinBudget(int byteCount) {
        return byteCount > 0 && byteCount <= MAX_AUDIO_BYTES;
    }
}
