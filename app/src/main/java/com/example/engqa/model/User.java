package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/** Firestore POJO for {@code users/{uid}}. The document id equals the Firebase Auth uid. */
@IgnoreExtraProperties
public class User {

    /** Roles are granted by an administrator only (see firestore.rules). */
    public static final String ROLE_USER = "USER";
    public static final String ROLE_CENTER = "CENTER";
    public static final String ROLE_ADMIN = "ADMIN";

    @DocumentId
    private String uid;
    private String username;
    private String usernameLower;
    private String email;
    private String level;
    private String avatarUrl;
    private String role;
    private String centerId;
    private long questionCount;
    private long answerCount;
    private long acceptedCount;
    @ServerTimestamp
    private Date createdAt;

    public User() {
    }

    public User(String uid, String username, String email, String level) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.level = level;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsernameLower() {
        return usernameLower;
    }

    public void setUsernameLower(String usernameLower) {
        this.usernameLower = usernameLower;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCenterId() {
        return centerId;
    }

    public void setCenterId(String centerId) {
        this.centerId = centerId;
    }

    /** A missing role is treated as a normal learner. */
    @Exclude
    public String roleOrDefault() {
        return role != null ? role : ROLE_USER;
    }

    @Exclude
    public boolean isCenter() {
        return ROLE_CENTER.equals(role);
    }

    @Exclude
    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public long getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(long questionCount) {
        this.questionCount = questionCount;
    }

    public long getAnswerCount() {
        return answerCount;
    }

    public void setAnswerCount(long answerCount) {
        this.answerCount = answerCount;
    }

    public long getAcceptedCount() {
        return acceptedCount;
    }

    public void setAcceptedCount(long acceptedCount) {
        this.acceptedCount = acceptedCount;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
