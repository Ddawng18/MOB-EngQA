package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/** Firestore POJO for {@code notifications/{id}}. */
@IgnoreExtraProperties
public class AppNotification {

    public static final String TYPE_ANSWER = "answer";
    public static final String TYPE_VOTE = "vote";
    public static final String TYPE_ACCEPTED = "accepted";
    /** Admin warning after a report; only admins may create this type (see firestore.rules). */
    public static final String TYPE_MODERATION_WARNING = "moderation_warning";
    /** Admin deletion of reported content; only admins may create this type. */
    public static final String TYPE_MODERATION_DELETED = "moderation_deleted";
    public static final String TYPE_CENTER_APPROVED = "center_approved";
    public static final String TYPE_CENTER_REJECTED = "center_rejected";
    public static final String TYPE_PROMO_APPROVED = "promo_approved";
    public static final String TYPE_PROMO_REJECTED = "promo_rejected";

    @DocumentId
    private String id;
    private String recipientId;
    private String actorId;
    private String actorName;
    private String type;
    private String questionId;
    private String answerId;
    private String reportId;
    private String centerId;
    private String promoId;
    private String title;
    private String body;
    private boolean read;
    @ServerTimestamp
    private Date createdAt;

    public AppNotification() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public String getAnswerId() {
        return answerId;
    }

    public void setAnswerId(String answerId) {
        this.answerId = answerId;
    }

    /** Report that produced this moderation notification, when applicable. */
    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    /** Center involved in a center/promo moderation notification, when applicable. */
    public String getCenterId() {
        return centerId;
    }

    public void setCenterId(String centerId) {
        this.centerId = centerId;
    }

    /** Promo involved in a promo moderation notification, when applicable. */
    public String getPromoId() {
        return promoId;
    }

    public void setPromoId(String promoId) {
        this.promoId = promoId;
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

    @PropertyName("read")
    public boolean isRead() {
        return read;
    }

    @PropertyName("read")
    public void setRead(boolean read) {
        this.read = read;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
