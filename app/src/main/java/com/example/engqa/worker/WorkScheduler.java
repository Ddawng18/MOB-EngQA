package com.example.engqa.worker;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Single place that enqueues the app's background work. */
public final class WorkScheduler {

    private static final String UNIQUE_CACHE_CLEANUP = "cache-cleanup";

    private WorkScheduler() {
    }

    /** Queues an offline question; it runs when a network is available and retries on failure. */
    public static void enqueuePendingQuestion(Context context, String folderPath) {
        Data input = new Data.Builder()
                .putString(PendingQuestionWorker.KEY_FOLDER, folderPath)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(PendingQuestionWorker.class)
                .setInputData(input)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("pending-question")
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "pending-question-" + new File(folderPath).getName(),
                ExistingWorkPolicy.KEEP,
                request);
    }

    /** Daily cleanup of old cached media. */
    public static void scheduleCacheCleanup(Context context) {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(CacheCleanupWorker.class, 1, TimeUnit.DAYS)
                        .addTag(UNIQUE_CACHE_CLEANUP)
                        .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_CACHE_CLEANUP,
                ExistingPeriodicWorkPolicy.KEEP,
                request);
    }
}
