package com.example.engqa;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Interface 2 - "Chi tiết câu hỏi" (question detail screen). UI prototype with static data.
 */
public class QuestionDetailActivity extends AppCompatActivity {

    /** avatar, username, time, text, upvotes */
    private static final String[][] ANSWERS = {
            {"L", "lan_teacher", "1 giờ trước",
                    "Dùng \"has been\" với chủ ngữ số ít (he, she, it) và \"have been\" với I, you, we, they.",
                    "12"},
            {"T", "tuan_ng", "45 phút trước",
                    "Ví dụ: She has been here since 8 AM. They have been friends for years.",
                    "7"},
            {"H", "hoa_pham", "20 phút trước",
                    "Bạn có thể xem thêm phần thì hiện tại hoàn thành trong sách ngữ pháp để hiểu rõ hơn nhé.",
                    "3"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_question_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        // TODO: open full-screen image viewer
        findViewById(R.id.attachmentPlaceholder).setOnClickListener(v -> toast("Phóng to ảnh (chưa triển khai)"));
        // TODO: submit answer
        findViewById(R.id.btnSend).setOnClickListener(v -> toast("Gửi câu trả lời (chưa triển khai)"));

        LinearLayout list = findViewById(R.id.answerList);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String[] a : ANSWERS) {
            View item = inflater.inflate(R.layout.item_answer, list, false);
            ((TextView) item.findViewById(R.id.tvAvatar)).setText(a[0]);
            ((TextView) item.findViewById(R.id.tvUsername)).setText(a[1]);
            ((TextView) item.findViewById(R.id.tvTime)).setText(a[2]);
            ((TextView) item.findViewById(R.id.tvAnswerText)).setText(a[3]);
            TextView upvote = item.findViewById(R.id.tvUpvote);
            upvote.setText("▲ " + a[4]);
            // TODO: real upvote
            upvote.setOnClickListener(v -> toast("Upvote (chưa triển khai)"));
            list.addView(item);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
