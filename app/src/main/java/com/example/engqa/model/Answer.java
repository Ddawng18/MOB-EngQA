package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/** Firestore POJO for {@code questions/{qid}/answers/{aid}}. */
@IgnoreExtraProperties
public class Answer {

    @DocumentId
    private String id;
    private String questionId;
    private String authorId;
    private String authorName;
    private String body;
    private long upCount;
    private long downCount;
    private boolean isAccepted;
    @ServerTimestamp
    private Date createdAt;

    public Answer() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public long getUpCount() {
        return upCount;
    }

    public void setUpCount(long upCount) {
        this.upCount = upCount;
    }

    public long getDownCount() {
        return downCount;
    }

    public void setDownCount(long downCount) {
        this.downCount = downCount;
    }

    @PropertyName("isAccepted")
    public boolean isAccepted() {
        return isAccepted;
    }

    @PropertyName("isAccepted")
    public void setAccepted(boolean accepted) {
        isAccepted = accepted;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    /** Net score used for sorting and display. */
    public long netScore() {
        return upCount - downCount;
    }
}
