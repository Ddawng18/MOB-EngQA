package com.example.engqa.media;

import android.graphics.Bitmap;
import android.util.LruCache;

/** Small in-memory cache for Base64 feed thumbnails. */
public final class ThumbnailLoader {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ThumbnailLoader() {
    }

    public static Bitmap load(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        Bitmap cached = CACHE.get(base64);
        if (cached != null) {
            return cached;
        }
        Bitmap bitmap = MediaCompressor.decodeBitmap(base64);
        if (bitmap != null) {
            CACHE.put(base64, bitmap);
        }
        return bitmap;
    }
}
