package com.example.engqa;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout questionList;
    private EditText searchInput;
    private TextView emptyState;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private static final String[][] QUESTIONS = {
            {"M", "minh_anh", "2 giờ trước", "Ngữ pháp", "Khi nào dùng \"has been\" và \"have been\"?", "12"},
            {"A", "anh_khoa", "4 giờ trước", "Từ vựng", "Phân biệt affect và effect trong tiếng Anh", "8"},
            {"L", "linhnguyen", "Hôm qua", "Phát âm", "Cách phát âm đuôi -ed trong quá khứ đơn?", "5"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!NetworkUtils.isOnline(this)) {
            showServerError();
            return;
        }
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_home);

        View root = findViewById(R.id.homeRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        questionList = findViewById(R.id.questionList);
        searchInput = findViewById(R.id.etSearch);
        emptyState = findViewById(R.id.emptyState);

        renderQuestions("");
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                renderQuestions(text.toString());
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        });

        findViewById(R.id.btnAsk).setOnClickListener(view ->
                startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.btnProfile).setOnClickListener(view ->
                toast("Hồ sơ cá nhân (chưa triển khai)"));
        findViewById(R.id.btnNotifications).setOnClickListener(view ->
                toast("Thông báo (chưa triển khai)"));
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> {
                    if (!NetworkUtils.isOnline(HomeActivity.this)) {
                        showServerError();
                    }
                });
            }
        };
        connectivityManager.registerNetworkCallback(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                networkCallback);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
    }

    private void showServerError() {
        Intent intent = new Intent(this, ServerErrorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void renderQuestions(String query) {
        String normalizedQuery = query.trim().toLowerCase();
        questionList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int visibleCount = 0;

        for (String[] question : QUESTIONS) {
            String searchableText = (question[3] + " " + question[4] + " " + question[1]).toLowerCase();
            if (!normalizedQuery.isEmpty() && !searchableText.contains(normalizedQuery)) {
                continue;
            }

            View card = inflater.inflate(R.layout.item_question, questionList, false);
            ((TextView) card.findViewById(R.id.tvQuestionAvatar)).setText(question[0]);
            ((TextView) card.findViewById(R.id.tvQuestionUser)).setText(question[1]);
            ((TextView) card.findViewById(R.id.tvQuestionTime)).setText(question[2]);
            ((TextView) card.findViewById(R.id.tvQuestionTag)).setText(question[3]);
            ((TextView) card.findViewById(R.id.tvQuestionTitle)).setText(question[4]);
            ((TextView) card.findViewById(R.id.tvQuestionAnswers)).setText(question[5] + " câu trả lời");
            card.setOnClickListener(view -> startActivity(
                    new Intent(this, QuestionDetailActivity.class)));
            questionList.addView(card);
            visibleCount++;
        }

        emptyState.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}
