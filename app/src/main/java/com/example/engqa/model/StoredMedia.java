package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/** Firestore POJO for {@code questions/{qid}/media/{mid}} (Firestore-only media storage). */
@IgnoreExtraProperties
public class StoredMedia {

    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_AUDIO = "audio";

    @DocumentId
    private String id;
    private String type;
    private String mime;
    private String data;
    private long order;
    private long sizeBytes;
    @ServerTimestamp
    private Date createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public long getOrder() {
        return order;
    }

    public void setOrder(long order) {
        this.order = order;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isImage() {
        return TYPE_IMAGE.equals(type);
    }

    public boolean isAudio() {
        return TYPE_AUDIO.equals(type);
    }
}
