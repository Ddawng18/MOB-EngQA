package com.example.engqa;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.model.Center;
import com.example.engqa.model.CenterPost;
import com.example.engqa.model.Course;
import com.example.engqa.model.UiState;
import com.example.engqa.viewmodel.CenterViewModel;

/** Manager screen to edit the centre profile and add courses or posts. */
public class CenterEditActivity extends AppCompatActivity {

    public static final String EXTRA_CENTER_ID = "com.example.engqa.extra.CENTER_ID";

    private CenterViewModel viewModel;
    private EditText etName;
    private EditText etDescription;
    private EditText etAddress;
    private EditText etPhone;
    private EditText etEmail;
    private EditText etCourseTitle;
    private EditText etCourseLevel;
    private EditText etCourseSchedule;
    private EditText etPostTitle;
    private EditText etPostBody;
    private String centerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_center_edit);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        centerId = getIntent().getStringExtra(EXTRA_CENTER_ID);
        if (centerId == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(CenterViewModel.class);
        etName = findViewById(R.id.etCenterName);
        etDescription = findViewById(R.id.etCenterDescription);
        etAddress = findViewById(R.id.etCenterAddress);
        etPhone = findViewById(R.id.etCenterPhone);
        etEmail = findViewById(R.id.etCenterEmail);
        etCourseTitle = findViewById(R.id.etCourseTitle);
        etCourseLevel = findViewById(R.id.etCourseLevel);
        etCourseSchedule = findViewById(R.id.etCourseSchedule);
        etPostTitle = findViewById(R.id.etPostTitle);
        etPostBody = findViewById(R.id.etPostBody);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSaveCenter).setOnClickListener(v -> saveCenter());
        findViewById(R.id.btnAddCourse).setOnClickListener(v -> addCourse());
        findViewById(R.id.btnAddPost).setOnClickListener(v -> addPost());

        viewModel.getCenter().observe(this, state -> {
            if (state != null && state.getStatus() == UiState.Status.SUCCESS) {
                prefill(state.getData());
            }
        });
        viewModel.getActionState().observe(this, state -> {
            if (state == null || state.getStatus() != UiState.Status.SUCCESS) {
                if (state != null && state.getStatus() == UiState.Status.ERROR) {
                    toast(state.getMessage());
                }
                return;
            }
            switch (String.valueOf(state.getData())) {
                case "course_added":
                    toast("Đã thêm khóa học");
                    etCourseTitle.setText("");
                    etCourseLevel.setText("");
                    etCourseSchedule.setText("");
                    break;
                case "post_added":
                    toast("Đã đăng bài viết");
                    etPostTitle.setText("");
                    etPostBody.setText("");
                    break;
                default:
                    toast("Đã lưu trung tâm");
                    finish();
                    break;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.startDetail(centerId);
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    private void prefill(Center center) {
        if (center == null) {
            return;
        }
        etName.setText(center.getName());
        etDescription.setText(center.getDescription());
        etAddress.setText(center.getAddress());
        etPhone.setText(center.getPhone());
        etEmail.setText(center.getEmail());
    }

    private void saveCenter() {
        Center center = new Center();
        center.setName(etName.getText().toString().trim());
        center.setDescription(etDescription.getText().toString().trim());
        center.setAddress(etAddress.getText().toString().trim());
        center.setPhone(etPhone.getText().toString().trim());
        center.setEmail(etEmail.getText().toString().trim());
        viewModel.updateCenter(center);
    }

    private void addCourse() {
        String title = etCourseTitle.getText().toString().trim();
        if (title.isEmpty()) {
            toast("Nhập tên khóa học");
            return;
        }
        viewModel.addCourse(new Course(title,
                "Khóa học tại trung tâm",
                etCourseLevel.getText().toString().trim(),
                etCourseSchedule.getText().toString().trim()));
    }

    private void addPost() {
        String title = etPostTitle.getText().toString().trim();
        if (title.isEmpty()) {
            toast("Nhập tiêu đề bài viết");
            return;
        }
        viewModel.addPost(new CenterPost(title, etPostBody.getText().toString().trim(), null));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
