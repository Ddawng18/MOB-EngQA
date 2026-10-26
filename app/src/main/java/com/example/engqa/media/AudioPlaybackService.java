package com.example.engqa.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.engqa.HomeActivity;
import com.example.engqa.QuestionDetailActivity;
import com.example.engqa.R;

import java.io.IOException;

/**
 * Foreground service that keeps an answer's audio playing when the user leaves the screen
 * (Service + Notification lab). The inline {@link com.example.engqa.ui.AudioPlayerView} keeps its
 * existing lightweight player; this service is started explicitly by the "Nền" action.
 */
public class AudioPlaybackService extends Service {

    public static final String ACTION_PLAY = "com.example.engqa.action.AUDIO_PLAY";
    public static final String ACTION_TOGGLE = "com.example.engqa.action.AUDIO_TOGGLE";
    public static final String ACTION_STOP = "com.example.engqa.action.AUDIO_STOP";
    public static final String EXTRA_URL = "audio_url";
    public static final String EXTRA_QUESTION_ID = "question_id";

    private static final String TAG = "AudioPlaybackService";
    private static final String CHANNEL_ID = "EngQA_playback";
    private static final int NOTIFICATION_ID = 4102;

    private MediaPlayer player;
    private String currentUrl;
    private String questionId;

    public static void play(Context context, String url, String questionId) {
        if (context == null || url == null || url.isEmpty()) {
            return;
        }
        Intent intent = new Intent(context, AudioPlaybackService.class)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_QUESTION_ID, questionId);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        if (context == null) {
            return;
        }
        context.startService(new Intent(context, AudioPlaybackService.class)
                .setAction(ACTION_STOP));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            release();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_TOGGLE.equals(action)) {
            if (player != null && player.isPlaying()) {
                player.pause();
                updateNotification(false);
            } else if (player != null) {
                player.start();
                updateNotification(true);
            }
            return START_NOT_STICKY;
        }
        String url = intent.getStringExtra(EXTRA_URL);
        questionId = intent.getStringExtra(EXTRA_QUESTION_ID);
        if (url == null || url.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // Must call startForeground promptly; the preparing state is replaced on prepared.
        startForeground(NOTIFICATION_ID, notification(false, true));
        if (player != null && url.equals(currentUrl)) {
            if (!player.isPlaying()) {
                player.start();
                updateNotification(true);
            }
            return START_NOT_STICKY;
        }
        release();
        currentUrl = url;
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        try {
            if (url.startsWith("content://") || url.startsWith("file://")) {
                player.setDataSource(this, Uri.parse(url));
            } else {
                player.setDataSource(url);
            }
            player.setOnPreparedListener(mp -> {
                mp.start();
                updateNotification(true);
            });
            player.setOnCompletionListener(mp -> {
                release();
                stopForeground(true);
                stopSelf();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "MediaPlayer error " + what + "/" + extra);
                release();
                stopForeground(true);
                stopSelf();
                return true;
            });
            player.prepareAsync();
        } catch (IOException e) {
            Log.w(TAG, "Cannot play audio", e);
            release();
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void updateNotification(boolean playing) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification(playing, false));
        }
    }

    private Notification notification(boolean playing, boolean preparing) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        Intent open = questionId != null
                ? new Intent(this, QuestionDetailActivity.class)
                    .putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID, questionId)
                : new Intent(this, HomeActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 1, open, flags);

        Intent toggle = new Intent(this, AudioPlaybackService.class)
                .setAction(ACTION_TOGGLE);
        PendingIntent togglePending = PendingIntent.getService(this, 2, toggle, flags);
        Intent stop = new Intent(this, AudioPlaybackService.class)
                .setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 3, stop, flags);

        String text = preparing ? getString(R.string.audio_preparing)
                : getString(playing ? R.string.audio_playing : R.string.audio_paused);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_audio)
                .setContentTitle(getString(R.string.label_audio))
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        getString(playing ? R.string.audio_pause : R.string.audio_play),
                        togglePending)
                .addAction(R.drawable.ic_close, getString(R.string.audio_stop), stopPending)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.audio_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.audio_channel_desc));
            manager.createNotificationChannel(channel);
        }
    }

    private void release() {
        if (player != null) {
            try {
                player.release();
            } catch (IllegalStateException ignored) {
                // Already released.
            }
            player = null;
        }
        currentUrl = null;
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
