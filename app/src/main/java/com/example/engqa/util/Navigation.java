package com.example.engqa.util;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.engqa.HomeActivity;
import com.example.engqa.R;

/**
 * Central navigation rules for the app.
 *
 * <p>Two behaviours, matching how mainstream apps treat Up vs. Back:
 * <ul>
 *   <li>{@link #wireBack(AppCompatActivity)} – used by every nested screen (question detail,
 *       settings, edit profile, account security, ...). Back returns to the screen that opened it,
 *       so Settings → Edit profile → Back lands on Settings. Only when a screen was opened without
 *       a back stack (deep link, notification) does it synthesise Home.</li>
 *   <li>{@link #goHome(Activity)} – used by the tab screens, where Back always means "go to the
 *       Home tab".</li>
 * </ul>
 */
public final class Navigation {

    private Navigation() {
    }

    /**
     * Standard Up/Back for nested screens: finish this screen and reveal the previous one. If the
     * screen is the task root (deep link), open Home first so the user never lands on a blank task.
     */
    public static void wireBack(final AppCompatActivity activity) {
        View back = activity.findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(v -> goBack(activity));
        }
        activity.getOnBackPressedDispatcher().addCallback(activity,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        goBack(activity);
                    }
                });
    }

    private static void goBack(Activity activity) {
        if (activity.isTaskRoot()) {
            Intent intent = new Intent(activity, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        }
        activity.finish();
    }

    /** Clears everything above Home and finishes the calling screen (tab navigation). */
    public static void goHome(Activity activity) {
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
        activity.finish();
    }

    /** Toolbar/system Back for screens that are tabs rather than a nested detail screen. */
    public static void wireBackToHome(final AppCompatActivity activity) {
        View back = activity.findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(v -> goHome(activity));
        }
        activity.getOnBackPressedDispatcher().addCallback(activity,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        goHome(activity);
                    }
                });
    }
}
