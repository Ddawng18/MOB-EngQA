package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Firestore POJO for {@code questions/{qid}}. Media is stored as Storage URLs, never Base64. */
@IgnoreExtraProperties
public class Question {

    public static final String TYPE_GRAMMAR = "grammar";
    public static final String TYPE_VOCABULARY = "vocabulary";

    @DocumentId
    private String id;
    private String authorId;
    private String authorName;
    private String title;
    private String body;
    private String categoryId;
    private String categoryName;
    private List<String> tags = new ArrayList<>();
    private String level;
    private String type;
    private List<String> imageUrls = new ArrayList<>();
    private String audioUrl;
    private String thumbBase64;
    private long imageCount;
    private boolean hasAudio;
    private String passageText;
    private List<String> searchKeywords = new ArrayList<>();
    private long answerCount;
    private long score;
    private boolean isSolved;
    private String acceptedAnswerId;
    @ServerTimestamp
    private Date createdAt;
    @ServerTimestamp
    private Date updatedAt;

    public Question() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    /** Small Base64 thumbnail (Firestore-only media mode) used by the feed card. */
    public String getThumbBase64() {
        return thumbBase64;
    }

    public void setThumbBase64(String thumbBase64) {
        this.thumbBase64 = thumbBase64;
    }

    public long getImageCount() {
        return imageCount;
    }

    public void setImageCount(long imageCount) {
        this.imageCount = imageCount;
    }

    @PropertyName("hasAudio")
    public boolean isHasAudio() {
        return hasAudio;
    }

    @PropertyName("hasAudio")
    public void setHasAudio(boolean hasAudio) {
        this.hasAudio = hasAudio;
    }

    public String getPassageText() {
        return passageText;
    }

    public void setPassageText(String passageText) {
        this.passageText = passageText;
    }

    public List<String> getSearchKeywords() {
        return searchKeywords;
    }

    public void setSearchKeywords(List<String> searchKeywords) {
        this.searchKeywords = searchKeywords;
    }

    public long getAnswerCount() {
        return answerCount;
    }

    public void setAnswerCount(long answerCount) {
        this.answerCount = answerCount;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    @PropertyName("isSolved")
    public boolean isSolved() {
        return isSolved;
    }

    @PropertyName("isSolved")
    public void setSolved(boolean solved) {
        isSolved = solved;
    }

    public String getAcceptedAnswerId() {
        return acceptedAnswerId;
    }

    public void setAcceptedAnswerId(String acceptedAnswerId) {
        this.acceptedAnswerId = acceptedAnswerId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
