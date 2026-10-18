package com.example.engqa.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.engqa.R;

/**
 * Shared Loading / Empty / Error(+Retry) placeholder for the realtime screens so every list shows
 * the same four states.
 */
public class StateView extends LinearLayout {

    private ProgressBar progress;
    private TextView message;
    private TextView retry;

    public StateView(Context context) {
        this(context, null);
    }

    public StateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setGravity(android.view.Gravity.CENTER);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        inflate(context, R.layout.view_state, this);
        progress = findViewById(R.id.progressState);
        message = findViewById(R.id.tvStateMessage);
        retry = findViewById(R.id.btnStateRetry);
        setVisibility(GONE);
    }

    public void setOnRetryListener(OnClickListener listener) {
        retry.setOnClickListener(listener);
    }

    public void showLoading() {
        setVisibility(VISIBLE);
        progress.setVisibility(VISIBLE);
        message.setText(R.string.loading);
        message.setVisibility(VISIBLE);
        retry.setVisibility(GONE);
    }

    public void showEmpty() {
        setVisibility(VISIBLE);
        progress.setVisibility(GONE);
        message.setText(R.string.state_empty);
        message.setVisibility(VISIBLE);
        retry.setVisibility(GONE);
    }

    public void showError(String text) {
        setVisibility(VISIBLE);
        progress.setVisibility(GONE);
        message.setText(text != null ? text : getContext().getString(R.string.state_error));
        message.setVisibility(VISIBLE);
        retry.setVisibility(VISIBLE);
    }

    public void showContent() {
        setVisibility(GONE);
    }

    public boolean isShowingContent() {
        return getVisibility() != VISIBLE;
    }

    public View getRetryButton() {
        return retry;
    }
}
