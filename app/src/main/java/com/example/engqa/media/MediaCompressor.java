package com.example.engqa.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Image/audio compression and Base64 helpers for Firestore media documents. */
public final class MediaCompressor {

    private MediaCompressor() {
    }

    /** Compresses an image to JPEG at {@code maxDimension} on its longest side. */
    public static byte[] compressImage(Context context, Uri uri, int maxDimension, int quality)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(context, uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Ảnh không hợp lệ");
        }

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= maxDimension
                && bounds.outHeight / (sample * 2) >= maxDimension) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = open(context, uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Ảnh không hợp lệ");
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min(1f, (float) maxDimension / Math.max(width, height));
        if (scale < 1f) {
            Bitmap scaled = Bitmap.createScaledBitmap(
                    bitmap, Math.round(width * scale), Math.round(height * scale), true);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            bitmap = scaled;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    /** Tries progressively smaller/softer JPEGs until the payload fits one Firestore document. */
    public static byte[] compressImageToBudget(Context context, Uri uri) throws IOException {
        int[][] attempts = {
                {MediaBudget.MAX_IMAGE_DIMENSION, MediaBudget.IMAGE_QUALITY},
                {MediaBudget.MAX_IMAGE_DIMENSION, 55},
                {800, 60},
                {800, 50},
                {640, 55}
        };
        for (int[] attempt : attempts) {
            byte[] bytes = compressImage(context, uri, attempt[0], attempt[1]);
            if (MediaBudget.fitsDocument(bytes.length)) {
                return bytes;
            }
        }
        throw new IOException("Ảnh quá lớn sau khi nén");
    }

    /** Small Base64 thumbnail stored in the question document for the feed. */
    public static String thumbnailBase64(Context context, Uri uri) throws IOException {
        byte[] bytes = compressImage(
                context, uri, MediaBudget.MAX_THUMB_DIMENSION, MediaBudget.THUMB_QUALITY);
        return toBase64(bytes);
    }

    /** Reads a file/content URI, failing when it exceeds {@code maxBytes}. */
    public static byte[] readBytes(Context context, Uri uri, int maxBytes) throws IOException {
        try (InputStream in = open(context, uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Tệp vượt quá giới hạn Firestore");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    public static String toBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    public static byte[] fromBase64(String value) {
        return Base64.decode(value, Base64.DEFAULT);
    }

    public static Bitmap decodeBitmap(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = fromBase64(base64);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    /** Decodes Base64 audio to a cache file so MediaPlayer can play it. */
    public static File writeToCache(Context context, String base64, String name) throws IOException {
        File dir = new File(context.getCacheDir(), "media");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Không tạo được thư mục cache");
        }
        File file = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(fromBase64(base64));
        }
        return file;
    }

    private static InputStream open(Context context, Uri uri) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("Không đọc được tệp đã chọn");
        }
        return in;
    }
}
