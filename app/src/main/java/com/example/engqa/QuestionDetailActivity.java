package com.example.engqa;

import java.util.Locale;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.media.MediaCompressor;
import com.example.engqa.model.Answer;
import com.example.engqa.model.Question;
import com.example.engqa.model.StoredMedia;
import com.example.engqa.model.UiState;
import com.example.engqa.ui.AnswerAdapter;
import com.example.engqa.ui.AudioPlayerView;
import com.example.engqa.ui.ImageAdapter;
import com.example.engqa.ui.MediaImageAdapter;
import com.example.engqa.ui.StateView;
import com.example.engqa.util.ReportTargets;
import com.example.engqa.util.TimeUtils;
import com.example.engqa.viewmodel.AdminViewModel;
import com.example.engqa.viewmodel.QuestionDetailViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.engqa.util.Navigation;

/** Question detail: realtime question + answers, up/down vote, accept, media and reporting. */
public class QuestionDetailActivity extends BaseNavActivity {

    public static final String EXTRA_QUESTION_ID = "com.example.engqa.extra.QUESTION_ID";
    /** Set by the moderation console so the reported answer is brought into view. */
    public static final String EXTRA_FOCUS_ANSWER_ID = "com.example.engqa.extra.FOCUS_ANSWER_ID";

    private QuestionDetailViewModel viewModel;
    private AnswerAdapter answerAdapter;
    private ImageAdapter imageAdapter;
    private MediaImageAdapter mediaImageAdapter;
    private StateView stateView;
    private AudioPlayerView audioPlayer;
    private TextView tvAnswersHeader;
    private EditText etAnswer;
    private TextView btnSend;
    private RecyclerView recyclerAnswers;
    private String questionId;
    private String uid;
    private String focusAnswerId;
    private boolean focusAnswerHandled;
    private boolean isAdmin;
    private Question currentQuestion;
    private List<StoredMedia> currentMedia = new ArrayList<>();
    private StoredMedia currentAudio;
    private final ExecutorService mediaExecutor = Executors.newSingleThreadExecutor();

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

        questionId = getIntent().getStringExtra(EXTRA_QUESTION_ID);
        if (TextUtils.isEmpty(questionId)) {
            toast("Không tìm thấy câu hỏi");
            finish();
            return;
        }
        focusAnswerId = getIntent().getStringExtra(EXTRA_FOCUS_ANSWER_ID);
        uid = new SessionManager(this).getUserId();

        viewModel = new ViewModelProvider(this).get(QuestionDetailViewModel.class);
        // Admins get the same delete action on reported content; the rules already allow it.
        AdminViewModel adminViewModel = new ViewModelProvider(this).get(AdminViewModel.class);
        adminViewModel.getIsAdmin().observe(this,
                allowed -> isAdmin = Boolean.TRUE.equals(allowed));
        adminViewModel.checkAdmin();
        stateView = findViewById(R.id.stateView);
        audioPlayer = findViewById(R.id.audioPlayer);
        tvAnswersHeader = findViewById(R.id.tvAnswersHeader);
        etAnswer = findViewById(R.id.etAnswer);

        setupAnswers();
        setupImages();

        Navigation.wireBack(this);
        btnSend = findViewById(R.id.btnSend);
        btnSend.setOnClickListener(v -> submitAnswer());
        findViewById(R.id.btnMore).setOnClickListener(this::showOptionsMenu);
        stateView.setOnRetryListener(v -> {
            viewModel.stop();
            viewModel.start(questionId, uid);
        });
        bindBottomNav();

        observe();
    }

    @Override
    protected int selectedNavItem() {
        return NAV_HOME;
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.start(questionId, uid);
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaExecutor.shutdownNow();
    }

    private void setupAnswers() {
        answerAdapter = new AnswerAdapter(new AnswerAdapter.OnAnswerActionListener() {
            @Override
            public void onVote(Answer answer, int requestedValue) {
                viewModel.vote(answer.getId(), requestedValue);
            }

            @Override
            public void onAccept(Answer answer) {
                viewModel.accept(answer.getId());
            }
        });
        answerAdapter.setContext(
                currentQuestion != null ? currentQuestion.getAuthorId() : null, uid);
        answerAdapter.setOnAnswerReportListener(this::showAnswerReportDialog);
        recyclerAnswers = findViewById(R.id.recyclerAnswers);
        recyclerAnswers.setLayoutManager(new LinearLayoutManager(this));
        recyclerAnswers.setAdapter(answerAdapter);
        recyclerAnswers.setNestedScrollingEnabled(false);
    }

    private void setupImages() {
        // Detail images are shown large: full width, one per row, original aspect ratio. Tapping
        // one still opens the zoomable viewer.
        imageAdapter = new ImageAdapter(url -> {
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_IMAGE_URL, url);
            startActivity(intent);
        }, R.layout.item_detail_image);
        mediaImageAdapter = new MediaImageAdapter(media -> {
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_QUESTION_ID, questionId);
            intent.putExtra(ImageViewerActivity.EXTRA_MEDIA_ID, media.getId());
            startActivity(intent);
        }, R.layout.item_detail_image);
        RecyclerView recycler = findViewById(R.id.recyclerImages);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(imageAdapter);
    }

    private void observe() {
        viewModel.getQuestion().observe(this, this::renderQuestion);
        viewModel.getAnswers().observe(this, this::renderAnswers);
        viewModel.getMedia().observe(this, this::renderMedia);
        viewModel.getMyVotes().observe(this, votes -> {
            answerAdapter.setMyVotes(votes);
            answerAdapter.notifyDataSetChanged();
        });
        viewModel.getActionState().observe(this, state -> {
            if (state == null) {
                return;
            }
            switch (state.getStatus()) {
                case LOADING:
                    btnSend.setEnabled(false);
                    break;
                case SUCCESS:
                    btnSend.setEnabled(true);
                    if ("question_deleted".equals(state.getData())) {
                        finish();
                    } else if ("deleted".equals(state.getData())) {
                        toast(getString(R.string.answer_deleted));
                    } else if ("answer_posted".equals(state.getData())) {
                        etAnswer.setText("");
                        toast(getString(R.string.answer_posted));
                    } else {
                        toast(getString(R.string.accepted_success));
                    }
                    break;
                case ERROR:
                    btnSend.setEnabled(true);
                    toast(state.getMessage());
                    break;
                default:
                    break;
            }
        });
    }

    private void renderQuestion(UiState<Question> state) {
        if (state == null) {
            return;
        }
        switch (state.getStatus()) {
            case LOADING:
                stateView.showLoading();
                break;
            case SUCCESS:
                stateView.showContent();
                bindQuestion(state.getData());
                break;
            case ERROR:
            default:
                stateView.showError(state.getMessage());
                break;
        }
    }

    private void bindQuestion(Question question) {
        currentQuestion = question;
        String name = question.getAuthorName() != null ? question.getAuthorName() : "user";
        ((TextView) findViewById(R.id.tvQuestionAvatar)).setText(
                name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault()));
        ((TextView) findViewById(R.id.tvQuestionUser)).setText(name);
        ((TextView) findViewById(R.id.tvQuestionTime)).setText(
                TimeUtils.relative(question.getCreatedAt()));
        ((TextView) findViewById(R.id.tvQuestionTag)).setText(
                question.getCategoryName() != null
                        ? question.getCategoryName() : question.getCategoryId());
        ((TextView) findViewById(R.id.tvQuestionTitle)).setText(question.getTitle());
        ((TextView) findViewById(R.id.tvQuestionBody)).setText(question.getBody());
        findViewById(R.id.tvQuestionSolved).setVisibility(
                question.isSolved() ? View.VISIBLE : View.GONE);

        TextView passage = findViewById(R.id.tvPassage);
        if (!TextUtils.isEmpty(question.getPassageText())) {
            passage.setText(question.getPassageText());
            passage.setVisibility(View.VISIBLE);
        } else {
            passage.setVisibility(View.GONE);
        }

        renderImages();
        renderAudio();

        answerAdapter.setContext(question.getAuthorId(), uid);
        answerAdapter.setVotingEnabled(uid != null);
    }

    private void renderMedia(List<StoredMedia> media) {
        currentMedia = media != null ? media : new ArrayList<>();
        currentAudio = null;
        for (StoredMedia item : currentMedia) {
            if (item.isAudio()) {
                currentAudio = item;
                break;
            }
        }
        renderImages();
        renderAudio();
    }

    private void renderImages() {
        List<StoredMedia> images = new ArrayList<>();
        for (StoredMedia item : currentMedia) {
            if (item.isImage()) {
                images.add(item);
            }
        }
        RecyclerView recycler = findViewById(R.id.recyclerImages);
        if (!images.isEmpty()) {
            if (recycler.getAdapter() != mediaImageAdapter) {
                recycler.setAdapter(mediaImageAdapter);
            }
            mediaImageAdapter.submit(images);
        } else {
            if (recycler.getAdapter() != imageAdapter) {
                recycler.setAdapter(imageAdapter);
            }
            imageAdapter.submit(currentQuestion != null
                    ? currentQuestion.getImageUrls() : null);
        }
    }

    private void renderAudio() {
        if (currentAudio != null) {
            final StoredMedia audio = currentAudio;
            mediaExecutor.execute(() -> {
                try {
                    String name = "remote_" + audio.getId() + ".m4a";
                    File file = MediaCompressor.writeToCache(this, audio.getData(), name);
                    runOnUiThread(() -> audioPlayer.setUrl(Uri.fromFile(file).toString()));
                } catch (Exception e) {
                    runOnUiThread(() -> audioPlayer.setUrl(null));
                }
            });
        } else if (currentQuestion != null && currentQuestion.getAudioUrl() != null) {
            audioPlayer.setUrl(currentQuestion.getAudioUrl());
        } else {
            audioPlayer.setUrl(null);
        }
    }

    private void renderAnswers(UiState<List<Answer>> state) {
        if (state == null) {
            return;
        }
        List<Answer> data = state.getData();
        int count = data != null ? data.size() : 0;
        tvAnswersHeader.setText(count > 0
                ? getString(R.string.answers_header, count)
                : getString(R.string.answers_header_zero));
        if (state.getStatus() == UiState.Status.SUCCESS) {
            answerAdapter.submitList(data, this::focusReportedAnswer);
        } else if (state.getStatus() == UiState.Status.EMPTY) {
            answerAdapter.submitList(java.util.Collections.emptyList(), this::focusReportedAnswer);
        }
    }

    /** When opened from the moderation queue, scroll to the answer that was reported. */
    private void focusReportedAnswer() {
        if (focusAnswerHandled || TextUtils.isEmpty(focusAnswerId)) {
            return;
        }
        int position = answerAdapter.indexOf(focusAnswerId);
        if (position >= 0) {
            focusAnswerHandled = true;
            recyclerAnswers.post(() -> recyclerAnswers.scrollToPosition(position));
        }
    }

    private void submitAnswer() {
        if (uid == null) {
            toast("Bạn cần đăng nhập");
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        viewModel.addAnswer(etAnswer.getText().toString());
    }

    private void showOptionsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.report);
        if (currentQuestion != null && uid != null
                && (uid.equals(currentQuestion.getAuthorId()) || isAdmin)) {
            popup.getMenu().add(0, 2, 1, R.string.delete);
        }
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showReportDialog();
                return true;
            }
            if (item.getItemId() == 2) {
                confirmDeleteQuestion();
                return true;
            }
            return false;
        });
        popup.show();
    }

    /** Destructive actions always ask first; the answers and media are removed as well. */
    private void confirmDeleteQuestion() {
        String message = currentQuestion != null && currentQuestion.getTitle() != null
                ? getString(R.string.delete_question_message, currentQuestion.getTitle())
                : getString(R.string.delete_question_message_generic);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.delete_question_title)
                .setMessage(message)
                .setPositiveButton(R.string.delete, (dialog, which) -> viewModel.deleteQuestion())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showReportDialog() {
        if (uid == null) {
            toast("Bạn cần đăng nhập");
            return;
        }
        ReportDialogFragment fragment = ReportDialogFragment.newInstance(
                ReportTargets.questionPath(questionId));
        fragment.show(getSupportFragmentManager(), "report");
    }

    /** Reports one answer, not the whole question; the parent id is required by the target path. */
    private void showAnswerReportDialog(Answer answer) {
        if (uid == null) {
            toast(getString(R.string.login_required));
            return;
        }
        if (answer == null || TextUtils.isEmpty(answer.getId())) {
            toast(getString(R.string.report_answer_missing));
            return;
        }
        // The answer is rendered from this question's subcollection, so that path is authoritative.
        String parentQuestionId = !TextUtils.isEmpty(questionId)
                ? questionId : answer.getQuestionId();
        if (TextUtils.isEmpty(parentQuestionId)) {
            toast(getString(R.string.report_target_missing));
            return;
        }
        ReportDialogFragment.newInstance(
                        ReportTargets.answerPath(parentQuestionId, answer.getId()),
                        R.string.report_answer)
                .show(getSupportFragmentManager(), "report_answer");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
