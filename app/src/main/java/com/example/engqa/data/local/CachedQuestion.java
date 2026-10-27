package com.example.engqa.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * Offline copy of a question. The full Firestore document is stored as JSON so the cache does not
 * have to mirror every field as a column; the indexed columns are the ones used for feed ordering.
 */
@Entity(tableName = "cached_question", primaryKeys = {"owner_uid", "question_id"},
        indices = {@Index("owner_uid")})
public class CachedQuestion {

    @NonNull
    @ColumnInfo(name = "owner_uid")
    private String ownerUid;

    @NonNull
    @ColumnInfo(name = "question_id")
    private String questionId;

    @ColumnInfo(name = "payload")
    private String payload;

    @ColumnInfo(name = "category_id")
    private String categoryId;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "score")
    private long score;

    @ColumnInfo(name = "answer_count")
    private long answerCount;

    @ColumnInfo(name = "cached_at")
    private long cachedAt;

    public CachedQuestion(@NonNull String ownerUid, @NonNull String questionId, String payload,
                          String categoryId, long createdAt, long score, long answerCount,
                          long cachedAt) {
        this.ownerUid = ownerUid;
        this.questionId = questionId;
        this.payload = payload;
        this.categoryId = categoryId;
        this.createdAt = createdAt;
        this.score = score;
        this.answerCount = answerCount;
        this.cachedAt = cachedAt;
    }

    @NonNull
    public String getOwnerUid() {
        return ownerUid;
    }

    @NonNull
    public String getQuestionId() {
        return questionId;
    }

    public String getPayload() {
        return payload;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getScore() {
        return score;
    }

    public long getAnswerCount() {
        return answerCount;
    }

    public long getCachedAt() {
        return cachedAt;
    }
}
