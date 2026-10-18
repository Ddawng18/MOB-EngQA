package com.example.engqa.data;

import android.net.Uri;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

/**
 * Uploads images and audio to Cloud Storage under {@code questions/{uid}/{qid}/...}.
 *
 * <p>Images are limited to 5 MB (JPG/PNG) and audio to 10 MB (MP3/M4A/AAC/WAV) — the same limits
 * enforced by {@code storage.rules}. Only download URLs are stored in Firestore.
 */
public class StorageRepository {

    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    public static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final FirebaseStorage storage = FirebaseStorage.getInstance();

    public void uploadImage(Uri uri, String uid, String questionId, String fileName,
                            ProgressCallback progress, Callback<String> callback) {
        String path = "questions/" + uid + "/" + questionId + "/images/" + fileName;
        upload(uri, path, "image/*", progress, callback);
    }

    public void uploadAudio(Uri uri, String uid, String questionId, String fileName,
                            ProgressCallback progress, Callback<String> callback) {
        String path = "questions/" + uid + "/" + questionId + "/audio/" + fileName;
        upload(uri, path, "audio/*", progress, callback);
    }

    private void upload(Uri uri, String path, String contentType, ProgressCallback progress,
                        Callback<String> callback) {
        StorageReference ref = storage.getReference().child(path);
        UploadTask task = ref.putFile(uri, new com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(contentType)
                .build());
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
        }).addOnSuccessListener(downloadUri -> callback.onSuccess(downloadUri.toString()))
          .addOnFailureListener(callback::onError);
    }
}
