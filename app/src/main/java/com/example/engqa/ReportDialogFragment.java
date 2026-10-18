package com.example.engqa;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.engqa.data.Callback;
import com.example.engqa.data.ReportRepository;
import com.example.engqa.data.UserRepository;
import com.example.engqa.model.Report;
import com.example.engqa.model.User;

/** Collects a reason and writes a {@code reports/{id}} document for a question or answer. */
public class ReportDialogFragment extends DialogFragment {

    private static final String ARG_TARGET_PATH = "targetPath";

    public static ReportDialogFragment newInstance(String targetPath) {
        ReportDialogFragment fragment = new ReportDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TARGET_PATH, targetPath);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_report, null, false);
        EditText reason = view.findViewById(R.id.etReportReason);

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.report_title)
                .setView(view)
                .setPositiveButton(R.string.ok, (dialog, which) -> submit(reason.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    private void submit(String reasonText) {
        String targetPath = getArguments() != null
                ? getArguments().getString(ARG_TARGET_PATH) : null;
        if (reasonText == null || reasonText.trim().isEmpty()) {
            Toast.makeText(requireContext(), R.string.report_reason_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = new UserRepository().currentUid();
        if (uid == null) {
            return;
        }
        new UserRepository().getUser(uid, new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                String name = user != null ? user.getUsername() : "user";
                Report report = new Report(targetPath, uid, name, reasonText.trim());
                new ReportRepository().report(report, new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Toast.makeText(requireContext(), R.string.report_sent,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(requireContext(), e.getLocalizedMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Report report = new Report(targetPath, uid, "user", reasonText.trim());
                new ReportRepository().report(report, null);
            }
        });
    }
}
