package com.example.engqa.data;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

public class MediaUploadSession {

    private final List<UploadedMedia> uploadedItems = new ArrayList<>();
    private final FirebaseStorage storage = FirebaseStorage.getInstance();

    public synchronized void add(UploadedMedia media) {
        if (media != null) {
            uploadedItems.add(media);
        }
    }

    public synchronized List<String> getImageUrls() {
        List<String> urls = new ArrayList<>();
        for (UploadedMedia item : uploadedItems) {
            if (item.isImage()) {
                urls.add(item.getUrl());
            }
        }
        return urls;
    }

    public synchronized String getAudioUrl() {
        for (UploadedMedia item : uploadedItems) {
            if (!item.isImage()) {
                return item.getUrl();
            }
        }
        return null;
    }

    public synchronized void rollback() {
        for (UploadedMedia item : uploadedItems) {
            try {
                StorageReference ref = storage.getReference().child(item.getStoragePath());
                ref.delete();
            } catch (Exception ignored) {
            }
        }
        uploadedItems.clear();
    }
}
