package com.example.engqa;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Interface 1 - "Đặt câu hỏi" (question creation screen). UI prototype only.
 */
public class MainActivity extends BaseNavActivity {

    private EditText etTitle;
    private EditText etDescription;
    private TextView btnAttachImage;
    private TextView btnTakePhoto;
    private Spinner spinnerTag;
    private TextView btnPostQuestion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        btnAttachImage = findViewById(R.id.btnAttachImage);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        spinnerTag = findViewById(R.id.spinnerTag);
        btnPostQuestion = findViewById(R.id.btnPostQuestion);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setupTagSpinner();
        bindBottomNav();

        // TODO: open gallery picker
        btnAttachImage.setOnClickListener(v -> toast("Đính kèm ảnh (chưa triển khai)"));
        // TODO: open camera
        btnTakePhoto.setOnClickListener(v -> toast("Chụp ảnh (chưa triển khai)"));
        // TODO: submit question. For now it just opens Interface 2 so both screens can be inspected.
        btnPostQuestion.setOnClickListener(v ->
                startActivity(new Intent(this, QuestionDetailActivity.class)));
    }

    @Override
    protected int selectedNavItem() {
        return NAV_ASK;
    }

    /** First entry is the hint; it is shown in the placeholder (grey) colour. */
    private void setupTagSpinner() {
        String[] tags = {
                getString(R.string.hint_tag), "Ngữ pháp", "Từ vựng", "Phát âm", "Nghe", "Viết"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, tags) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextSize(14);
                tv.setPadding(dp(8), 0, 0, 0);
                tv.setTextColor(position == 0 ? getColor(R.color.text_hint) : Color.WHITE);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextSize(14);
                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                tv.setTextColor(position == 0 ? getColor(R.color.text_hint) : Color.WHITE);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTag.setAdapter(adapter);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
