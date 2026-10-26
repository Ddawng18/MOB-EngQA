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

/**
 * Terminal screen for moderated accounts.
 *
 * <p>TIMEOUT is temporary (24h, applied after three content warnings); DELETED is the soft-delete
 * tombstone after three timeouts. Both reuse the offline illustration but replace the copy, and
 * neither offers a way back into the app: the only actions are retry (timeout) and sign out.
 */
public class AccountBlockedActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "com.example.engqa.extra.BLOCKED_MODE";
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

        if (deleted) {
            title.setText(R.string.blocked_deleted_title);
            message.setText(R.string.blocked_deleted_message);
            retry.setVisibility(View.GONE);
        } else {
            title.setText(R.string.blocked_timeout_title);
            message.setText(R.string.blocked_timeout_message);
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
