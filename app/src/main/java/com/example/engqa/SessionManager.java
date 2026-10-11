package com.example.engqa;

import android.content.Context;
import android.content.SharedPreferences;

/** Remembers which user is logged in across app launches. */
public class SessionManager {

    private static final String PREFS = "engqa_session";
    private static final String KEY_USER_ID = "user_id";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void login(long userId) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply();
    }

    public void logout() {
        prefs.edit().remove(KEY_USER_ID).apply();
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public boolean isLoggedIn() {
        return getUserId() != -1;
    }
}