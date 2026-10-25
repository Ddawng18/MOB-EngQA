package com.example.engqa.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.engqa.SessionManager;
import com.example.engqa.data.Callback;
import com.example.engqa.data.local.SavedPromo;
import com.example.engqa.data.local.SavedPromoRepository;

import java.util.List;

/** Backs the "Bài đã lưu" screen (local Room data only). */
public class SavedViewModel extends AndroidViewModel {

    private final SavedPromoRepository repository;
    private final String uid;

    public SavedViewModel(@NonNull Application application) {
        super(application);
        repository = new SavedPromoRepository(application);
        uid = new SessionManager(application).getUserId();
    }

    public LiveData<List<SavedPromo>> getSavedPromos() {
        return repository.observeAll(uid);
    }

    public void remove(String promoId, Callback<Void> callback) {
        repository.remove(uid, promoId, callback);
    }
}
