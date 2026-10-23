package com.example.engqa.worker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.engqa.NetworkUtils;
import com.example.engqa.R;
import com.example.engqa.data.Callback;
import com.example.engqa.data.QuestionPublisher;
import com.example.engqa.notification.NotificationHelper;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Publishes a queued question (compressed Firestore media + question document) when the network is
 * back. Retries with exponential backoff, then notifies and broadcasts on success.
 */
public class PendingQuestionWorker extends Worker {

    public static final String KEY_FOLDER = "folder";
    public static final String ACTION_PENDING_POSTED = "com.example.engqa.action.PENDING_POSTED";

    public PendingQuestionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String folderPath = getInputData().getString(KEY_FOLDER);
        if (folderPath == null) {
            return Result.failure();
        }
        File folder = new File(folderPath);
        PendingQuestionDraft draft = PendingDraftStore.read(folder);
        if (draft == null || draft.uid == null) {
            return Result.failure();
        }
        if (!NetworkUtils.isOnline(context)) {
            return Result.retry();
        }

        QuestionPublisher publisher = new QuestionPublisher();
        try {
            QuestionPublisher.Draft outgoing = new QuestionPublisher.Draft();
            outgoing.authorId = draft.uid;
            outgoing.authorName = draft.authorName;
            outgoing.title = draft.title;
            outgoing.body = draft.body;
            outgoing.passageText = draft.passageText;
            outgoing.categoryId = draft.categoryId;
            outgoing.categoryName = draft.categoryName;
            outgoing.level = draft.level;
            outgoing.type = draft.type;
            outgoing.tags = draft.tags;
            for (String name : draft.imageFiles) {
                outgoing.images.add(Uri.fromFile(new File(folder, "images/" + name)));
            }
            if (draft.audioFile != null) {
                outgoing.audio = Uri.fromFile(new File(folder, draft.audioFile));
            }

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<String> questionId = new AtomicReference<>();
            final AtomicReference<Exception> failure = new AtomicReference<>();
            publisher.publish(context, outgoing, null, new Callback<String>() {
                @Override
                public void onSuccess(String id) {
                    questionId.set(id);
                    latch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    failure.set(e);
                    latch.countDown();
                }
            });

            if (!latch.await(180, TimeUnit.SECONDS)
                    || failure.get() != null || questionId.get() == null) {
                return Result.retry();
            }

            PendingDraftStore.delete(folder);
            NotificationHelper.show(
                    context,
                    (int) (System.currentTimeMillis() & 0x7FFFFFFF),
                    context.getString(R.string.pending_posted_title),
                    draft.title,
                    questionId.get());
            Intent intent = new Intent(ACTION_PENDING_POSTED).setPackage(context.getPackageName());
            context.sendBroadcast(intent);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        } finally {
            publisher.shutdown();
        }
    }
}
