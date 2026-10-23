package com.example.engqa.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.engqa.data.ProgressCallback;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageEncoder {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ImageEncoder() {
    }

    public static class Result {
        private final byte[] bytes;
        private final long width;
        private final long height;

        public Result(byte[] bytes, long width, long height) {
            this.bytes = bytes;
            this.width = width;
            this.height = height;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public long getWidth() {
            return width;
        }

        public long getHeight() {
            return height;
        }
    }

    public interface Callback {
        void onSuccess(Result result);

        void onError(Exception e);
    }

    public static void encode(Context context, Uri uri, ProgressCallback progress, Callback callback) {
        executor.execute(() -> {
            try {
                if (progress != null) {
                    mainHandler.post(() -> progress.onProgress(10));
                }
                Result result = processImage(context, uri);
                if (progress != null) {
                    mainHandler.post(() -> progress.onProgress(60));
                }
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private static Result processImage(Context context, Uri uri) throws Exception {
        InputStream dimIs = context.getContentResolver().openInputStream(uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(dimIs, null, options);
        if (dimIs != null) {
            dimIs.close();
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw new IllegalArgumentException("Không thể đọc kích thước ảnh");
        }

        int inSampleSize = 1;
        int maxDim = Math.max(options.outWidth, options.outHeight);
        while (maxDim / inSampleSize > 1024) {
            inSampleSize *= 2;
        }

        int rotation = 0;
        try (InputStream exifIs = context.getContentResolver().openInputStream(uri)) {
            if (exifIs != null) {
                ExifInterface exifInterface = new ExifInterface(exifIs);
                int orientation = exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                    rotation = 90;
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    rotation = 180;
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                    rotation = 270;
                }
            }
        } catch (Exception ignored) {
        }

        InputStream decodeIs = context.getContentResolver().openInputStream(uri);
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = inSampleSize;
        Bitmap originalBitmap = BitmapFactory.decodeStream(decodeIs, null, decodeOptions);
        if (decodeIs != null) {
            decodeIs.close();
        }

        if (originalBitmap == null) {
            throw new IllegalArgumentException("Không thể đọc định dạng ảnh");
        }

        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(originalBitmap, 0, 0,
                    originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, true);
            if (rotated != originalBitmap) {
                originalBitmap.recycle();
                originalBitmap = rotated;
            }
        }

        String mime = context.getContentResolver().getType(uri);
        boolean isPng = (mime != null && mime.equalsIgnoreCase("image/png")) || originalBitmap.hasAlpha();
        Bitmap workingBitmap;
        if (isPng) {
            workingBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(workingBitmap);
            canvas.drawColor(0xFF141414);
            canvas.drawBitmap(originalBitmap, 0, 0, null);
            originalBitmap.recycle();
        } else {
            workingBitmap = originalBitmap;
        }

        int[] qualities = {80, 70, 60, 50, 40};
        byte[] compressedBytes = null;
        int finalWidth = 0;
        int finalHeight = 0;

        Bitmap currentBitmap = workingBitmap;
        int scaleAttempts = 0;

        while (scaleAttempts <= 3) {
            for (int quality : qualities) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                byte[] bytes = baos.toByteArray();
                if (bytes.length <= 350000) {
                    compressedBytes = bytes;
                    finalWidth = currentBitmap.getWidth();
                    finalHeight = currentBitmap.getHeight();
                    break;
                }
            }
            if (compressedBytes != null) {
                break;
            }
            if (scaleAttempts < 3) {
                int newWidth = (int) (currentBitmap.getWidth() * 0.8f);
                int newHeight = (int) (currentBitmap.getHeight() * 0.8f);
                if (newWidth <= 0 || newHeight <= 0) {
                    break;
                }
                Bitmap scaled = Bitmap.createScaledBitmap(currentBitmap, newWidth, newHeight, true);
                if (scaled != currentBitmap) {
                    currentBitmap.recycle();
                }
                currentBitmap = scaled;
            }
            scaleAttempts++;
        }

        if (!currentBitmap.isRecycled()) {
            currentBitmap.recycle();
        }

        if (compressedBytes == null) {
            throw new IllegalStateException("Ảnh quá lớn, vui lòng chọn ảnh nhỏ hơn");
        }

        return new Result(compressedBytes, finalWidth, finalHeight);
    }
}
