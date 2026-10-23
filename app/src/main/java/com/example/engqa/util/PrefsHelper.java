package com.example.engqa.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small wrapper around the app's {@link SharedPreferences}: the feed chip, the learner's
 * name/phone for the consultation dialog (course 9.1), the notification preference and the cached
 * "Word of the Day" JSON.
 */
public final class PrefsHelper {

    private static final String PREFS = "engqa_prefs";
    private static final String KEY_FILTER = "feed_filter";
    private static final String KEY_LEAD_NAME = "lead_name";
    private static final String KEY_LEAD_PHONE = "lead_phone";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";
    private static final String KEY_NOTIFICATIONS_ASKED = "notifications_permission_asked";
    private static final String KEY_WORD_CACHE = "word_cache_json";

    private PrefsHelper() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getFilter(Context context) {
        return prefs(context).getString(KEY_FILTER, null);
    }

    public static void setFilter(Context context, String filter) {
        prefs(context).edit().putString(KEY_FILTER, filter).apply();
    }

    public static String getLeadName(Context context) {
        return prefs(context).getString(KEY_LEAD_NAME, "");
    }

    public static String getLeadPhone(Context context) {
        return prefs(context).getString(KEY_LEAD_PHONE, "");
    }

    public static void rememberLead(Context context, String name, String phone) {
        prefs(context).edit()
                .putString(KEY_LEAD_NAME, name != null ? name : "")
                .putString(KEY_LEAD_PHONE, phone != null ? phone : "")
                .apply();
    }

    // ------------------------------------------------------------------ notifications

    /** Defaults to on so a fresh install still shows interaction notifications. */
    public static boolean areNotificationsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NOTIFICATIONS, true);
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    /** Used to ask for POST_NOTIFICATIONS only once from the home screen. */
    public static boolean hasAskedNotifications(Context context) {
        return prefs(context).getBoolean(KEY_NOTIFICATIONS_ASKED, false);
    }

    public static void setAskedNotifications(Context context, boolean asked) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ASKED, asked).apply();
    }

    // ------------------------------------------------------------------ word cache

    public static String getWordCache(Context context) {
        return prefs(context).getString(KEY_WORD_CACHE, null);
    }

    public static void setWordCache(Context context, String json) {
        prefs(context).edit().putString(KEY_WORD_CACHE, json).apply();
    }
}
