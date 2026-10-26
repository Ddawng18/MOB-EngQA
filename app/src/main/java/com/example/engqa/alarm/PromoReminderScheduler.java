package com.example.engqa.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.engqa.model.Promo;

/**
 * Schedules a one-shot local reminder one day before an approved promo ends. The alarm is
 * deliberately inexact ({@code setWindow}) so it works without SCHEDULE_EXACT_ALARM and is kind to
 * the battery. The pending promo is also stored in SharedPreferences so {@link BootReceiver} can
 * restore it after a reboot.
 */
public final class PromoReminderScheduler {

    public static final String ACTION_REMIND = "com.example.engqa.action.PROMO_REMIND";
    public static final String EXTRA_PROMO_ID = "promo_id";
    public static final String EXTRA_TITLE = "promo_title";
    public static final String EXTRA_END_AT = "promo_end_at";

    private static final String PREFS = "promo_reminders";
    private static final long WINDOW_MS = 15L * 60 * 1000;
    private static final long LEAD_MS = 24L * 60 * 60 * 1000;

    private PromoReminderScheduler() {
    }

    public static void schedule(Context context, Promo promo) {
        if (context == null || promo == null || promo.getPromoId() == null
                || promo.getEndAt() == null) {
            return;
        }
        long triggerAt = promo.getEndAt().getTime() - LEAD_MS;
        if (triggerAt <= System.currentTimeMillis()) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putLong(promo.getPromoId() + "_end", promo.getEndAt().getTime())
                .putString(promo.getPromoId() + "_title",
                        promo.getTitle() != null ? promo.getTitle() : "")
                .apply();
        scheduleAt(context, promo.getPromoId(),
                promo.getTitle() != null ? promo.getTitle() : "", triggerAt);
    }

    /** Called by BootReceiver; SharedPreferences survive the reboot. */
    public static void rescheduleAll(Context context) {
        SharedPreferences prefs = prefs(context);
        long now = System.currentTimeMillis();
        for (String key : prefs.getAll().keySet()) {
            if (!key.endsWith("_end")) {
                continue;
            }
            String promoId = key.substring(0, key.length() - "_end".length());
            long endAt = prefs.getLong(key, 0L);
            if (endAt <= now) {
                prefs.edit().remove(key).remove(promoId + "_title").apply();
                continue;
            }
            long triggerAt = endAt - LEAD_MS;
            if (triggerAt > now) {
                scheduleAt(context, promoId,
                        prefs.getString(promoId + "_title", ""), triggerAt);
            }
        }
    }

    public static void cancel(Context context, String promoId) {
        if (context == null || promoId == null) {
            return;
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) {
            manager.cancel(pendingIntent(context, promoId, "", 0L));
        }
        prefs(context).edit().remove(promoId + "_end")
                .remove(promoId + "_title").apply();
    }

    private static void scheduleAt(Context context, String promoId, String title, long triggerAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        long endAt = prefs(context).getLong(promoId + "_end", 0L);
        PendingIntent pending = pendingIntent(context, promoId, title, endAt);
        manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MS, pending);
    }

    private static PendingIntent pendingIntent(Context context, String promoId, String title,
                                               long endAt) {
        Intent intent = new Intent(context, PromoReminderReceiver.class)
                .setAction(ACTION_REMIND)
                .putExtra(EXTRA_PROMO_ID, promoId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_END_AT, endAt);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, promoId.hashCode(), intent, flags);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
