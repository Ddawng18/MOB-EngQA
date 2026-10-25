package com.example.engqa.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** Local "saved promos" queries; the list is a {@link LiveData} so the screen updates itself. */
@Dao
public interface SavedPromoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SavedPromo promo);

    @Query("DELETE FROM saved_promo WHERE owner_uid = :ownerUid AND promo_id = :promoId")
    void delete(String ownerUid, String promoId);

    @Query("SELECT * FROM saved_promo WHERE owner_uid = :ownerUid ORDER BY saved_at DESC")
    LiveData<List<SavedPromo>> getAll(String ownerUid);

    @Query("SELECT promo_id FROM saved_promo WHERE owner_uid = :ownerUid "
            + "AND promo_id = :promoId LIMIT 1")
    String findId(String ownerUid, String promoId);
}
