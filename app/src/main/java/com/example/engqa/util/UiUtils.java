package com.example.engqa.util;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import java.util.Date;

/** Small UI helpers shared by the promo screens. */
public final class UiUtils {

    private UiUtils() {
    }

    /** Relative label ("2 giờ trước"); delegates to {@link TimeUtils} so tests stay in one place. */
    public static String relativeTime(Date date) {
        return TimeUtils.relative(date);
    }

    public static void toast(Context context, String message) {
        if (context != null && message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void toast(View view, String message) {
        if (view != null) {
            toast(view.getContext(), message);
        }
    }

    public static void show(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}
