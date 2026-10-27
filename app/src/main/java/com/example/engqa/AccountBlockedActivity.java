package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.engqa.util.ModerationPolicy;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Terminal screen for moderated accounts.
 *
 * <p>TIMEOUT is temporary (24h, applied after three content warnings); DELETED is the soft-delete
 * tombstone after three timeouts. Both reuse the offline illustration but replace the copy, and
 * neither offers a way back into the app: the only actions are retry (timeout) and sign out.
 *
 * <p>The caller passes the committed timeout counters so a timed-out user, who cannot open the
 * notification list, still sees the same penalty information on this screen.
 */
public class AccountBlockedActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "com.example.engqa.extra.BLOCKED_MODE";
    public static final String EXTRA_TIMEOUT_COUNT =
            "com.example.engqa.extra.BLOCKED_TIMEOUT_COUNT";
    public static final String EXTRA_WARNING_COUNT =
            "com.example.engqa.extra.BLOCKED_WARNING_COUNT";
    public static final String EXTRA_TIMEOUT_UNTIL =
            "com.example.engqa.extra.BLOCKED_TIMEOUT_UNTIL";
    public static final String MODE_TIMEOUT = "timeout";
    public static final String MODE_DELETED = "deleted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.error_screen_bg);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_account_blocked);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        boolean deleted = MODE_DELETED.equals(getIntent().getStringExtra(EXTRA_MODE));
        TextView title = findViewById(R.id.tvBlockedTitle);
        TextView message = findViewById(R.id.tvBlockedMessage);
        View retry = findViewById(R.id.btnBlockedRetry);
        long timeoutCount = getIntent().getLongExtra(EXTRA_TIMEOUT_COUNT, 0L);
        long warningCount = getIntent().getLongExtra(EXTRA_WARNING_COUNT, 0L);

        if (deleted) {
            title.setText(R.string.blocked_deleted_title);
            String text = getString(R.string.blocked_deleted_message);
            if (timeoutCount > 0) {
                text = text + "\n" + getString(R.string.blocked_deleted_progress, timeoutCount,
                        ModerationPolicy.TIMEOUTS_BEFORE_DELETE);
            }
            message.setText(text);
            retry.setVisibility(View.GONE);
        } else {
            title.setText(R.string.blocked_timeout_title);
            String text = getString(R.string.blocked_timeout_message,
                    ModerationPolicy.timeoutDurationHours());
            if (timeoutCount > 0) {
                text = text + "\n" + getString(R.string.blocked_timeout_progress, timeoutCount,
                        ModerationPolicy.TIMEOUTS_BEFORE_DELETE,
                        ModerationPolicy.remainingTimeoutsBeforeDeletion(timeoutCount));
            }
            text = text + "\n" + getString(R.string.blocked_warning_progress, warningCount,
                    ModerationPolicy.WARNINGS_PER_TIMEOUT,
                    ModerationPolicy.remainingWarningsBeforeTimeout(warningCount));
            long until = getIntent().getLongExtra(EXTRA_TIMEOUT_UNTIL, -1L);
            if (until > 0) {
                DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                        DateFormat.SHORT, new Locale("vi", "VN"));
                text = text + "\n" + getString(R.string.blocked_timeout_until,
                        format.format(new Date(until)));
            }
            message.setText(text);
            retry.setVisibility(View.VISIBLE);
            retry.setOnClickListener(v -> {
                startActivity(new Intent(this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            });
        }

        findViewById(R.id.btnBlockedLogout).setOnClickListener(v -> {
            new SessionManager(this).logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });
    }
}
