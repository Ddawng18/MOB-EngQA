package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Firestore POJO for {@code centers/{cid}}. */
@IgnoreExtraProperties
public class Center {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";

    @DocumentId
    private String id;
    private String name;
    private String logoUrl;
    private String description;
    private String address;
    private String phone;
    private String email;
    private List<String> managerUids = new ArrayList<>();
    private String status = STATUS_PENDING;
    @ServerTimestamp
    private Date createdAt;

    public Center() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getManagerUids() {
        return managerUids;
    }

    public void setManagerUids(List<String> managerUids) {
        this.managerUids = managerUids;
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

    public boolean isApproved() {
        return STATUS_APPROVED.equals(status);
    }
}
