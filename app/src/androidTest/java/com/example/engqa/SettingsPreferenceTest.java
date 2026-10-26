package com.example.engqa;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.engqa.util.PrefsHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Deterministic UI test for the new Settings screen (course 9.2 / 6.1). */
@RunWith(AndroidJUnit4.class)
public class SettingsPreferenceTest {

    @Test
    public void settingsShowsNotificationPreference() {
        try (ActivityScenario<SettingsActivity> ignored =
                     ActivityScenario.launch(SettingsActivity.class)) {
            onView(withText(R.string.settings_notifications))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void notificationPreferencePersistsInSharedPreferences() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        PrefsHelper.setNotificationsEnabled(context, false);
        assertFalse(PrefsHelper.areNotificationsEnabled(context));

        PrefsHelper.setNotificationsEnabled(context, true);
        assertTrue(PrefsHelper.areNotificationsEnabled(context));
    }
}
