package com.example.engqa.worker;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Single place that enqueues one-shot WorkManager jobs. Repeating maintenance uses JobScheduler. */
public final class WorkScheduler {

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
}
