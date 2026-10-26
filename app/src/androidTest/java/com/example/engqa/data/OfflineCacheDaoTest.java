package com.example.engqa.data;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.engqa.data.local.AppDatabase;
import com.example.engqa.data.local.CachedNotification;
import com.example.engqa.data.local.CachedQuestion;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Verifies the Room cache tables used by the offline feed/detail fallback. */
@RunWith(AndroidJUnit4.class)
public class OfflineCacheDaoTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void questionsAreScopedByAccountAndCategory() {
        db.cachedQuestionDao().upsertAll(Arrays.asList(
                question("u1", "q1", "grammar", 1_000, 5, 2),
                question("u1", "q2", "vocabulary", 2_000, 1, 0),
                question("u2", "q3", "grammar", 3_000, 9, 4)));

        List<CachedQuestion> grammar = db.cachedQuestionDao().load("u1", "grammar");
        assertEquals(1, grammar.size());
        assertEquals("q1", grammar.get(0).getQuestionId());

        List<CachedQuestion> all = db.cachedQuestionDao().load("u1", null);
        assertEquals(2, all.size());
        assertEquals("q2", all.get(0).getQuestionId());
    }

    @Test
    public void findAndExpiryWork() {
        db.cachedQuestionDao().upsertAll(Arrays.asList(
                question("u1", "q1", "grammar", 1_000, 5, 2),
                new CachedQuestion("u1", "old", "{}", "grammar", 1, 0, 0, 1)));
        assertNotNull(db.cachedQuestionDao().find("u1", "q1"));
        assertNull(db.cachedQuestionDao().find("u2", "q1"));

        db.cachedQuestionDao().deleteOlderThan(100);
        assertNull(db.cachedQuestionDao().find("u1", "old"));
    }

    @Test
    public void notificationsAreScopedByAccount() {
        db.cachedNotificationDao().upsertAll(Arrays.asList(
                new CachedNotification("u1", "n1", "{}", 2_000, false, 2_000),
                new CachedNotification("u2", "n2", "{}", 3_000, true, 3_000)));
        assertEquals(1, db.cachedNotificationDao().load("u1").size());
        assertEquals("n1", db.cachedNotificationDao().load("u1").get(0).getNotificationId());
    }

    private static CachedQuestion question(String uid, String id, String category,
                                           long createdAt, long score, long answers) {
        return new CachedQuestion(uid, id, "{\"id\":\"" + id + "\"}", category,
                createdAt, score, answers, System.currentTimeMillis());
    }
}
