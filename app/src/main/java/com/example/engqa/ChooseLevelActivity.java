package com.example.engqa;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ChooseLevelActivity extends AppCompatActivity {

    private AppCompatButton btnBeginner;
    private AppCompatButton btnIntermediate;
    private AppCompatButton btnAdvance;
    private AppCompatButton btnContinue;

    private String selectedLevel = null;

    private static final String LEVEL_BEGINNER = "Beginner";
    private static final String LEVEL_INTERMEDIATE = "Intermediate";
    private static final String LEVEL_ADVANCE = "Advance";

    private static final String DESC_BEGINNER = "Dành cho người mới bắt đầu.";
    private static final String DESC_INTERMEDIATE = "Dành cho người đã có kiến thức cơ bản và muốn nâng cao kỹ năng";
    private static final String DESC_ADVANCE = "Dành cho người đã có kinh nghiệm và muốn chinh phục thử thách cao hơn";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = Color.parseColor("#121417");
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_choose_level);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        btnBeginner = findViewById(R.id.btnBeginner);
        btnIntermediate = findViewById(R.id.btnIntermediate);
        btnAdvance = findViewById(R.id.btnAdvance);
        btnContinue = findViewById(R.id.btnContinue);

        updateCardUI(btnBeginner, LEVEL_BEGINNER, DESC_BEGINNER, false);
        updateCardUI(btnIntermediate, LEVEL_INTERMEDIATE, DESC_INTERMEDIATE, false);
        updateCardUI(btnAdvance, LEVEL_ADVANCE, DESC_ADVANCE, false);

        btnBeginner.setOnClickListener(v -> selectLevel(LEVEL_BEGINNER));
        btnIntermediate.setOnClickListener(v -> selectLevel(LEVEL_INTERMEDIATE));
        btnAdvance.setOnClickListener(v -> selectLevel(LEVEL_ADVANCE));

        btnContinue.setOnClickListener(v -> {
            if (selectedLevel == null) {
                Toast.makeText(this, "Vui lòng chọn trình độ", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Đã chọn: " + selectedLevel, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, HomeActivity.class);
                intent.putExtra("level", selectedLevel);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
    }

    private void selectLevel(String level) {
        selectedLevel = level;

        boolean isBeginner = level.equals(LEVEL_BEGINNER);
        boolean isIntermediate = level.equals(LEVEL_INTERMEDIATE);
        boolean isAdvance = level.equals(LEVEL_ADVANCE);

        btnBeginner.setSelected(isBeginner);
        btnIntermediate.setSelected(isIntermediate);
        btnAdvance.setSelected(isAdvance);

        updateCardUI(btnBeginner, LEVEL_BEGINNER, DESC_BEGINNER, isBeginner);
        updateCardUI(btnIntermediate, LEVEL_INTERMEDIATE, DESC_INTERMEDIATE, isIntermediate);
        updateCardUI(btnAdvance, LEVEL_ADVANCE, DESC_ADVANCE, isAdvance);
    }

    private void updateCardUI(Button btn, String title, String desc, boolean isSelected) {
        String fullText = title + "\n" + desc;
        SpannableString ss = new SpannableString(fullText);

        // Title span: 16sp, Bold, color
        ss.setSpan(new AbsoluteSizeSpan(16, true), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(isSelected ? Color.WHITE : Color.parseColor("#333333")), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Description span: 13sp, color
        ss.setSpan(new AbsoluteSizeSpan(13, true), title.length() + 1, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(isSelected ? Color.parseColor("#E0E0E0") : Color.parseColor("#666666")), title.length() + 1, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        btn.setText(ss);
    }
}
