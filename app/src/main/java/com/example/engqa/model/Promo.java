package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Firestore POJO for {@code promos/{promoId}} — a centre's promotional post.
 *
 * <p>Promos live in their own collection so the Q&amp;A feed is never diluted; the feed interleaves
 * them (see {@code util.FeedMixer}). {@code centerName} and {@code centerVerified} are
 * denormalised so the feed needs a single query.
 */
@IgnoreExtraProperties
public class Promo {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_HIDDEN = "HIDDEN";

    public static final String TYPE_COURSE = "COURSE";
    public static final String TYPE_EVENT = "EVENT";
    public static final String TYPE_SCHOLARSHIP = "SCHOLARSHIP";
    public static final String TYPE_ANNOUNCEMENT = "ANNOUNCEMENT";

    @DocumentId
    private String promoId;
    private String centerId;
    private String centerName;
    private boolean centerVerified;
    private String authorUid;
    private String title;
    private String body;
    private String type;
    private String level;
    private String fee;
    private String schedule;
    private String contactPhone;
    private String linkUrl;
    private String imageUrl;
    private String status = STATUS_PENDING;
    private long viewCount;
    private long clickCount;
    private long leadCount;
    @ServerTimestamp
    private Date createdAt;
    private Date endAt;

    public Promo() {
    }

    public String getPromoId() {
        return promoId;
    }

    public void setPromoId(String promoId) {
        this.promoId = promoId;
    }

    public String getCenterId() {
        return centerId;
    }

    public void setCenterId(String centerId) {
        this.centerId = centerId;
    }

    public String getCenterName() {
        return centerName;
    }

    public void setCenterName(String centerName) {
        this.centerName = centerName;
    }

    @PropertyName("centerVerified")
    public boolean isCenterVerified() {
        return centerVerified;
    }

    @PropertyName("centerVerified")
    public void setCenterVerified(boolean centerVerified) {
        this.centerVerified = centerVerified;
    }

    public String getAuthorUid() {
        return authorUid;
    }

    public void setAuthorUid(String authorUid) {
        this.authorUid = authorUid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getFee() {
        return fee;
    }

    public void setFee(String fee) {
        this.fee = fee;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public long getLeadCount() {
        return leadCount;
    }

    public void setLeadCount(long leadCount) {
        this.leadCount = leadCount;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getEndAt() {
        return endAt;
    }

    public void setEndAt(Date endAt) {
        this.endAt = endAt;
    }

    @Exclude
    public boolean isApproved() {
        return STATUS_APPROVED.equals(status);
    }

    /** A promo with a past {@code endAt} is hidden from the feed; {@code null} means no deadline. */
    @Exclude
    public boolean isExpired(long nowMillis) {
        return endAt != null && endAt.getTime() < nowMillis;
    }
}
