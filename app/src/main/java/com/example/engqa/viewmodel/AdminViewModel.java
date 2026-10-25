package com.example.engqa.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.AdminRepository;
import com.example.engqa.data.Callback;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.model.Center;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Report;
import com.example.engqa.model.UiState;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/** State and actions for the moderation console. */
public class AdminViewModel extends ViewModel {

    private final AdminRepository repository = new AdminRepository();

    private final MutableLiveData<Boolean> isAdmin = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Center>>> centers = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Promo>>> promos = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Report>>> reports = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> actionState = new MutableLiveData<>();

    private ListenerRegistration centersRegistration;
    private ListenerRegistration promosRegistration;
    private ListenerRegistration reportsRegistration;

    public LiveData<Boolean> getIsAdmin() {
        return isAdmin;
    }

    public LiveData<UiState<List<Center>>> getCenters() {
        return centers;
    }

    public LiveData<UiState<List<Promo>>> getPromos() {
        return promos;
    }

    public LiveData<UiState<List<Report>>> getReports() {
        return reports;
    }

    public LiveData<UiState<String>> getActionState() {
        return actionState;
    }

    public void checkAdmin() {
        repository.isAdmin(new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                isAdmin.setValue(Boolean.TRUE.equals(result));
            }

            @Override
            public void onError(Exception e) {
                isAdmin.setValue(false);
            }
        });
    }

    public void start() {
        if (centersRegistration != null) {
            return;
        }
        centers.setValue(UiState.loading());
        promos.setValue(UiState.loading());
        reports.setValue(UiState.loading());

        centersRegistration = repository.observePendingCenters(listCallback(centers));
        promosRegistration = repository.observePendingPromos(listCallback(promos));
        reportsRegistration = repository.observePendingReports(listCallback(reports));
    }

    public void stop() {
        if (centersRegistration != null) {
            centersRegistration.remove();
            centersRegistration = null;
        }
        if (promosRegistration != null) {
            promosRegistration.remove();
            promosRegistration = null;
        }
        if (reportsRegistration != null) {
            reportsRegistration.remove();
            reportsRegistration = null;
        }
    }

    public void approveCenter(Center center) {
        act(callback -> repository.approveCenter(center, callback), "center_approved");
    }

    public void rejectCenter(Center center, String note) {
        act(callback -> repository.rejectCenter(center, note, callback), "center_rejected");
    }

    public void approvePromo(Promo promo) {
        act(callback -> repository.approvePromo(promo, callback), "promo_approved");
    }

    public void rejectPromo(Promo promo, String note) {
        act(callback -> repository.rejectPromo(promo, note, callback), "promo_rejected");
    }

    public void warnReport(Report report) {
        act(callback -> repository.warnReport(report, callback), "report_warned");
    }

    public void deleteReportedPost(Report report) {
        act(callback -> repository.deleteReportedPost(report, callback), "report_deleted");
    }

    public void dismissReport(Report report, String note) {
        act(callback -> repository.dismissReport(report, note, callback), "report_dismissed");
    }

    private interface Action {
        void run(Callback<Void> callback);
    }

    private void act(Action action, String successCode) {
        action.run(new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success(successCode));
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    private <T> Callback<List<T>> listCallback(MutableLiveData<UiState<List<T>>> target) {
        return new Callback<List<T>>() {
            @Override
            public void onSuccess(List<T> result) {
                target.setValue(UiState.success(result));
            }

            @Override
            public void onError(Exception e) {
                target.setValue(UiState.error(RepoErrors.message(e)));
            }
        };
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
