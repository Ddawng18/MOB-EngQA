package com.example.engqa;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.containsString;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.engqa.util.ModerationPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies the two moderation terminal states render the required moderation copy. */
@RunWith(AndroidJUnit4.class)
public class AccountBlockedTest {

    @Test
    public void timeoutScreenShowsRemainingTimeoutProgress() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AccountBlockedActivity.class)
                .putExtra(AccountBlockedActivity.EXTRA_MODE,
                        AccountBlockedActivity.MODE_TIMEOUT)
                .putExtra(AccountBlockedActivity.EXTRA_TIMEOUT_COUNT, 2L)
                .putExtra(AccountBlockedActivity.EXTRA_WARNING_COUNT, 0L)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<AccountBlockedActivity> ignored =
                     ActivityScenario.launch(intent)) {
            onView(withText(containsString("còn 1 lần timeout nữa trước khi tài khoản bị xoá")))
                    .check(matches(isDisplayed()));
            onView(withText(containsString("Bộ đếm cảnh cáo hiện tại: 0/" +
                    ModerationPolicy.WARNINGS_PER_TIMEOUT)))
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
