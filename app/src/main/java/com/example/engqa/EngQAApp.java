package com.example.engqa;

import android.app.Application;
import android.content.Context;

import com.example.engqa.notification.NotificationHelper;
import com.example.engqa.notification.NotificationWatcher;
import com.example.engqa.worker.WorkScheduler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

/**
 * Application entry point: turns on Firestore offline persistence, creates the notification channel
 * and starts watching for the signed-in user's notifications.
 */
public class EngQAApp extends Application {

    private static EngQAApp instance;

    public static Context getContext() {
        return instance.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Keep working offline and show cached data while the network is unavailable.
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        firestore.setFirestoreSettings(settings);

        NotificationHelper.createChannel(this);
        WorkScheduler.scheduleCacheCleanup(this);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            NotificationWatcher.start(user.getUid());
        }
    }
}
