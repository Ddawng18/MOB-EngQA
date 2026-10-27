package com.example.engqa.worker;

import android.content.Context;
import android.net.Uri;

import com.example.engqa.media.ImageUtils;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Copies attachments into app-internal storage and writes {@code draft.json}, so a queued post
 * survives URI-permission loss, process death and reboots.
 */
public final class PendingDraftStore {

    private static final String DRAFT_FILE = "draft.json";
    private static final Gson GSON = new Gson();

    private PendingDraftStore() {
    }

    public static File save(Context context, PendingQuestionDraft draft,
                            List<Uri> images, Uri audio) throws IOException {
        File root = new File(context.getFilesDir(), "pending");
        File folder = new File(root, UUID.randomUUID().toString());
        File imagesDir = new File(folder, "images");
        if (!imagesDir.mkdirs()) {
            throw new IOException("Không tạo được thư mục nháp");
        }

        draft.imageFiles = new ArrayList<>();
        if (images != null) {
            for (int i = 0; i < images.size(); i++) {
                Uri uri = images.get(i);
                String extension = extensionFor(ImageUtils.mimeType(context, uri), ".jpg");
                String name = "img_" + i + extension;
                copy(context, uri, new File(imagesDir, name));
                draft.imageFiles.add(name);
            }
        }

        if (audio != null) {
            File audioDir = new File(folder, "audio");
            if (!audioDir.mkdirs()) {
                throw new IOException("Không tạo được thư mục âm thanh");
            }
            String extension = extensionFor(ImageUtils.mimeType(context, audio), ".m4a");
            String name = "audio" + extension;
            copy(context, audio, new File(audioDir, name));
            draft.audioFile = "audio/" + name;
        }

        File draftFile = new File(folder, DRAFT_FILE);
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(draftFile), StandardCharsets.UTF_8)) {
            GSON.toJson(draft, writer);
        }
        return folder;
    }

    public static PendingQuestionDraft read(File folder) {
        if (folder == null) {
            return null;
        }
        File draftFile = new File(folder, DRAFT_FILE);
        if (!draftFile.isFile()) {
            return null;
        }
        try (Reader reader = new InputStreamReader(
                new FileInputStream(draftFile), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, PendingQuestionDraft.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void delete(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        File[] children = folder.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        folder.delete();
    }

    private static void copy(Context context, Uri source, File destination) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {
            if (in == null) {
                throw new IOException("Không đọc được tệp đã chọn");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static String extensionFor(String mime, String fallback) {
        String type = mime != null ? mime.toLowerCase(Locale.ROOT) : "";
        if (type.contains("jpeg") || type.contains("jpg")) {
            return ".jpg";
        }
        if (type.contains("png")) {
            return ".png";
        }
        if (type.contains("m4a") || type.contains("mp4")) {
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
        return fallback;
    }
}
