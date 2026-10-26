package com.example.engqa;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.firebase.auth.FirebaseAuth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented UI tests for the login form. They only exercise local validation, so no network or
 * seeded data is required.
 */
@RunWith(AndroidJUnit4.class)
public class LoginValidationTest {

    @Before
    public void signOut() {
        // A cached session would send LoginActivity straight to Home.
        FirebaseAuth.getInstance().signOut();
    }

    @Test
    public void emptyFields_showEmailValidationError() {
        try (ActivityScenario<LoginActivity> ignored =
                     ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.btnLogin)).perform(click());
            onView(withId(R.id.tvAuthError))
                    .check(matches(isDisplayed()))
                    .check(matches(withText("Vui lòng nhập email")));
        }
    }

    @Test
    public void invalidEmail_showsInvalidEmailError() {
        try (ActivityScenario<LoginActivity> ignored =
                     ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.edtEmail))
                    .perform(typeText("not-an-email"), closeSoftKeyboard());
            onView(withId(R.id.edtPassword))
                    .perform(typeText("123456"), closeSoftKeyboard());
            onView(withId(R.id.btnLogin)).perform(click());
            onView(withId(R.id.tvAuthError))
                    .check(matches(withText("Email không hợp lệ")));
        }
    }
}
