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
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.model.UiState;
import com.example.engqa.viewmodel.ReportViewModel;

/** Collects a reason and sends it through {@link ReportViewModel}. */
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

        ReportViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(ReportViewModel.class);
        viewModel.reset();

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.report_title)
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        // Keep the dialog open while the request runs so errors can be corrected.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = reason.getText().toString().trim();
                    if (text.isEmpty()) {
                        reason.setError(getString(R.string.report_reason_hint));
                        reason.requestFocus();
                        return;
                    }
                    String targetPath = getArguments() != null
                            ? getArguments().getString(ARG_TARGET_PATH) : null;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    viewModel.submit(targetPath, text);
                }));

        viewModel.getReportState().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == UiState.Status.LOADING) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            } else if (state.getStatus() == UiState.Status.SUCCESS) {
                Toast.makeText(requireContext(), R.string.report_sent, Toast.LENGTH_SHORT).show();
                dismiss();
            } else if (state.getStatus() == UiState.Status.ERROR) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        return dialog;
    }
}
