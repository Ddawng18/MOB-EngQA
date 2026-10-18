package com.example.engqa.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.engqa.R;
import com.example.engqa.media.AudioPlayerManager;

/**
 * Inline audio player used in the question detail and preview screens. The media player is released
 * with the view; a screen that must keep the position across a rotation reads {@link #getPosition()}
 * and calls {@link #setPendingPosition(int)} after recreating.
 */
public class AudioPlayerView extends LinearLayout {

    private final AudioPlayerManager playerManager = new AudioPlayerManager();
    private ImageView playButton;
    private TextView label;
    private String url;

    public AudioPlayerView(Context context) {
        this(context, null);
    }

    public AudioPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        inflate(context, R.layout.view_audio_player, this);
        playButton = findViewById(R.id.btnPlayPause);
        label = findViewById(R.id.tvAudioLabel);
        playButton.setOnClickListener(v -> toggle());
        playerManager.setListener((playing, positionMs, durationMs) ->
                playButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play));
        setVisibility(GONE);
    }

    public void setUrl(String url) {
        this.url = url;
        if (url == null || url.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        label.setText(R.string.label_audio);
        playButton.setImageResource(R.drawable.ic_play);
    }

    public int getPosition() {
        return playerManager.getPosition();
    }

    public void setPendingPosition(int positionMs) {
        playerManager.setPendingPosition(positionMs);
    }

    private void toggle() {
        if (url != null) {
            playerManager.toggle(url);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        playerManager.release();
        super.onDetachedFromWindow();
    }
}
