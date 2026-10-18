package com.example.engqa.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.Callback;
import com.example.engqa.data.PromoRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.model.Lead;
import com.example.engqa.model.Promo;
import com.example.engqa.model.UiState;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Backs the promo detail screen.
 *
 * <p>{@code viewCounted} lives in the ViewModel, so opening the screen bumps {@code viewCount}
 * exactly once even across a rotation. {@code clickCount} is bumped per tap on call/website.
 */
public class PromoViewModel extends ViewModel {

    private final PromoRepository repository = new PromoRepository();

    private final MutableLiveData<UiState<Promo>> promo = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> actionState = new MutableLiveData<>();

    private ListenerRegistration registration;
    private String promoId;
    private boolean viewCounted;

    public LiveData<UiState<Promo>> getPromo() {
        return promo;
    }

    public LiveData<UiState<String>> getActionState() {
        return actionState;
    }

    public String getPromoId() {
        return promoId;
    }

    public void start(String newPromoId) {
        if (newPromoId == null) {
            return;
        }
        if (newPromoId.equals(promoId) && registration != null) {
            return;
        }
        if (!newPromoId.equals(promoId)) {
            viewCounted = false;
        }
        promoId = newPromoId;
        attach();
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    @Override
    protected void onCleared() {
        stop();
        super.onCleared();
    }

    /** Counts one view the first time the detail is shown; later rotations are ignored. */
    public void onOpened() {
        if (viewCounted || promoId == null) {
            return;
        }
        viewCounted = true;
        repository.bump(promoId, "viewCount", noopCallback());
    }

    public void onContactClick() {
        if (promoId == null) {
            return;
        }
        repository.bump(promoId, "clickCount", noopCallback());
    }

    public void submitLead(String uid, String name, String phone, String note) {
        if (promoId == null) {
            return;
        }
        if (uid == null) {
            actionState.setValue(UiState.error("Bạn cần đăng nhập để đăng ký tư vấn"));
            return;
        }
        actionState.setValue(UiState.<String>loading());
        Lead lead = new Lead(uid, name, phone, note);
        repository.submitLead(promoId, lead, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success("lead_submitted"));
            }

            @Override
            public void onError(Exception e) {
                if (e instanceof PromoRepository.LeadExistsException) {
                    actionState.setValue(UiState.error("Bạn đã đăng ký bài này rồi"));
                } else {
                    actionState.setValue(UiState.error(RepoErrors.message(e)));
                }
            }
        });
    }

    private void attach() {
        promo.setValue(UiState.<Promo>loading());
        registration = repository.observeById(promoId, new Callback<Promo>() {
            @Override
            public void onSuccess(Promo result) {
                if (result == null) {
                    promo.setValue(UiState.<Promo>error("Không tìm thấy bài đăng"));
                } else {
                    promo.setValue(UiState.success(result));
                    onOpened();
                }
            }

            @Override
            public void onError(Exception e) {
                promo.setValue(UiState.<Promo>error(RepoErrors.message(e)));
            }
        });
    }

    private Callback<Void> noopCallback() {
        return new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
            }

            @Override
            public void onError(Exception e) {
            }
        };
    }
}
