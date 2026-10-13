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
        findViewById(R.id.btnMyQuestions).setOnClickListener(v -> toast("Câu hỏi của tôi (chưa triển khai)"));
        findViewById(R.id.btnMyAnswers).setOnClickListener(v -> toast("Câu trả lời của tôi (chưa triển khai)"));
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
        // Set defaults immediately
        tvQuestionsAskedCount.setText("12");
        tvAnswersCount.setText("48");
        tvLevelBadge.setText("Beginner");

        SessionManager session = new SessionManager(this);
        String uid = session.getUserId();
        if (uid != null) {
            new UserRepository().getUser(uid, new UserRepository.Callback<UserRepository.User>() {
                @Override
                public void onSuccess(UserRepository.User user) {
                    if (isFinishing()) return;
                    if (user != null) {
                        tvUsername.setText(user.username != null ? user.username : "user");
                        tvEmail.setText(user.email != null ? user.email : "");

                        String letterSource = (user.username != null && !user.username.isEmpty()) ? user.username : user.email;
                        if (letterSource != null && !letterSource.isEmpty()) {
                            tvAvatar.setText(String.valueOf(letterSource.charAt(0)).toUpperCase());
                        }

                        if (user.level != null && !user.level.isEmpty()) {
                            tvLevelBadge.setText(user.level);
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (isFinishing()) return;
                    Toast.makeText(MenuScreenActivity.this, UserRepository.messageFor(e), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
