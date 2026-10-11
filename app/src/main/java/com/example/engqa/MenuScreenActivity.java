package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MenuScreenActivity extends AppCompatActivity {

    private TextView tvAvatar;
    private TextView tvUsername;
    private TextView tvEmail;
    private TextView tvLevelBadge;
    private TextView tvQuestionsAskedCount;
    private TextView tvAnswersCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_menu_screen);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        tvAvatar = findViewById(R.id.tvAvatar);
        tvUsername = findViewById(R.id.tvUsername);
        tvEmail = findViewById(R.id.tvEmail);
        tvLevelBadge = findViewById(R.id.tvLevelBadge);
        tvQuestionsAskedCount = findViewById(R.id.tvQuestionsAskedCount);
        tvAnswersCount = findViewById(R.id.tvAnswersCount);

        loadUserData();

        // Top bar back
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Menu items click handlers
        findViewById(R.id.btnMyQuestions).setOnClickListener(v -> toast("Danh sách câu hỏi của tôi (chưa triển khai)"));
        findViewById(R.id.btnMyAnswers).setOnClickListener(v -> toast("Danh sách câu trả lời của tôi (chưa triển khai)"));
        findViewById(R.id.btnSaved).setOnClickListener(v -> toast("Tính năng Đã lưu (chưa triển khai)"));
        findViewById(R.id.btnStats).setOnClickListener(v -> toast("Tính năng Thống kê học tập (chưa triển khai)"));
        findViewById(R.id.btnSettings).setOnClickListener(v -> toast("Tính năng Cài đặt (chưa triển khai)"));
        findViewById(R.id.btnHelp).setOnClickListener(v -> toast("Tính năng Trợ giúp (chưa triển khai)"));

        // Logout
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new SessionManager(this).logout();
            Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        // Bottom nav
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
        findViewById(R.id.btnAsk).setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
    }

    private void loadUserData() {
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            UserDbHelper.User user = new UserDbHelper(this).findById(session.getUserId());
            if (user != null) {
                tvUsername.setText(user.username != null ? user.username : "user");
                tvEmail.setText(user.email != null ? user.email : "");

                if (user.username != null && !user.username.isEmpty()) {
                    tvAvatar.setText(String.valueOf(user.username.charAt(0)).toUpperCase());
                } else if (user.email != null && !user.email.isEmpty()) {
                    tvAvatar.setText(String.valueOf(user.email.charAt(0)).toUpperCase());
                }

                if (user.level != null && !user.level.isEmpty()) {
                    tvLevelBadge.setText(user.level);
                } else {
                    tvLevelBadge.setText("Beginner");
                }
            }
        }
        // Mock / placeholder counts as requested
        tvQuestionsAskedCount.setText("12");
        tvAnswersCount.setText("48");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
