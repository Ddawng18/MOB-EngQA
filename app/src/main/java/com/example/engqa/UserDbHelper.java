package com.example.engqa;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Locale;

/** Local USER table (see ERD in README). Swap for a real API once the backend exists. */
public class UserDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "engqa.db";
    private static final int DB_VERSION = 1;

    public static class User {
        public final long id;
        public final String username;
        public final String email;
        public final String level;

        User(long id, String username, String email, String level) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.level = level;
        }
    }

    public UserDbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL UNIQUE,"
                + "email TEXT NOT NULL UNIQUE,"
                + "password_hash TEXT NOT NULL,"
                + "salt TEXT NOT NULL,"
                + "level TEXT,"
                + "created_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean emailExists(String email) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM users WHERE email = ?", new String[]{normalizeEmail(email)})) {
            return c.moveToFirst();
        }
    }

    /** @return the new user, or null if the email is already registered. */
    public User register(String email, String password) {
        String mail = normalizeEmail(email);
        if (emailExists(mail)) {
            return null;
        }
        String salt = PasswordHasher.newSalt();
        ContentValues values = new ContentValues();
        values.put("username", uniqueUsername(mail.substring(0, mail.indexOf('@'))));
        values.put("email", mail);
        values.put("password_hash", PasswordHasher.hash(password, salt));
        values.put("salt", salt);
        values.put("created_at", System.currentTimeMillis());
        long id = getWritableDatabase().insert("users", null, values);
        return id == -1 ? null : findById(id);
    }

    /** @return the user if credentials match, otherwise null. */
    public User authenticate(String email, String password) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, password_hash, salt FROM users WHERE email = ?",
                new String[]{normalizeEmail(email)})) {
            if (!c.moveToFirst()) {
                return null;
            }
            if (!PasswordHasher.verify(password, c.getString(2), c.getString(1))) {
                return null;
            }
            return findById(c.getLong(0));
        }
    }

    public void setLevel(long userId, String level) {
        ContentValues values = new ContentValues();
        values.put("level", level);
        getWritableDatabase().update("users", values, "id = ?", new String[]{String.valueOf(userId)});
    }

    public User findById(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, username, email, level FROM users WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst()
                    ? new User(c.getLong(0), c.getString(1), c.getString(2), c.getString(3))
                    : null;
        }
    }

    private String uniqueUsername(String base) {
        String name = base.isEmpty() ? "user" : base;
        String candidate = name;
        int n = 1;
        while (usernameExists(candidate)) {
            candidate = name + (++n);
        }
        return candidate;
    }

    private boolean usernameExists(String username) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM users WHERE username = ?", new String[]{username})) {
            return c.moveToFirst();
        }
    }
}