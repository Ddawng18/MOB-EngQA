package com.example.engqa.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Locally saved promo (Room). Keeping this out of Firestore means saving costs no reads/writes and
 * needs no security rule.
 */
@Entity(tableName = "saved_promo")
public class SavedPromo {

    @PrimaryKey
    @NonNull
    private String promoId;

    @ColumnInfo(name = "center_name")
    private String centerName;

    private String title;

    @ColumnInfo(name = "image_url")
    private String imageUrl;

    @ColumnInfo(name = "saved_at")
    private long savedAt;

    public SavedPromo(@NonNull String promoId, String centerName, String title,
                      String imageUrl, long savedAt) {
        this.promoId = promoId;
        this.centerName = centerName;
        this.title = title;
        this.imageUrl = imageUrl;
        this.savedAt = savedAt;
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
