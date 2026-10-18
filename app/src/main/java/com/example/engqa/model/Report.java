package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/** Firestore POJO for {@code reports/{id}} (Group B). */
@IgnoreExtraProperties
public class Report {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RESOLVED = "resolved";

    @DocumentId
    private String id;
    private String targetPath;
    private String reporterId;
    private String reporterName;
    private String reason;
    private String status = STATUS_PENDING;
    @ServerTimestamp
    private Date createdAt;

    public Report() {
    }

    public Report(String targetPath, String reporterId, String reporterName, String reason) {
        this.targetPath = targetPath;
        this.reporterId = reporterId;
        this.reporterName = reporterName;
        this.reason = reason;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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
