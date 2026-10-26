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

    /** Moderation state. DELETED is a soft delete: the profile stays as a tombstone. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_DELETED = "DELETED";

    public static final long WARNINGS_PER_TIMEOUT = 3;
    public static final long TIMEOUTS_BEFORE_DELETE = 3;
    public static final long PROMO_WARNINGS_BEFORE_CENTER_REVOKE = 3;
    public static final long TIMEOUT_DURATION_MS = 24L * 60 * 60 * 1000;

    @DocumentId
    private String uid;
    private String username;
    private String email;
    private String level;
    private String avatarUrl;
    private String role;
    private String centerId;
    private long questionCount;
    private long answerCount;
    private long acceptedCount;
    private long warningCount;
    private long timeoutCount;
    private long promoWarningCount;
    private String status = STATUS_ACTIVE;
    private Date timeoutUntil;
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

    public long getWarningCount() {
        return warningCount;
    }

    public void setWarningCount(long warningCount) {
        this.warningCount = warningCount;
    }

    public long getTimeoutCount() {
        return timeoutCount;
    }

    public void setTimeoutCount(long timeoutCount) {
        this.timeoutCount = timeoutCount;
    }

    public long getPromoWarningCount() {
        return promoWarningCount;
    }

    public void setPromoWarningCount(long promoWarningCount) {
        this.promoWarningCount = promoWarningCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getTimeoutUntil() {
        return timeoutUntil;
    }

    public void setTimeoutUntil(Date timeoutUntil) {
        this.timeoutUntil = timeoutUntil;
    }

    @Exclude
    public boolean isDeleted() {
        return STATUS_DELETED.equals(status);
    }

    /** True while the moderation timeout is still in the future. */
    @Exclude
    public boolean isTimedOut() {
        return timeoutUntil != null && timeoutUntil.after(new Date());
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
