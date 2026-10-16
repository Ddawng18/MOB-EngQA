package com.example.engqa;

import android.content.Context;

import com.example.engqa.notification.NotificationWatcher;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Remembers which user is logged in via Firebase Auth and drives the notification watcher. */
public class SessionManager {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public SessionManager(Context context) {
        // Context kept for API symmetry with the original class; FirebaseAuth is a singleton.
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
        if (uid != null) {
            NotificationWatcher.start(uid);
        }
    }

    public void logout() {
        NotificationWatcher.stop();
        auth.signOut();
    }
}
