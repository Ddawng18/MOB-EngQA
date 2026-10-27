package com.example.engqa.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/** Single Room database for local-only data and the offline read cache. */
@Database(entities = {SavedPromo.class, CachedQuestion.class, CachedNotification.class},
        version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    /**
     * Adds the per-account owner to the key. Legacy rows keep an empty owner, so they are no longer
     * shown to any account instead of leaking between users.
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE saved_promo_new ("
                    + "owner_uid TEXT NOT NULL, "
                    + "promo_id TEXT NOT NULL, "
                    + "center_name TEXT, "
                    + "title TEXT, "
                    + "image_url TEXT, "
                    + "saved_at INTEGER NOT NULL, "
                    + "PRIMARY KEY(owner_uid, promo_id))");
            database.execSQL("INSERT INTO saved_promo_new "
                    + "(owner_uid, promo_id, center_name, title, image_url, saved_at) "
                    + "SELECT '', promoId, center_name, title, image_url, saved_at "
                    + "FROM saved_promo");
            database.execSQL("DROP TABLE saved_promo");
            database.execSQL("ALTER TABLE saved_promo_new RENAME TO saved_promo");
        }
    };

    /**
     * Adds the offline cache tables. The cache is disposable, so it is created empty; existing
     * saved promos are untouched.
     */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS cached_question ("
                    + "owner_uid TEXT NOT NULL, "
                    + "question_id TEXT NOT NULL, "
                    + "payload TEXT, "
                    + "category_id TEXT, "
                    + "created_at INTEGER NOT NULL, "
                    + "score INTEGER NOT NULL, "
                    + "answer_count INTEGER NOT NULL, "
                    + "cached_at INTEGER NOT NULL, "
                    + "PRIMARY KEY(owner_uid, question_id))");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_question_owner_uid "
                    + "ON cached_question(owner_uid)");
            database.execSQL("CREATE TABLE IF NOT EXISTS cached_notification ("
                    + "owner_uid TEXT NOT NULL, "
                    + "notification_id TEXT NOT NULL, "
                    + "payload TEXT, "
                    + "created_at INTEGER NOT NULL, "
                    + "is_read INTEGER NOT NULL, "
                    + "cached_at INTEGER NOT NULL, "
                    + "PRIMARY KEY(owner_uid, notification_id))");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_notification_owner_uid "
                    + "ON cached_notification(owner_uid)");
        }
    };

    public abstract SavedPromoDao savedPromoDao();

    public abstract CachedQuestionDao cachedQuestionDao();

    public abstract CachedNotificationDao cachedNotificationDao();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "engqa.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
