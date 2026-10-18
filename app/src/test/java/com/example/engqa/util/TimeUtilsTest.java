package com.example.engqa.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

/** Unit tests for the relative-time labels used across the feed and detail screens. */
public class TimeUtilsTest {

    private Date ago(long millis) {
        return new Date(System.currentTimeMillis() - millis);
    }

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    @Test
    public void nullOrFuture_isHandled() {
        assertEquals("", TimeUtils.relative(null));
        assertEquals("Vừa xong", TimeUtils.relative(new Date(System.currentTimeMillis() + HOUR)));
    }

    @Test
    public void minutesHoursAndDays() {
        assertEquals("Vừa xong", TimeUtils.relative(ago(MINUTE / 2)));
        assertEquals("5 phút trước", TimeUtils.relative(ago(5 * MINUTE)));
        assertEquals("2 giờ trước", TimeUtils.relative(ago(2 * HOUR)));
        assertEquals("3 ngày trước", TimeUtils.relative(ago(3 * DAY)));
    }

    @Test
    public void olderThanAWeek_isFormattedAsDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -30);
        String value = TimeUtils.relative(calendar.getTime());
        assertEquals(true, value.matches("\\d{2}/\\d{2}/\\d{4}"));
    }
}
