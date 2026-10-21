package com.example.engqa;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.DatePicker;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import java.util.Calendar;
import java.util.Date;

/** Date picker used for the promo's {@code endAt} deadline. */
public class DatePickerFragment extends DialogFragment
        implements DatePickerDialog.OnDateSetListener {

    /** Implemented by the hosting Activity. */
    public interface OnDateSetListener {
        void onDateSet(int year, int month, int dayOfMonth);
    }

    private static final String ARG_INITIAL_MILLIS = "initialMillis";

    public static DatePickerFragment newInstance(Date initial) {
        DatePickerFragment fragment = new DatePickerFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_INITIAL_MILLIS, initial != null
                ? initial.getTime() : System.currentTimeMillis());
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Calendar calendar = Calendar.getInstance();
        if (getArguments() != null && getArguments().containsKey(ARG_INITIAL_MILLIS)) {
            calendar.setTimeInMillis(getArguments().getLong(ARG_INITIAL_MILLIS));
        }
        DatePickerDialog dialog = new DatePickerDialog(requireContext(), this,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        return dialog;
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        if (getActivity() instanceof OnDateSetListener) {
            ((OnDateSetListener) getActivity()).onDateSet(year, month, dayOfMonth);
        }
    }
}
