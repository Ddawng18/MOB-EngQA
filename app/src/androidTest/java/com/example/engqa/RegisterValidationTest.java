package com.example.engqa;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test for the registration form. The short display name fails local validation before
 * any network call, so the test is deterministic and needs no Firebase data.
 */
@RunWith(AndroidJUnit4.class)
public class RegisterValidationTest {

    @Test
    public void shortUsername_showsUsernameValidationError() {
        try (ActivityScenario<RegisterActivity> ignored =
                     ActivityScenario.launch(RegisterActivity.class)) {
            onView(withId(R.id.edtUsername))
                    .perform(typeText("ab"), closeSoftKeyboard());
            onView(withId(R.id.edtEmail))
                    .perform(typeText("test@example.com"), closeSoftKeyboard());
            onView(withId(R.id.edtPassword))
                    .perform(typeText("123456"), closeSoftKeyboard());
            onView(withId(R.id.edtConfirmPassword))
                    .perform(scrollTo(), typeText("123456"), closeSoftKeyboard());
            // Landscape has less vertical space; scroll the form instead of relying on a single
            // scrollTo (which can run while the IME is still settling).
            onView(withId(R.id.registerRoot)).perform(swipeUp(), swipeUp());
            onView(withId(R.id.btnRegister)).perform(click());
            onView(withId(R.id.tvAuthError))
                    .check(matches(isDisplayed()))
                    .check(matches(withText("Tên hiển thị phải từ 3 đến 24 ký tự")));
        }
    }
}
