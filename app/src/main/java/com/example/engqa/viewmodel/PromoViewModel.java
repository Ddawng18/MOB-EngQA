package com.example.engqa.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.engqa.data.Callback;
import com.example.engqa.data.PromoRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.local.SavedPromo;
import com.example.engqa.data.local.SavedPromoRepository;
import com.example.engqa.model.Lead;
import com.example.engqa.model.Promo;
import com.example.engqa.model.UiState;
import com.example.engqa.util.SingleLiveEvent;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Backs the promo detail screen.
 *
 * <p>{@code viewCounted} lives in the ViewModel, so opening the screen bumps {@code viewCount}
 * exactly once even across a rotation. {@code clickCount} is bumped per tap on call/website. The
 * local "saved" flag also lives here (Room), so the screen never talks to a repository directly.
 */
public class PromoViewModel extends AndroidViewModel {

    private final PromoRepository repository = new PromoRepository();
    private final SavedPromoRepository savedRepository;

    private final MutableLiveData<UiState<Promo>> promo = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> actionState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>(false);
    private final SingleLiveEvent<String> saveEvent = new SingleLiveEvent<>();

    private ListenerRegistration registration;
    private String promoId;
    private boolean viewCounted;
    private boolean saving;

    public PromoViewModel(@NonNull Application application) {
        super(application);
        savedRepository = new SavedPromoRepository(application);
    }

    public LiveData<UiState<Promo>> getPromo() {
        return promo;
    }

    public LiveData<UiState<String>> getActionState() {
        return actionState;
    }

    public LiveData<Boolean> getSaved() {
        return saved;
    }

    /** One-shot "saved" / "removed" / error message for the screen to toast. */
    public LiveData<String> getSaveEvent() {
        return saveEvent;
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
            saved.setValue(false);
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

    /** Re-reads the Room flag after returning to the screen. */
    public void refreshSaved() {
        if (promoId == null) {
            saved.setValue(false);
            return;
        }
        savedRepository.isSaved(promoId, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                saved.setValue(Boolean.TRUE.equals(result));
            }

            @Override
            public void onError(Exception e) {
                // Keep the current value.
            }
        });
    }

    /** Saves or removes the promo locally and reports the outcome as a one-shot event. */
    public void toggleSaved(Promo currentPromo) {
        if (saving || promoId == null || currentPromo == null) {
            return;
        }
        saving = true;
        if (Boolean.TRUE.equals(saved.getValue())) {
            savedRepository.remove(promoId, new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    saving = false;
                    saved.setValue(false);
                    saveEvent.setValue("removed");
                }

                @Override
                public void onError(Exception e) {
                    saving = false;
                    saveEvent.setValue(RepoErrors.message(e));
                }
            });
        } else {
            SavedPromo entity = new SavedPromo(promoId, currentPromo.getCenterName(),
                    currentPromo.getTitle(), currentPromo.getImageUrl(),
                    System.currentTimeMillis());
            savedRepository.save(entity, new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    saving = false;
                    saved.setValue(true);
                    saveEvent.setValue("saved");
                }

                @Override
                public void onError(Exception e) {
                    saving = false;
                    saveEvent.setValue(RepoErrors.message(e));
                }
            });
        }
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
