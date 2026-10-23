package com.example.engqa;

import android.content.Context;

import com.example.engqa.notification.NotificationWatcher;
import com.example.engqa.util.PrefsHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Remembers which user is logged in via Firebase Auth and drives the notification watcher. */
public class SessionManager {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final android.content.Context appContext;

    public SessionManager(android.content.Context context) {
        this.appContext = context.getApplicationContext();
    }

    public String getUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    /** Call after a successful sign-in so notifications start arriving immediately. */
    public void onLoginSuccess() {
        String uid = getUserId();
        if (uid != null && PrefsHelper.areNotificationsEnabled(appContext)) {
            NotificationWatcher.start(uid);
        }
    }

    public void logout() {
        NotificationWatcher.stop();
        auth.signOut();
    }
}
