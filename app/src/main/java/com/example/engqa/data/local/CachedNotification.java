package com.example.engqa.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

/** Offline copy of one notification; the payload is JSON so the model can keep evolving. */
@Entity(tableName = "cached_notification", primaryKeys = {"owner_uid", "notification_id"},
        indices = {@Index("owner_uid")})
public class CachedNotification {

    @NonNull
    @ColumnInfo(name = "owner_uid")
    private String ownerUid;

    @NonNull
    @ColumnInfo(name = "notification_id")
    private String notificationId;

    @ColumnInfo(name = "payload")
    private String payload;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "is_read")
    private boolean read;

    @ColumnInfo(name = "cached_at")
    private long cachedAt;

    public CachedNotification(@NonNull String ownerUid, @NonNull String notificationId,
                              String payload, long createdAt, boolean read, long cachedAt) {
        this.ownerUid = ownerUid;
        this.notificationId = notificationId;
        this.payload = payload;
        this.createdAt = createdAt;
        this.read = read;
        this.cachedAt = cachedAt;
    }

    @NonNull
    public String getOwnerUid() {
        return ownerUid;
    }

    @NonNull
    public String getNotificationId() {
        return notificationId;
    }

    public String getPayload() {
        return payload;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public long getCachedAt() {
        return cachedAt;
    }
}
