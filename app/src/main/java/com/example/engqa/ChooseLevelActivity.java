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
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.data.Callback;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.viewmodel.AuthViewModel;

/** Level selection shown right after registration; writes the level to the user document. */
public class ChooseLevelActivity extends AppCompatActivity {

    private AppCompatButton btnBeginner;
    private AppCompatButton btnIntermediate;
    private AppCompatButton btnAdvanced;
    private AppCompatButton btnContinue;

    private AuthViewModel viewModel;
    private String selectedLevel = null;

    private static final String LEVEL_BEGINNER = "Beginner";
    private static final String LEVEL_INTERMEDIATE = "Intermediate";
    private static final String LEVEL_ADVANCED = "Advanced";
    private static final String STATE_SELECTED_LEVEL = "selected_level";

    private static final String DESC_BEGINNER = "Dành cho người mới bắt đầu.";
    private static final String DESC_INTERMEDIATE =
            "Dành cho người đã có kiến thức cơ bản và muốn nâng cao kỹ năng";
    private static final String DESC_ADVANCED =
            "Dành cho người đã có kinh nghiệm và muốn chinh phục thử thách cao hơn";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = Color.parseColor("#121417");
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_choose_level);

        View rootLayout = findViewById(R.id.rootLayout);
        int baseStart = rootLayout.getPaddingStart();
        int baseTop = rootLayout.getPaddingTop();
        int baseEnd = rootLayout.getPaddingEnd();
        int baseBottom = rootLayout.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPaddingRelative(baseStart + bars.left, baseTop + bars.top,
                    baseEnd + bars.right, baseBottom + bars.bottom);
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        btnBeginner = findViewById(R.id.btnBeginner);
        btnIntermediate = findViewById(R.id.btnIntermediate);
        btnAdvanced = findViewById(R.id.btnAdvance);
        btnContinue = findViewById(R.id.btnContinue);

        updateCardUI(btnBeginner, LEVEL_BEGINNER, DESC_BEGINNER, false);
        updateCardUI(btnIntermediate, LEVEL_INTERMEDIATE, DESC_INTERMEDIATE, false);
        updateCardUI(btnAdvanced, LEVEL_ADVANCED, DESC_ADVANCED, false);

        btnBeginner.setOnClickListener(v -> selectLevel(LEVEL_BEGINNER));
        btnIntermediate.setOnClickListener(v -> selectLevel(LEVEL_INTERMEDIATE));
        btnAdvanced.setOnClickListener(v -> selectLevel(LEVEL_ADVANCED));

        btnContinue.setOnClickListener(v -> {
            if (selectedLevel == null) {
                Toast.makeText(this, "Vui lòng chọn trình độ", Toast.LENGTH_SHORT).show();
                return;
            }
            btnContinue.setEnabled(false);
            viewModel.setLevel(selectedLevel, new Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    btnContinue.setEnabled(true);
                    goHome();
                }

                @Override
                public void onError(Exception e) {
                    btnContinue.setEnabled(true);
                    Toast.makeText(ChooseLevelActivity.this, RepoErrors.message(e),
                            Toast.LENGTH_LONG).show();
                }
            });
        });

        if (savedInstanceState != null) {
            String restored = savedInstanceState.getString(STATE_SELECTED_LEVEL);
            if (restored != null) {
                selectLevel(restored);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_LEVEL, selectedLevel);
    }

    private void goHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra("level", selectedLevel);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void selectLevel(String level) {
        selectedLevel = level;

        boolean isBeginner = level.equals(LEVEL_BEGINNER);
        boolean isIntermediate = level.equals(LEVEL_INTERMEDIATE);
        boolean isAdvanced = level.equals(LEVEL_ADVANCED);

        btnBeginner.setSelected(isBeginner);
        btnIntermediate.setSelected(isIntermediate);
        btnAdvanced.setSelected(isAdvanced);

        updateCardUI(btnBeginner, LEVEL_BEGINNER, DESC_BEGINNER, isBeginner);
        updateCardUI(btnIntermediate, LEVEL_INTERMEDIATE, DESC_INTERMEDIATE, isIntermediate);
        updateCardUI(btnAdvanced, LEVEL_ADVANCED, DESC_ADVANCED, isAdvanced);
    }

    private void updateCardUI(Button btn, String title, String desc, boolean isSelected) {
        String fullText = title + "\n" + desc;
        SpannableString ss = new SpannableString(fullText);

        ss.setSpan(new AbsoluteSizeSpan(16, true), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(isSelected ? Color.WHITE : Color.parseColor("#333333")),
                0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ss.setSpan(new AbsoluteSizeSpan(13, true), title.length() + 1, fullText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ForegroundColorSpan(isSelected ? Color.parseColor("#E0E0E0")
                        : Color.parseColor("#666666")), title.length() + 1, fullText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        btn.setText(ss);
    }
}
