package com.example.engqa.job;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.example.engqa.data.OfflineCache;

import java.io.File;

/**
 * Periodic maintenance job (JobScheduler lab). WorkManager handles the one-shot offline publish;
 * this service handles the repeating cache cleanup because it is a good fit for a persisted job
 * with no user-visible deadline.
 */
public class CacheCleanupJobService extends JobService {

    public static final int JOB_ID = 7301;
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long PERIOD_MS = 24L * 60 * 60 * 1000;

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || scheduler.getPendingJob(JOB_ID) != null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, CacheCleanupJobService.class))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .setRequiresCharging(false)
                .build();
        scheduler.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
            File cache = getCacheDir();
            clean(new File(cache, "images"), cutoff);
            clean(new File(cache, "audio"), cutoff);
            clean(new File(cache, "media"), cutoff);
            OfflineCache.get().clearExpired();
            jobFinished(params, false);
        }, "engqa-cache-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Returning true asks JobScheduler to retry if the system stops the job.
        return true;
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
