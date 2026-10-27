package com.example.engqa.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CachedNotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CachedNotification> notifications);

    @Query("SELECT * FROM cached_notification WHERE owner_uid = :ownerUid "
            + "ORDER BY created_at DESC LIMIT 100")
    List<CachedNotification> load(String ownerUid);

    @Query("DELETE FROM cached_notification WHERE owner_uid = :ownerUid")
    void clear(String ownerUid);

    @Query("DELETE FROM cached_notification WHERE cached_at < :cutoff")
    void deleteOlderThan(long cutoff);
}
