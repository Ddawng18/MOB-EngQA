package com.example.engqa.data;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.LruCache;

import com.example.engqa.EngQAApp;
import com.example.engqa.media.ImageEncoder;
import com.example.engqa.util.ImageRefs;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageRepository {

    private static final LruCache<String, byte[]> CACHE = new LruCache<String, byte[]>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, byte[] value) {
            return value != null ? value.length : 0;
        }
    };

    public void uploadImage(Uri uri, String uid, String questionId, String fileName,
                            ProgressCallback progress, Callback<String> callback) {
        Context context = EngQAApp.getContext();
        if (progress != null) {
            progress.onProgress(0);
        }
        ImageEncoder.encode(context, uri, progressCallback -> {
            if (progress != null) {
                progress.onProgress((int) (progressCallback * 0.6f));
            }
        }, new ImageEncoder.Callback() {
            @Override
            public void onSuccess(ImageEncoder.Result result) {
                try {
                    if (progress != null) {
                        progress.onProgress(60);
                    }
                    String base64 = Base64.encodeToString(result.getBytes(), Base64.NO_WRAP);
                    if (base64.length() > 480000) {
                        throw new IllegalStateException("Ảnh quá lớn, vui lòng chọn ảnh nhỏ hơn");
                    }
                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    String imageId = db.collection(FirestoreRefs.IMAGES).document().getId();

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("ownerId", uid);
                    doc.put("questionId", questionId);
                    doc.put("mimeType", "image/jpeg");
                    doc.put("width", result.getWidth());
                    doc.put("height", result.getHeight());
                    doc.put("sizeBytes", (long) result.getBytes().length);
                    doc.put("dataBase64", base64);
                    doc.put("createdAt", FieldValue.serverTimestamp());

                    if (progress != null) {
                        progress.onProgress(80);
                    }

                    db.collection(FirestoreRefs.IMAGES).document(imageId)
                            .set(doc)
                            .addOnSuccessListener(aVoid -> {
                                if (progress != null) {
                                    progress.onProgress(100);
                                }
                                callback.onSuccess(ImageRefs.toRef(imageId));
                            })
                            .addOnFailureListener(callback::onError);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public void loadBytes(String ref, Callback<byte[]> callback) {
        if (!ImageRefs.isRef(ref)) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Không phải tham chiếu ảnh hợp lệ"));
            }
            return;
        }
        String imageId = ImageRefs.idOf(ref);
        if (imageId == null || imageId.isEmpty()) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Mã ảnh không hợp lệ"));
            }
            return;
        }
        byte[] cached = CACHE.get(imageId);
        if (cached != null) {
            if (callback != null) {
                callback.onSuccess(cached);
            }
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(FirestoreRefs.IMAGES).document(imageId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        if (callback != null) {
                            callback.onError(new IllegalStateException("Không tìm thấy dữ liệu ảnh"));
                        }
                        return;
                    }
                    String base64 = snapshot.getString("dataBase64");
                    if (base64 == null || base64.isEmpty()) {
                        if (callback != null) {
                            callback.onError(new IllegalStateException("Nội dung ảnh bị trống"));
                        }
                        return;
                    }
                    try {
                        byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
                        CACHE.put(imageId, bytes);
                        if (callback != null) {
                            callback.onSuccess(bytes);
                        }
                    } catch (Exception e) {
                        if (callback != null) {
                            callback.onError(e);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onError(e);
                    }
                });
    }

    public void deleteByRefs(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        for (String ref : refs) {
            if (ImageRefs.isRef(ref)) {
                String imageId = ImageRefs.idOf(ref);
                if (imageId != null && !imageId.isEmpty()) {
                    db.collection(FirestoreRefs.IMAGES).document(imageId).delete();
                }
            }
        }
    }
}
