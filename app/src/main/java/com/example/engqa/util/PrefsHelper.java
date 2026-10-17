package com.example.engqa.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small wrapper around the app's {@link SharedPreferences}: remembers the selected feed chip and
 * the learner's name/phone so the consultation dialog can prefill them (course 9.1).
 */
public final class PrefsHelper {

    private static final String PREFS = "engqa_prefs";
    private static final String KEY_FILTER = "feed_filter";
    private static final String KEY_LEAD_NAME = "lead_name";
    private static final String KEY_LEAD_PHONE = "lead_phone";

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
}
