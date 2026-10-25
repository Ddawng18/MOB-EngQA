package com.example.engqa.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.engqa.QuestionDetailActivity;
import com.example.engqa.R;
import com.example.engqa.util.PrefsHelper;

/** Creates the notification channel and posts in-app interaction notifications. */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "EngQA";

    private NotificationHelper() {
    }

    /** Must run once at app start; safe to call repeatedly. */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(context.getString(R.string.notification_channel_desc));
            manager.createNotificationChannel(channel);
        }
    }

    public static void show(Context context, int id, String title, String body, String questionId) {
        Intent intent = new Intent(context, QuestionDetailActivity.class);
        intent.putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID, questionId);
        show(context, id, title, body, intent);
    }

    /** Same as above but with an explicit destination (used by the moderation watcher). */
    public static void show(Context context, int id, String title, String body, Intent intent) {
        if (!PrefsHelper.areNotificationsEnabled(context)) {
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent =
                PendingIntent.getActivity(context, id, intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS not granted yet.
        }
    }
}
