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

    @Query("DELETE FROM saved_promo WHERE promoId = :promoId")
    void delete(String promoId);

    @Query("SELECT * FROM saved_promo ORDER BY saved_at DESC")
    LiveData<List<SavedPromo>> getAll();

    @Query("SELECT promoId FROM saved_promo WHERE promoId = :promoId LIMIT 1")
    String findId(String promoId);
}
