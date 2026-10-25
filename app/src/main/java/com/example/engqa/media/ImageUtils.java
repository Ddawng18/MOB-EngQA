package com.example.engqa.media;

import java.util.Locale;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import java.io.File;

/** Helpers for picking/capturing images: temp capture URIs and file metadata lookups. */
public final class ImageUtils {

    private ImageUtils() {
    }

    /** A {@code FileProvider} URI in the app cache that the camera app can write into. */
    public static Uri createImageCaptureUri(Context context) {
        File dir = new File(context.getCacheDir(), "images");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        File file = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
    }

    public static String displayName(Context context, Uri uri) {
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // fall through to the URI-based name
        }
        if (name == null) {
            name = "file_" + System.currentTimeMillis();
        }
        return name;
    }

    public static long sizeBytes(Context context, Uri uri) {
        long size = -1;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) {
                    size = cursor.getLong(index);
                }
            }
        } catch (Exception ignored) {
            // unknown size
        }
        if (size < 0) {
            try {
                android.content.res.AssetFileDescriptor descriptor =
                        context.getContentResolver().openAssetFileDescriptor(uri, "r");
                if (descriptor != null) {
                    size = descriptor.getLength();
                    descriptor.close();
                }
            } catch (Exception ignored) {
                // unknown size
            }
        }
        return size;
    }

    public static String mimeType(Context context, Uri uri) {
        String type = context.getContentResolver().getType(uri);
        if (type != null) {
            return type;
        }
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (extension != null) {
            String fromExtension = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
            if (fromExtension != null) {
                return fromExtension;
            }
        }
        return "application/octet-stream";
    }
}
