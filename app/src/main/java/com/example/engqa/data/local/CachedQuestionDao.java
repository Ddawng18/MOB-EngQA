package com.example.engqa.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CachedQuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CachedQuestion> questions);

    @Query("SELECT * FROM cached_question WHERE owner_uid = :ownerUid "
            + "AND (:categoryId IS NULL OR :categoryId = '' OR category_id = :categoryId) "
            + "ORDER BY created_at DESC LIMIT 200")
    List<CachedQuestion> load(String ownerUid, String categoryId);

    @Query("SELECT * FROM cached_question WHERE owner_uid = :ownerUid "
            + "AND question_id = :questionId LIMIT 1")
    CachedQuestion find(String ownerUid, String questionId);

    @Query("DELETE FROM cached_question WHERE owner_uid = :ownerUid")
    void clear(String ownerUid);

    @Query("DELETE FROM cached_question WHERE cached_at < :cutoff")
    void deleteOlderThan(long cutoff);
}
