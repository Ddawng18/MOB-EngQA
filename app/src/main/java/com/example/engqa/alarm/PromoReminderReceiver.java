package com.example.engqa.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.engqa.notification.NotificationHelper;

/** Handles the inexact promo-deadline alarm and posts a local notification. */
public class PromoReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !PromoReminderScheduler.ACTION_REMIND.equals(intent.getAction())) {
            return;
        }
        String promoId = intent.getStringExtra(PromoReminderScheduler.EXTRA_PROMO_ID);
        String title = intent.getStringExtra(PromoReminderScheduler.EXTRA_TITLE);
        long endAt = intent.getLongExtra(PromoReminderScheduler.EXTRA_END_AT, 0L);
        if (promoId == null || endAt <= System.currentTimeMillis()) {
            return;
        }
        NotificationHelper.showPromoReminder(context, promoId, title);
    }
}
