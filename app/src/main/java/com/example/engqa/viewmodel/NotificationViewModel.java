package com.example.engqa.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.Callback;
import com.example.engqa.data.NotificationRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.model.AppNotification;
import com.example.engqa.model.UiState;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/** Backs the notification list screen. */
public class NotificationViewModel extends ViewModel {

    private final NotificationRepository notificationRepository = new NotificationRepository();

    private final MutableLiveData<UiState<List<AppNotification>>> notifications =
            new MutableLiveData<>();

    private ListenerRegistration registration;
    private String uid;

    public LiveData<UiState<List<AppNotification>>> getNotifications() {
        return notifications;
    }

    public void start(String uid) {
        if (this.uid != null && this.uid.equals(uid) && registration != null) {
            return;
        }
        this.uid = uid;
        notifications.setValue(UiState.<List<AppNotification>>loading());
        registration = notificationRepository.observeMine(uid,
                new Callback<List<AppNotification>>() {
                    @Override
                    public void onSuccess(List<AppNotification> result) {
                        notifications.setValue(result.isEmpty()
                                ? UiState.<List<AppNotification>>empty()
                                : UiState.success(result));
                    }

                    @Override
                    public void onError(Exception e) {
                        notifications.setValue(
                                UiState.<List<AppNotification>>error(RepoErrors.message(e)));
                    }
                });
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

    public void markRead(AppNotification notification) {
        if (notification.getId() != null) {
            notificationRepository.markRead(notification.getId(), new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                }

                @Override
                public void onError(Exception e) {
                }
            });
        }
    }

    public void markAllRead() {
        UiState<List<AppNotification>> state = notifications.getValue();
        if (state != null && state.getData() != null) {
            notificationRepository.markAllRead(state.getData(), new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                }

                @Override
                public void onError(Exception e) {
                }
            });
        }
    }
}
