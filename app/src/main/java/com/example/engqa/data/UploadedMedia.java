package com.example.engqa.data;

public class UploadedMedia {

    private final String url;
    private final String storagePath;
    private final String mimeType;
    private final long sizeBytes;
    private final boolean isImage;

    public UploadedMedia(String url, String storagePath, String mimeType, long sizeBytes, boolean isImage) {
        this.url = url;
        this.storagePath = storagePath;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.isImage = isImage;
    }

    public String getUrl() {
        return url;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public boolean isImage() {
        return isImage;
    }
}
