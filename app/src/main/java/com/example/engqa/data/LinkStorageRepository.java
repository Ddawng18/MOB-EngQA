package com.example.engqa.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.engqa.NetworkUtils;
import com.example.engqa.media.ImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads media to a free anonymous file host and returns a public HTTPS link.
 *
 * <p>Why this exists: Cloud Storage for Firebase requires the Blaze plan, so a Spark project cannot
 * create a bucket. Firestore already stores media as URLs ({@code imageUrls}, {@code audioUrl}), and
 * the UI loads them with Glide / MediaPlayer, so uploading to any HTTPS host works without changing
 * the schema or the screens.
 *
 * <p>The upload runs on a background executor (never on the main thread) and posts progress and the
 * result back to the main thread. It is a drop-in replacement for {@link StorageRepository}: the
 * same method signatures are used by {@code CreateQuestionViewModel}.
 *
 * <p>Trade-off: x0.at keeps files for a limited time (like 0x0.st). For a long-lived deployment,
 * switch back to {@link StorageRepository} after enabling Blaze, or use Cloudinary.
 */
public class LinkStorageRepository {

    private static final String ENDPOINT = "https://x0.at";
    private static final String USER_AGENT = "EngQA/1.0 (Android)";
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 180_000;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_FALLBACK_BYTES = 12 * 1024 * 1024;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    /**
     * @param context application context; injected (rather than read from a global) so the uploader
     *                is explicit about its dependency and easier to test.
     */
    public LinkStorageRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public void uploadImage(Uri uri, String uid, String questionId, String fileName,
                            ProgressCallback progress, Callback<String> callback) {
        upload(uri, "images", progress, callback);
    }

    public void uploadAudio(Uri uri, String uid, String questionId, String fileName,
                            ProgressCallback progress, Callback<String> callback) {
        upload(uri, "audio", progress, callback);
    }

    /** Stops the worker thread; call from the ViewModel's {@code onCleared()}. */
    public void shutdown() {
        executor.shutdown();
    }

    /** Synchronous upload used by the WorkManager worker (already on a background thread). */
    public String uploadFileSync(Context context, Uri uri, String kind) throws IOException {
        return uploadBlocking(context, uri, kind, null);
    }

    private void upload(Uri uri, String kind, ProgressCallback progress,
                        Callback<String> callback) {
        executor.execute(() -> {
            try {
                String url = uploadBlocking(context, uri, kind, progress);
                mainHandler.post(() -> callback.onSuccess(url));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private String uploadBlocking(Context context, Uri uri, String kind,
                                  ProgressCallback progress) throws IOException {
        if (!NetworkUtils.isOnline(context)) {
            throw new IOException("Không có kết nối mạng. Kiểm tra lại đường truyền.");
        }
        String mime = ImageUtils.mimeType(context, uri);
        String fileName = safeFileName(ImageUtils.displayName(context, uri), mime, kind);
        long size = ImageUtils.sizeBytes(context, uri);

        String boundary = "----EngQA" + UUID.randomUUID().toString().replace("-", "");
        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] buffered = null;
        if (size <= 0) {
            // Some content providers do not expose a size; buffer so we can still send a
            // Content-Length, which a few PHP hosts require.
            buffered = readAllBytes(context, uri, MAX_FALLBACK_BYTES);
            size = buffered.length;
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/plain");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setFixedLengthStreamingMode(head.length + size + tail.length);

        long total = head.length + size + tail.length;
        long written = 0;
        int lastPercent = -1;

        try (OutputStream out = connection.getOutputStream()) {
            out.write(head);
            written += head.length;

            if (buffered != null) {
                out.write(buffered);
                written += buffered.length;
                report(progress, 100);
            } else {
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        throw new IOException("Không đọc được tệp đã chọn");
                    }
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        written += read;
                        int percent = (int) (written * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            report(progress, percent);
                        }
                    }
                }
            }

            out.write(tail);
            out.flush();
        }

        int code = connection.getResponseCode();
        String body = readAll(connection,
                code >= 200 && code < 300 ? connection.getInputStream()
                        : connection.getErrorStream());
        connection.disconnect();

        if (code < 200 || code >= 300) {
            throw new IOException("Máy chủ lưu tệp trả về HTTP " + code
                    + (body.isEmpty() ? "" : ": " + body));
        }

        String link = body.trim();
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            throw new IOException("Máy chủ lưu tệp không trả về liên kết hợp lệ");
        }
        report(progress, 100);
        return link;
    }

    private void report(ProgressCallback progress, int percent) {
        if (progress != null) {
            mainHandler.post(() -> progress.onProgress(percent));
        }
    }

    private static byte[] readAllBytes(Context context, Uri uri, int maxBytes) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IOException("Không đọc được tệp đã chọn");
            }
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Tệp quá lớn để tải lên");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String readAll(HttpURLConnection connection, InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream stream = in;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static String safeFileName(String name, String mime, String kind) {
        String base = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty()) {
            base = kind + "_" + System.currentTimeMillis();
        }
        if (!base.contains(".")) {
            base = base + extensionFor(mime, kind);
        }
        return base;
    }

    private static String extensionFor(String mime, String kind) {
        String type = mime != null ? mime.toLowerCase(Locale.ROOT) : "";
        if (type.contains("jpeg") || type.contains("jpg")) {
            return ".jpg";
        }
        if (type.contains("png")) {
            return ".png";
        }
        if (type.contains("mp4") || type.contains("m4a")) {
            return ".m4a";
        }
        if (type.contains("mpeg") || type.contains("mp3")) {
            return ".mp3";
        }
        if (type.contains("wav")) {
            return ".wav";
        }
        if (type.contains("aac")) {
            return ".aac";
        }
        if (type.contains("3gpp")) {
            return ".3gp";
        }
        return "audio".equals(kind) ? ".m4a" : ".jpg";
    }
}
