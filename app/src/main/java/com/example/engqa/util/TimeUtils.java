package com.example.engqa.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Formats Firestore timestamps as short relative labels such as "2 giờ trước". */
public final class TimeUtils {

    private TimeUtils() {
    }

    public static String relative(Date date) {
        if (date == null) {
            return "";
        }
        long diff = System.currentTimeMillis() - date.getTime();
        if (diff < 0) {
            diff = 0;
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        if (minutes < 1) {
            return "Vừa xong";
        }
        if (minutes < 60) {
            return minutes + " phút trước";
        }
        long hours = TimeUnit.MINUTES.toHours(minutes);
        if (hours < 24) {
            return hours + " giờ trước";
        }
        long days = TimeUnit.HOURS.toDays(hours);
        if (days < 7) {
            return days + " ngày trước";
        }
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date);
    }
}
