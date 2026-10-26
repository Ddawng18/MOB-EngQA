package com.example.engqa;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies the two moderation terminal states render the exact required copy. */
@RunWith(AndroidJUnit4.class)
public class AccountBlockedTest {

    @Test
    public void timeoutScreenShowsRequiredMessage() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AccountBlockedActivity.class)
                .putExtra(AccountBlockedActivity.EXTRA_MODE,
                        AccountBlockedActivity.MODE_TIMEOUT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<AccountBlockedActivity> ignored =
                     ActivityScenario.launch(intent)) {
            onView(withText("You got timeout, better behave next time ok? OR ELSE"))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void deletedScreenShowsDeletedMessage() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AccountBlockedActivity.class)
                .putExtra(AccountBlockedActivity.EXTRA_MODE,
                        AccountBlockedActivity.MODE_DELETED)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<AccountBlockedActivity> ignored =
                     ActivityScenario.launch(intent)) {
            onView(withText(R.string.blocked_deleted_message))
                    .check(matches(isDisplayed()));
        }
    }
}
