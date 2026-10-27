package com.example.engqa.data;

import android.os.Handler;
import android.os.Looper;

import com.example.engqa.EngQAApp;
import com.example.engqa.data.local.AppDatabase;
import com.example.engqa.data.local.CachedNotification;
import com.example.engqa.data.local.CachedQuestion;
import com.example.engqa.model.AppNotification;
import com.example.engqa.model.Question;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small Room-backed read cache used when Firestore has no cached page (for example after a process
 * death while offline). It stores the last page the user actually saw, keyed by account so two
 * users on one device never see each other's content.
 */
public final class OfflineCache {

    private static final long CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final OfflineCache INSTANCE = new OfflineCache();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());

    private OfflineCache() {
    }

    public static OfflineCache get() {
        return INSTANCE;
    }

    public void cacheQuestions(String ownerUid, List<Question> questions) {
        if (ownerUid == null || questions == null || questions.isEmpty()) {
            return;
        }
        List<Question> copy = new ArrayList<>(questions);
        AppDatabase db = database();
        if (db == null) {
            return;
        }
        EXECUTOR.execute(() -> {
            long now = System.currentTimeMillis();
            List<CachedQuestion> rows = new ArrayList<>();
            for (Question question : copy) {
                if (question == null || question.getId() == null) {
                    continue;
                }
                rows.add(new CachedQuestion(ownerUid, question.getId(), gson.toJson(question),
                        question.getCategoryId(), time(question.getCreatedAt()),
                        question.getScore(), question.getAnswerCount(), now));
            }
            if (!rows.isEmpty()) {
                db.cachedQuestionDao().upsertAll(rows);
            }
        });
    }

    public void cacheQuestion(String ownerUid, Question question) {
        if (question == null) {
            return;
        }
        cacheQuestions(ownerUid, Collections.singletonList(question));
    }

    public void loadFeed(String ownerUid, String categoryId, String sort, int limit,
                         Callback<QuestionRepository.Page> callback) {
        if (ownerUid == null) {
            callback.onError(new IllegalStateException("Chưa đăng nhập"));
            return;
        }
        AppDatabase db = database();
        if (db == null) {
            callback.onError(new IllegalStateException("Không mở được bộ nhớ đệm"));
            return;
        }
        EXECUTOR.execute(() -> {
            List<CachedQuestion> rows = db.cachedQuestionDao().load(ownerUid, categoryId);
            List<Question> questions = parse(rows);
            sort(questions, sort);
            if (questions.size() > limit) {
                questions = new ArrayList<>(questions.subList(0, limit));
            }
            List<Question> result = questions;
            main.post(() -> {
                if (result.isEmpty()) {
                    callback.onError(new IllegalStateException("Không có dữ liệu ngoại tuyến"));
                } else {
                    callback.onSuccess(new QuestionRepository.Page(result, null, true));
                }
            });
        });
    }

    public void loadQuestion(String ownerUid, String questionId,
                             Callback<Question> callback) {
        if (ownerUid == null) {
            callback.onError(new IllegalStateException("Chưa đăng nhập"));
            return;
        }
        AppDatabase db = database();
        if (db == null) {
            callback.onError(new IllegalStateException("Không mở được bộ nhớ đệm"));
            return;
        }
        EXECUTOR.execute(() -> {
            CachedQuestion row = db.cachedQuestionDao().find(ownerUid, questionId);
            Question question = row == null ? null : gson.fromJson(row.getPayload(), Question.class);
            main.post(() -> {
                if (question == null) {
                    callback.onError(new IllegalStateException("Không có dữ liệu ngoại tuyến"));
                } else {
                    callback.onSuccess(question);
                }
            });
        });
    }

    public void loadSearch(String ownerUid, String categoryId, List<String> tokens, int limit,
                           Callback<QuestionRepository.Page> callback) {
        if (ownerUid == null || tokens == null || tokens.isEmpty()) {
            callback.onError(new IllegalStateException("Không có dữ liệu ngoại tuyến"));
            return;
        }
        AppDatabase db = database();
        if (db == null) {
            callback.onError(new IllegalStateException("Không mở được bộ nhớ đệm"));
            return;
        }
        EXECUTOR.execute(() -> {
            List<Question> questions = new ArrayList<>();
            for (CachedQuestion row : db.cachedQuestionDao().load(ownerUid, categoryId)) {
                Question question = gson.fromJson(row.getPayload(), Question.class);
                if (question != null && matches(question, tokens)) {
                    questions.add(question);
                }
            }
            if (questions.size() > limit) {
                questions = new ArrayList<>(questions.subList(0, limit));
            }
            List<Question> result = questions;
            main.post(() -> {
                if (result.isEmpty()) {
                    callback.onError(new IllegalStateException("Không có dữ liệu ngoại tuyến"));
                } else {
                    callback.onSuccess(new QuestionRepository.Page(result, null, true));
                }
            });
        });
    }

    public void cacheNotifications(String ownerUid, List<AppNotification> notifications) {
        if (ownerUid == null || notifications == null) {
            return;
        }
        AppDatabase db = database();
        if (db == null) {
            return;
        }
        List<AppNotification> copy = new ArrayList<>(notifications);
        EXECUTOR.execute(() -> {
            long now = System.currentTimeMillis();
            List<CachedNotification> rows = new ArrayList<>();
            for (AppNotification notification : copy) {
                if (notification == null || notification.getId() == null) {
                    continue;
                }
                rows.add(new CachedNotification(ownerUid, notification.getId(),
                        gson.toJson(notification), time(notification.getCreatedAt()),
                        notification.isRead(), now));
            }
            if (!rows.isEmpty()) {
                db.cachedNotificationDao().upsertAll(rows);
            }
        });
    }

    public void loadNotifications(String ownerUid, Callback<List<AppNotification>> callback) {
        if (ownerUid == null) {
            callback.onError(new IllegalStateException("Chưa đăng nhập"));
            return;
        }
        AppDatabase db = database();
        if (db == null) {
            callback.onError(new IllegalStateException("Không mở được bộ nhớ đệm"));
            return;
        }
        EXECUTOR.execute(() -> {
            List<AppNotification> result = new ArrayList<>();
            for (CachedNotification row : db.cachedNotificationDao().load(ownerUid)) {
                AppNotification notification =
                        gson.fromJson(row.getPayload(), AppNotification.class);
                if (notification != null) {
                    result.add(notification);
                }
            }
            main.post(() -> {
                if (result.isEmpty()) {
                    callback.onError(new IllegalStateException("Không có dữ liệu ngoại tuyến"));
                } else {
                    callback.onSuccess(result);
                }
            });
        });
    }

    /** Called by the periodic cache job; keeps the database bounded even if no screen opens. */
    public void clearExpired() {
        AppDatabase db = database();
        if (db == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - CACHE_TTL_MS;
        EXECUTOR.execute(() -> {
            db.cachedQuestionDao().deleteOlderThan(cutoff);
            db.cachedNotificationDao().deleteOlderThan(cutoff);
        });
    }

    private static AppDatabase database() {
        try {
            return AppDatabase.get(EngQAApp.getContext());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<Question> parse(List<CachedQuestion> rows) {
        List<Question> questions = new ArrayList<>();
        for (CachedQuestion row : rows) {
            Question question = gson.fromJson(row.getPayload(), Question.class);
            if (question != null) {
                questions.add(question);
            }
        }
        return questions;
    }

    private static void sort(List<Question> questions, String sort) {
        if (QuestionRepository.SORT_POPULAR.equals(sort)) {
            questions.sort((left, right) -> {
                int byScore = Long.compare(right.getScore(), left.getScore());
                return byScore != 0 ? byScore : byNewest(left, right);
            });
        } else if (QuestionRepository.SORT_UNANSWERED.equals(sort)) {
            questions.sort((left, right) -> {
                int byAnswers = Long.compare(left.getAnswerCount(), right.getAnswerCount());
                return byAnswers != 0 ? byAnswers : byNewest(left, right);
            });
        } else {
            questions.sort(OfflineCache::byNewest);
        }
    }

    private static int byNewest(Question left, Question right) {
        return Long.compare(time(right.getCreatedAt()), time(left.getCreatedAt()));
    }

    private static boolean matches(Question question, List<String> tokens) {
        List<String> keywords = question.getSearchKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String normalized = keyword.toLowerCase(Locale.ROOT);
            for (String token : tokens) {
                if (token != null && normalized.contains(token.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long time(Date date) {
        return date != null ? date.getTime() : 0L;
    }
}
