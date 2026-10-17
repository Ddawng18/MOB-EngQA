package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Firestore POJO for {@code promos/{promoId}/leads/{uid}} — a learner's consultation request.
 *
 * <p>The document id is the learner's uid, which prevents duplicate requests for the same promo
 * without any extra query.
 */
@IgnoreExtraProperties
public class Lead {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_CONTACTED = "CONTACTED";
    public static final String STATUS_DONE = "DONE";

    @DocumentId
    private String uid;
    private String userUid;
    private String name;
    private String phone;
    private String note;
    private String status = STATUS_NEW;
    @ServerTimestamp
    private Date createdAt;

    public Lead() {
    }

    public Lead(String uid, String name, String phone, String note) {
        this.uid = uid;
        this.userUid = uid;
        this.name = name;
        this.phone = phone;
        this.note = note;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
