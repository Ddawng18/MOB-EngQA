package com.example.engqa.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.engqa.data.Callback;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.ReportRepository;
import com.example.engqa.data.UserRepository;
import com.example.engqa.model.Report;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;

/** Backs the "report content" dialog; the fragment never touches a repository directly. */
public class ReportViewModel extends AndroidViewModel {

    private final UserRepository userRepository = new UserRepository();
    private final ReportRepository reportRepository = new ReportRepository();

    private final MutableLiveData<UiState<String>> reportState = new MutableLiveData<>();

    public ReportViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<UiState<String>> getReportState() {
        return reportState;
    }

    /** Clears the previous result when the dialog is opened again. */
    public void reset() {
        reportState.setValue(null);
    }

    public void submit(String targetPath, String reason) {
        final String uid = userRepository.currentUid();
        if (uid == null) {
            reportState.setValue(UiState.error("Bạn cần đăng nhập"));
            return;
        }
        reportState.setValue(UiState.<String>loading());
        userRepository.getUser(uid, new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                String name = user != null && user.getUsername() != null
                        ? user.getUsername() : "user";
                send(targetPath, reason, uid, name);
            }

            @Override
            public void onError(Exception e) {
                send(targetPath, reason, uid, "user");
            }
        });
    }

    private void send(String targetPath, String reason, String uid, String name) {
        Report report = new Report(targetPath, uid, name, reason);
        reportRepository.report(report, new Callback<String>() {
            @Override
            public void onSuccess(String reportId) {
                reportState.setValue(UiState.success(reportId));
            }

            @Override
            public void onError(Exception e) {
                reportState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }
}
