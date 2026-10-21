package com.example.engqa.media;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

/**
 * Small {@link MediaPlayer} wrapper bound to one screen's lifecycle.
 *
 * <p>The owning Activity calls {@link #release()} in {@code onStop} and keeps the last known
 * {@link #getPosition()} so playback resumes at the same place after a rotation.
 */
public class AudioPlayerManager {

    private static final String TAG = "AudioPlayerManager";

    public interface Listener {
        void onPlaybackStateChanged(boolean playing, int positionMs, int durationMs);
    }

    private MediaPlayer player;
    private String currentUrl;
    private int pendingPositionMs;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public int getPosition() {
        return player != null ? player.getCurrentPosition() : pendingPositionMs;
    }

    public void toggle(String url) {
        if (url == null) {
            return;
        }
        if (player != null && url.equals(currentUrl)) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.start();
            }
            notifyState();
            return;
        }
        release();
        currentUrl = url;
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        try {
            player.setDataSource(url);
            player.setOnPreparedListener(mp -> {
                if (pendingPositionMs > 0) {
                    mp.seekTo(pendingPositionMs);
                    pendingPositionMs = 0;
                }
                mp.start();
                notifyState();
            });
            player.setOnCompletionListener(mp -> {
                pendingPositionMs = 0;
                notifyState();
            });
            player.prepareAsync();
        } catch (IOException e) {
            Log.w(TAG, "Không phát được audio: " + url, e);
        }
    }

    /** Remembers a position to resume at on the next {@link #toggle(String)}. */
    public void setPendingPosition(int positionMs) {
        this.pendingPositionMs = positionMs;
    }

    public void release() {
        if (player != null) {
            pendingPositionMs = player.getCurrentPosition();
            player.release();
            player = null;
        }
    }

    private void notifyState() {
        if (listener != null && player != null) {
            listener.onPlaybackStateChanged(player.isPlaying(),
                    player.getCurrentPosition(), player.getDuration());
        }
    }
}
