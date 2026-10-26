package com.example.engqa.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.engqa.CenterDetailActivity;
import com.example.engqa.NotificationActivity;
import com.example.engqa.PromoDetailActivity;
import com.example.engqa.QuestionDetailActivity;
import com.example.engqa.R;
import com.example.engqa.model.AppNotification;
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
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        show(context, id, title, body, intent);
    }

    /** Routes moderation notifications to the related question, promo or center. */
    public static void show(Context context, int id, AppNotification notification) {
        Intent intent;
        if (notification.getQuestionId() != null) {
            intent = new Intent(context, QuestionDetailActivity.class);
            intent.putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID,
                    notification.getQuestionId());
            if (notification.getAnswerId() != null) {
                intent.putExtra(QuestionDetailActivity.EXTRA_FOCUS_ANSWER_ID,
                        notification.getAnswerId());
            }
        } else if (notification.getPromoId() != null) {
            intent = new Intent(context, PromoDetailActivity.class);
            intent.putExtra(PromoDetailActivity.EXTRA_PROMO_ID, notification.getPromoId());
        } else if (notification.getCenterId() != null) {
            intent = new Intent(context, CenterDetailActivity.class);
            intent.putExtra(CenterDetailActivity.EXTRA_CENTER_ID, notification.getCenterId());
        } else {
            intent = new Intent(context, NotificationActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        show(context, id, notification.getTitle(), notification.getBody(), intent);
    }

    /** Local reminder for a promo deadline (AlarmManager lab), independent of Firestore. */
    public static void showPromoReminder(Context context, String promoId, String promoTitle) {
        Intent intent = new Intent(context, PromoDetailActivity.class);
        intent.putExtra(PromoDetailActivity.EXTRA_PROMO_ID, promoId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        String title = context.getString(R.string.promo_reminder_title);
        String body = context.getString(R.string.promo_reminder_body,
                promoTitle == null || promoTitle.isEmpty()
                        ? context.getString(R.string.promo_reminder_fallback) : promoTitle);
        show(context, promoId.hashCode(), title, body, intent);
    }

    private static void show(Context context, int id, String title, String body, Intent intent) {
        if (!PrefsHelper.areNotificationsEnabled(context)) {
            return;
        }
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
