package com.example.engqa.data.local;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.engqa.data.Callback;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Writes to Room on a background {@link ExecutorService} (never on the main thread) and posts the
 * result back to the main thread so callers can touch the UI.
 */
public class SavedPromoRepository {

    private final SavedPromoDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SavedPromoRepository(Context context) {
        this.dao = AppDatabase.get(context).savedPromoDao();
    }

    public LiveData<List<SavedPromo>> observeAll() {
        return dao.getAll();
    }

    public void save(final SavedPromo promo, final Callback<Void> callback) {
        executor.execute(() -> {
            try {
                dao.insert(promo);
                postSuccess(callback);
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void remove(final String promoId, final Callback<Void> callback) {
        executor.execute(() -> {
            try {
                dao.delete(promoId);
                postSuccess(callback);
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void isSaved(final String promoId, final Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                boolean saved = dao.findId(promoId) != null;
                mainHandler.post(() -> callback.onSuccess(saved));
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    private void postSuccess(final Callback<Void> callback) {
        mainHandler.post(() -> callback.onSuccess(null));
    }

    private <T> void postError(final Callback<T> callback, final Exception e) {
        mainHandler.post(() -> callback.onError(e));
    }
}
