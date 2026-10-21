package com.example.engqa.data;

import android.content.Context;
import android.net.Uri;

import com.example.engqa.media.ImageUtils;
import com.example.engqa.util.Validators;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.Locale;

public class MediaStorageRepository {

    private final FirebaseStorage storage = FirebaseStorage.getInstance();

    public void uploadImage(Context context, Uri uri, String uid, String questionId, int index,
                            ProgressCallback progress, Callback<UploadedMedia> callback) {
        String mime = ImageUtils.mimeType(context, uri);
        if (mime != null) {
            mime = mime.toLowerCase(Locale.ROOT);
        }
        if ("image/jpg".equals(mime)) {
            mime = "image/jpeg";
        }
        if (!Validators.isAllowedImageType(mime)) {
            callback.onError(new IllegalArgumentException("Chỉ hỗ trợ ảnh JPG/PNG"));
            return;
        }

        long size = ImageUtils.sizeBytes(context, uri);
        if (size <= 0 || size > StorageRepository.MAX_IMAGE_BYTES) {
            callback.onError(new IllegalArgumentException("Ảnh vượt quá dung lượng tối đa 5MB"));
            return;
        }

        String ext = "jpg";
        if ("image/png".equals(mime)) {
            ext = "png";
        }

        String fileName = "img_" + index + "_" + System.currentTimeMillis() + "." + ext;
        String path = "questions/" + uid + "/" + questionId + "/images/" + fileName;

        uploadFile(uri, path, mime, size, true, progress, callback);
    }

    public void uploadAudio(Context context, Uri uri, String uid, String questionId,
                            ProgressCallback progress, Callback<UploadedMedia> callback) {
        String mime = ImageUtils.mimeType(context, uri);
        if (mime != null) {
            mime = mime.toLowerCase(Locale.ROOT);
        }
        if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
            if (mime == null || "application/octet-stream".equals(mime)) {
                mime = "audio/mp4";
            }
        }

        if (mime == null || (!Validators.isAllowedAudioType(mime) && !mime.startsWith("audio/"))) {
            callback.onError(new IllegalArgumentException("Định dạng âm thanh không được hỗ trợ"));
            return;
        }

        long size = ImageUtils.sizeBytes(context, uri);
        if (size > StorageRepository.MAX_AUDIO_BYTES) {
            callback.onError(new IllegalArgumentException("Tệp âm thanh vượt quá dung lượng tối đa 10MB"));
            return;
        }

        String ext = "m4a";
        if ("audio/mpeg".equals(mime) || "audio/mp3".equals(mime)) {
            ext = "mp3";
        } else if ("audio/wav".equals(mime) || "audio/x-wav".equals(mime)) {
            ext = "wav";
        } else if ("audio/mp4".equals(mime) || "audio/m4a".equals(mime)
                || "audio/x-m4a".equals(mime) || "audio/aac".equals(mime) || "audio/3gpp".equals(mime)) {
            ext = "m4a";
        }

        String fileName = "audio_" + System.currentTimeMillis() + "." + ext;
        String path = "questions/" + uid + "/" + questionId + "/audio/" + fileName;

        uploadFile(uri, path, mime, size, false, progress, callback);
    }

    private void uploadFile(Uri uri, String path, String mime, long size, boolean isImage,
                            ProgressCallback progress, Callback<UploadedMedia> callback) {
        StorageReference ref = storage.getReference().child(path);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(mime)
                .build();
        UploadTask task = ref.putFile(uri, metadata);
        task.addOnProgressListener(snapshot -> {
            if (progress != null && snapshot.getTotalByteCount() > 0) {
                int percent = (int) (100 * snapshot.getBytesTransferred()
                        / snapshot.getTotalByteCount());
                progress.onProgress(percent);
            }
        });
        task.continueWithTask(t -> {
            if (!t.isSuccessful()) {
                throw t.getException() != null ? t.getException()
                        : new IllegalStateException("Tải lên thất bại");
            }
            return ref.getDownloadUrl();
        }).addOnSuccessListener(downloadUri -> {
            UploadedMedia media = new UploadedMedia(downloadUri.toString(), path, mime, size, isImage);
            callback.onSuccess(media);
        }).addOnFailureListener(callback::onError);
    }
}
