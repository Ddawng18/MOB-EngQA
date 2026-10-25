package com.example.engqa;

import android.content.Context;

import androidx.work.WorkManager;

import com.example.engqa.notification.ModerationBackfill;
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

    /** Email of the signed-in account, used by the account-security screens. */
    public String getEmail() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getEmail() : null;
    }

    /** Call after a successful sign-in so notifications start arriving immediately. */
    public void onLoginSuccess() {
        String uid = getUserId();
        if (uid == null) {
            return;
        }
        if (PrefsHelper.areNotificationsEnabled(appContext)) {
            NotificationWatcher.start(uid);
        }
        ModerationBackfill.run(appContext, uid);
    }

    public void logout() {
        NotificationWatcher.stop();
        // Queued posts are scoped to the account that created them; do not let them run (or be
        // published) after a different account signs in on the same device.
        WorkManager.getInstance(appContext).cancelAllWorkByTag("pending-question");
        auth.signOut();
    }
}
