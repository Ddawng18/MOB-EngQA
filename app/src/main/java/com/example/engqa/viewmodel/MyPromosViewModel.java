package com.example.engqa.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.Callback;
import com.example.engqa.data.PromoRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.model.Promo;
import com.example.engqa.model.UiState;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/** The signed-in centre's own promos and their moderation status. */
public class MyPromosViewModel extends ViewModel {

    private final PromoRepository repository = new PromoRepository();
    private final MutableLiveData<UiState<List<Promo>>> promos = new MutableLiveData<>();
    private ListenerRegistration registration;
    private String uid;

    public LiveData<UiState<List<Promo>>> getPromos() {
        return promos;
    }

    public void start(String uid) {
        if (uid == null || uid.equals(this.uid)) {
            return;
        }
        stop();
        this.uid = uid;
        promos.setValue(UiState.loading());
        registration = repository.observeMine(uid, new Callback<List<Promo>>() {
            @Override
            public void onSuccess(List<Promo> result) {
                promos.setValue(result == null || result.isEmpty()
                        ? UiState.empty() : UiState.success(result));
            }

            @Override
            public void onError(Exception e) {
                promos.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        uid = null;
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
