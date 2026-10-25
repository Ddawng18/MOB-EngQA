package com.example.engqa;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.media.AudioRecorder;
import com.example.engqa.media.ImageUtils;
import com.example.engqa.media.MediaBudget;
import com.example.engqa.model.Categories;
import com.example.engqa.model.UiState;
import com.example.engqa.ui.AttachmentAdapter;
import com.example.engqa.ui.AudioPlayerView;
import com.example.engqa.util.PermissionHelper;
import com.example.engqa.util.Validators;
import com.example.engqa.viewmodel.CreateQuestionViewModel;

import java.io.File;
import com.example.engqa.util.Navigation;

/** "Đặt câu hỏi" screen: text, reading passage, images, audio and type/level selection. */
public class MainActivity extends BaseNavActivity {

    private CreateQuestionViewModel viewModel;
    private EditText etTitle;
    private EditText etDescription;
    private EditText etPassage;
    private Spinner spinnerTag;
    private Spinner spinnerLevel;
    private AttachmentAdapter attachmentAdapter;
    private AudioPlayerView audioPlayer;
    private TextView btnPost;
    private TextView tvUploadStatus;
    private TextView btnRecordAudio;

    private Uri pendingCameraUri;
    private AudioRecorder audioRecorder;
    private boolean recording;
    private final Handler recordHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoStopRecording = this::stopRecording;

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    addImage(result.getData().getData());
                }
            });

    private final ActivityResultLauncher<Intent> audioLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    setAudio(result.getData().getData());
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && pendingCameraUri != null) {
                    addImage(pendingCameraUri);
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            launchCamera();
                        } else {
                            toast("Cần quyền camera để chụp ảnh");
                        }
                    });

    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startRecording();
                        } else {
                            toast("Cần quyền ghi âm");
                        }
                    });

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

        viewModel = new ViewModelProvider(this).get(CreateQuestionViewModel.class);

        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etPassage = findViewById(R.id.etPassage);
        spinnerTag = findViewById(R.id.spinnerTag);
        spinnerLevel = findViewById(R.id.spinnerLevel);
        btnPost = findViewById(R.id.btnPostQuestion);
        tvUploadStatus = findViewById(R.id.tvUploadStatus);
        btnRecordAudio = findViewById(R.id.btnRecordAudio);
        audioPlayer = findViewById(R.id.audioPlayer);

        setupTagSpinner();
        setupLevelSpinner();
        setupAttachments();

        Navigation.wireBackToHome(this);
        findViewById(R.id.btnAttachImage).setOnClickListener(v -> pickImage());
        findViewById(R.id.btnTakePhoto).setOnClickListener(v -> requestCamera());
        findViewById(R.id.btnAddAudio).setOnClickListener(v -> pickAudio());
        btnRecordAudio.setOnClickListener(v -> onRecordClicked());
        btnPost.setOnClickListener(v -> submit());

        restoreDraft(savedInstanceState);
        observeSubmit();
        bindBottomNav();
    }

    @Override
    protected int selectedNavItem() {
        return NAV_ASK;
    }

    @Override
    protected boolean isTabActivity() {
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Draft text is kept in the ViewModel; this covers process death for the text fields too.
        super.onSaveInstanceState(outState);
        outState.putString("draftTitle", etTitle.getText().toString());
        outState.putString("draftBody", etDescription.getText().toString());
        outState.putString("draftPassage", etPassage.getText().toString());
    }

    private void restoreDraft(Bundle savedInstanceState) {
        // The ViewModel survives rotation; the bundle only matters after process death.
        if (savedInstanceState != null && !viewModel.hasContent()) {
            viewModel.setTitle(savedInstanceState.getString("draftTitle", ""));
            viewModel.setBody(savedInstanceState.getString("draftBody", ""));
            viewModel.setPassageText(savedInstanceState.getString("draftPassage", ""));
        }
        etTitle.setText(viewModel.getTitle());
        etDescription.setText(viewModel.getBody());
        etPassage.setText(viewModel.getPassageText());
        if (viewModel.getAudioUri() != null) {
            audioPlayer.setUrl(viewModel.getAudioUri().toString());
        }
    }

    private void setupAttachments() {
        RecyclerView recycler = findViewById(R.id.recyclerAttachments);
        attachmentAdapter = new AttachmentAdapter(uri -> {
            viewModel.removeImage(uri);
            attachmentAdapter.submit(viewModel.getImageUris());
        });
        recycler.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        recycler.setAdapter(attachmentAdapter);
        attachmentAdapter.submit(viewModel.getImageUris());
    }

    private void setupTagSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, Categories.NAMES) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(getColor(R.color.text_primary));
                tv.setTextSize(14);
                tv.setPadding(dp(8), 0, 0, 0);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(getColor(R.color.text_primary));
                tv.setTextSize(14);
                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTag.setAdapter(adapter);
    }

    private void setupLevelSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.levels)) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(getColor(R.color.text_primary));
                tv.setTextSize(14);
                tv.setPadding(dp(8), 0, 0, 0);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLevel.setAdapter(adapter);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        audioLauncher.launch(intent);
    }

    private void requestCamera() {
        if (PermissionHelper.hasCamera(this)) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            pendingCameraUri = ImageUtils.createImageCaptureUri(this);
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(intent);
        } catch (Exception e) {
            toast("Không mở được camera");
        }
    }

    private void addImage(Uri uri) {
        if (uri == null) {
            return;
        }
        String mime = ImageUtils.mimeType(this, uri);
        long size = ImageUtils.sizeBytes(this, uri);
        if (!Validators.isAllowedImageType(mime)) {
            toast("Chỉ hỗ trợ ảnh JPG/PNG");
            return;
        }
        if (size > Validators.MAX_IMAGE_BYTES) {
            toast("Ảnh vượt quá 5MB");
            return;
        }
        if (viewModel.getImageUris().size() >= MediaBudget.MAX_IMAGES) {
            toast(getString(R.string.image_limit));
            return;
        }
        viewModel.addImage(uri);
        attachmentAdapter.submit(viewModel.getImageUris());
    }

    private void setAudio(Uri uri) {
        if (uri == null) {
            return;
        }
        long size = ImageUtils.sizeBytes(this, uri);
        if (size > MediaBudget.MAX_AUDIO_BYTES) {
            toast(getString(R.string.audio_too_large));
            return;
        }
        viewModel.setAudioUri(uri);
        audioPlayer.setUrl(uri.toString());
    }

    private void onRecordClicked() {
        if (recording) {
            stopRecording();
            return;
        }
        if (PermissionHelper.hasRecordAudio(this)) {
            startRecording();
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startRecording() {
        try {
            audioRecorder = new AudioRecorder();
            audioRecorder.start(this);
            recording = true;
            btnRecordAudio.setText(R.string.btn_stop_record);
            recordHandler.removeCallbacks(autoStopRecording);
            recordHandler.postDelayed(autoStopRecording,
                    MediaBudget.MAX_AUDIO_SECONDS * 1000L);
        } catch (Exception e) {
            audioRecorder = null;
            recording = false;
            toast("Không bắt đầu ghi âm được");
        }
    }

    private void stopRecording() {
        recordHandler.removeCallbacks(autoStopRecording);
        recording = false;
        btnRecordAudio.setText(R.string.btn_record_audio);
        if (audioRecorder == null) {
            return;
        }
        File file = audioRecorder.stop();
        audioRecorder = null;
        if (file != null) {
            viewModel.setAudioUri(Uri.fromFile(file));
            audioPlayer.setUrl(Uri.fromFile(file).toString());
        }
    }

    private void submit() {
        String uid = new SessionManager(this).getUserId();
        if (uid == null) {
            toast("Bạn cần đăng nhập");
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        viewModel.setTitle(etTitle.getText().toString().trim());
        viewModel.setBody(etDescription.getText().toString().trim());
        viewModel.setPassageText(etPassage.getText().toString().trim());
        int tagIndex = spinnerTag.getSelectedItemPosition();
        viewModel.setCategory(Categories.SLUGS[tagIndex], Categories.NAMES[tagIndex]);
        viewModel.setLevel((String) spinnerLevel.getSelectedItem());

        btnPost.setEnabled(false);
        viewModel.submit();
    }

    private void observeSubmit() {
        viewModel.getSubmitState().observe(this, state -> {
            if (state == null) {
                return;
            }
            switch (state.getStatus()) {
                case LOADING:
                    btnPost.setEnabled(false);
                    tvUploadStatus.setVisibility(View.VISIBLE);
                    break;
                case SUCCESS:
                    btnPost.setEnabled(true);
                    tvUploadStatus.setVisibility(View.GONE);
                    if ("pending_saved".equals(state.getData())) {
                        toast(getString(R.string.pending_saved));
                    } else {
                        toast(getString(R.string.post_success));
                        Intent intent = new Intent(this, QuestionDetailActivity.class);
                        intent.putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID,
                                state.getData());
                        startActivity(intent);
                    }
                    finish();
                    break;
                case ERROR:
                default:
                    btnPost.setEnabled(true);
                    tvUploadStatus.setVisibility(View.GONE);
                    toast(state.getMessage());
                    break;
            }
        });
        viewModel.getUploadProgress().observe(this, percent -> {
            if (percent != null && percent > 0) {
                tvUploadStatus.setText(getString(R.string.uploading, percent));
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        recordHandler.removeCallbacks(autoStopRecording);
        if (recording && audioRecorder != null) {
            audioRecorder.cancel();
            audioRecorder = null;
            recording = false;
            btnRecordAudio.setText(R.string.btn_record_audio);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
