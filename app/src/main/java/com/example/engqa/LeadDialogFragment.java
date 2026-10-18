package com.example.engqa;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.util.PrefsHelper;
import com.example.engqa.util.PromoValidator;
import com.example.engqa.viewmodel.PromoViewModel;

/**
 * Consultation dialog: asks for name, phone and an optional note, prefilled from the details the
 * learner used last time. Submits through the screen's {@link PromoViewModel} so the promo detail
 * keeps ownership of the data layer.
 */
public class LeadDialogFragment extends DialogFragment {

    private static final String ARG_PROMO_ID = "promoId";
    private static final String ARG_UID = "uid";

    public static LeadDialogFragment newInstance(String promoId, String uid) {
        LeadDialogFragment fragment = new LeadDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PROMO_ID, promoId);
        args.putString(ARG_UID, uid);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_lead, null, false);

        EditText name = view.findViewById(R.id.etLeadName);
        EditText phone = view.findViewById(R.id.etLeadPhone);
        EditText note = view.findViewById(R.id.etLeadNote);

        name.setText(PrefsHelper.getLeadName(requireContext()));
        phone.setText(PrefsHelper.getLeadPhone(requireContext()));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.lead_dialog_title)
                .setView(view)
                .setPositiveButton(R.string.lead_submit, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        // Override the click so the dialog stays open when validation fails.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String nameValue = name.getText().toString().trim();
                    String phoneValue = phone.getText().toString().trim();
                    if (nameValue.isEmpty()) {
                        name.setError(getString(R.string.lead_name_required));
                        name.requestFocus();
                        return;
                    }
                    String phoneError = PromoValidator.phoneError(phoneValue);
                    if (phoneError != null) {
                        phone.setError(phoneError);
                        phone.requestFocus();
                        return;
                    }
                    submit(nameValue, phoneValue, note.getText().toString().trim());
                    dialog.dismiss();
                }));
        return dialog;
    }

    private void submit(String name, String phone, String note) {
        String promoId = getArguments() != null
                ? getArguments().getString(ARG_PROMO_ID) : null;
        String uid = getArguments() != null ? getArguments().getString(ARG_UID) : null;
        if (promoId == null) {
            return;
        }
        PrefsHelper.rememberLead(requireContext(), name, phone);

        PromoViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(PromoViewModel.class);
        viewModel.submitLead(uid, name, phone, note);
    }
}
