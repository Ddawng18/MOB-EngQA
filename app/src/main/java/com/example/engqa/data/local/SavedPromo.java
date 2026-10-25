package com.example.engqa.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * Locally saved promo (Room). Keeping this out of Firestore means saving costs no reads/writes and
 * needs no security rule. The owner and promo id form the key so two accounts on one device can
 * each keep their own copy of the same promo.
 */
@Entity(tableName = "saved_promo", primaryKeys = {"owner_uid", "promo_id"})
public class SavedPromo {

    /** The signed-in owner, so saved items never leak between accounts on one device. */
    @NonNull
    @ColumnInfo(name = "owner_uid")
    private String ownerUid;

    @NonNull
    @ColumnInfo(name = "promo_id")
    private String promoId;

    @ColumnInfo(name = "center_name")
    private String centerName;

    private String title;

    @ColumnInfo(name = "image_url")
    private String imageUrl;

    @ColumnInfo(name = "saved_at")
    private long savedAt;

    public SavedPromo(@NonNull String ownerUid, @NonNull String promoId, String centerName,
                      String title, String imageUrl, long savedAt) {
        this.ownerUid = ownerUid;
        this.promoId = promoId;
        this.centerName = centerName;
        this.title = title;
        this.imageUrl = imageUrl;
        this.savedAt = savedAt;
    }

    @NonNull
    public String getOwnerUid() {
        return ownerUid;
    }

    public void setOwnerUid(@NonNull String ownerUid) {
        this.ownerUid = ownerUid;
    }

    @NonNull
    public String getPromoId() {
        return promoId;
    }

    public void setPromoId(@NonNull String promoId) {
        this.promoId = promoId;
    }

    public String getCenterName() {
        return centerName;
    }

    public void setCenterName(String centerName) {
        this.centerName = centerName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public long getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(long savedAt) {
        this.savedAt = savedAt;
    }
}
