package com.example.engqa.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;

/** Deletes cached images/audio older than seven days (periodic WorkManager job). */
public class CacheCleanupWorker extends Worker {

    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    public CacheCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        File cache = getApplicationContext().getCacheDir();
        clean(new File(cache, "images"), cutoff);
        clean(new File(cache, "audio"), cutoff);
        clean(new File(cache, "media"), cutoff);
        return Result.success();
    }

    private static void clean(File directory, long cutoff) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                clean(child, cutoff);
            } else if (child.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
    }
}
