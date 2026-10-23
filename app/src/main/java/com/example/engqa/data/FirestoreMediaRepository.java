package com.example.engqa.data;

import com.example.engqa.media.MediaCompressor;
import com.example.engqa.model.StoredMedia;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores media as Base64 documents under {@code questions/{qid}/media/{mid}}.
 *
 * <p>Why: Firebase Storage requires Blaze, so this project keeps everything on Firestore. Each
 * media item is a separate document (max 5 images + 1 audio, each within the 600k Base64 budget) so
 * the question document stays light and the feed never downloads full payloads.
 */
public class FirestoreMediaRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ListenerRegistration observeMedia(String questionId,
                                             Callback<List<StoredMedia>> callback) {
        return mediaCollection(questionId)
                .orderBy("order", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    List<StoredMedia> media = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            StoredMedia item = document.toObject(StoredMedia.class);
                            if (item != null) {
                                if (item.getId() == null) {
                                    item.setId(document.getId());
                                }
                                media.add(item);
                            }
                        }
                    }
                    callback.onSuccess(media);
                });
    }

    /** Writes all images + the optional audio in one atomic batch. */
    public void saveAll(String questionId, List<byte[]> images, byte[] audio,
                        String audioMime, Callback<Void> callback) {
        if ((images == null || images.isEmpty()) && audio == null) {
            callback.onSuccess(null);
            return;
        }
        WriteBatch batch = db.batch();
        int order = 0;
        for (byte[] image : images) {
            DocumentReference reference = mediaCollection(questionId).document();
            Map<String, Object> data = new HashMap<>();
            data.put("type", StoredMedia.TYPE_IMAGE);
            data.put("mime", "image/jpeg");
            data.put("order", order++);
            data.put("data", MediaCompressor.toBase64(image));
            data.put("sizeBytes", image.length);
            data.put("createdAt", FieldValue.serverTimestamp());
            batch.set(reference, data);
        }
        if (audio != null) {
            DocumentReference reference = mediaCollection(questionId).document();
            Map<String, Object> data = new HashMap<>();
            data.put("type", StoredMedia.TYPE_AUDIO);
            data.put("mime", audioMime != null ? audioMime : "audio/m4a");
            data.put("order", order);
            data.put("data", MediaCompressor.toBase64(audio));
            data.put("sizeBytes", audio.length);
            data.put("createdAt", FieldValue.serverTimestamp());
            batch.set(reference, data);
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void load(String questionId, String mediaId, Callback<StoredMedia> callback) {
        mediaCollection(questionId).document(mediaId).get()
                .addOnSuccessListener(document -> callback.onSuccess(
                        document != null && document.exists()
                                ? document.toObject(StoredMedia.class) : null))
                .addOnFailureListener(callback::onError);
    }

    private CollectionReference mediaCollection(String questionId) {
        return db.collection(FirestoreRefs.QUESTIONS).document(questionId)
                .collection(FirestoreRefs.MEDIA);
    }
}
