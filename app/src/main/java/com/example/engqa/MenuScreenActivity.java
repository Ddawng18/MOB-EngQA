package com.example.engqa;

import java.util.Locale;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.model.Answer;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.ui.AnswerAdapter;
import com.example.engqa.ui.QuestionAdapter;
import com.example.engqa.viewmodel.ProfileViewModel;

import java.util.Collections;
import java.util.List;
import com.example.engqa.util.Navigation;

/** Profile screen: header with stats plus the user's own questions and answers. */
public class MenuScreenActivity extends BaseNavActivity {

    private ProfileViewModel viewModel;
    private QuestionAdapter questionAdapter;
    private AnswerAdapter answerAdapter;
    private TextView btnMyQuestions;
    private TextView btnMyAnswers;
    private TextView emptyView;
    private RecyclerView recyclerQuestions;
    private RecyclerView recyclerAnswers;
    private String uid;
    private String questionsError;
    private String answersError;
    private User boundUser;
    private int liveQuestionCount = -1;
    private int liveAnswerCount = -1;
    private int liveAcceptedCount = -1;
    private boolean showingQuestions = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_menu_screen);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        uid = new SessionManager(this).getUserId();
        if (uid == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        btnMyQuestions = findViewById(R.id.btnMyQuestions);
        btnMyAnswers = findViewById(R.id.btnMyAnswers);
        emptyView = findViewById(R.id.tvMyEmpty);
        recyclerQuestions = findViewById(R.id.recyclerMyQuestions);
        recyclerAnswers = findViewById(R.id.recyclerMyAnswers);

        setupLists();

        Navigation.wireBackToHome(this);
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        btnMyQuestions.setOnClickListener(v -> showTab(true));
        btnMyAnswers.setOnClickListener(v -> showTab(false));
        if (savedInstanceState != null) {
            showTab(savedInstanceState.getBoolean("showingQuestions", true));
        }
        bindBottomNav();

        observe();
    }

    @Override
    protected int selectedNavItem() {
        return NAV_PROFILE;
    }

    @Override
    protected boolean isTabActivity() {
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        String sessionUid = new SessionManager(this).getUserId();
        if (sessionUid == null) {
            // The session ended while this tab was in the back stack: never show the old profile.
            viewModel.stop();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        if (!sessionUid.equals(uid)) {
            uid = sessionUid;
        }
        viewModel.start(uid);
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    private void setupLists() {
        questionAdapter = new QuestionAdapter(question -> {
            Intent intent = new Intent(this, QuestionDetailActivity.class);
            intent.putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID, question.getId());
            startActivity(intent);
        });
        recyclerQuestions.setLayoutManager(new LinearLayoutManager(this));
        recyclerQuestions.setAdapter(questionAdapter);
        recyclerQuestions.setNestedScrollingEnabled(false);

        answerAdapter = new AnswerAdapter(new AnswerAdapter.OnAnswerActionListener() {
            @Override
            public void onVote(Answer answer, int requestedValue) {
            }

            @Override
            public void onAccept(Answer answer) {
            }
        });
        answerAdapter.setVotingEnabled(false);
        answerAdapter.setShowQuestionTitle(true);
        answerAdapter.setOnAnswerOpenListener(answer -> {
            if (answer.getQuestionId() != null) {
                Intent intent = new Intent(this, QuestionDetailActivity.class);
                intent.putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID, answer.getQuestionId());
                startActivity(intent);
            }
        });
        recyclerAnswers.setLayoutManager(new LinearLayoutManager(this));
        recyclerAnswers.setAdapter(answerAdapter);
        recyclerAnswers.setNestedScrollingEnabled(false);
    }

    private void observe() {
        viewModel.getUser().observe(this, state -> {
            if (state != null && state.getStatus() == UiState.Status.SUCCESS) {
                bindUser(state.getData());
            }
        });
        viewModel.getMyQuestions().observe(this, state -> {
            List<Question> data = state != null ? state.getData() : null;
            questionAdapter.submitList(data != null ? data : Collections.emptyList());
            questionsError = state != null && state.getStatus() == UiState.Status.ERROR
                    ? state.getMessage() : null;
            if (state != null && (state.getStatus() == UiState.Status.SUCCESS
                    || state.getStatus() == UiState.Status.EMPTY)) {
                liveQuestionCount = data != null ? data.size() : 0;
                renderStats();
            }
            updateEmpty();
        });
        viewModel.getMyAnswers().observe(this, state -> {
            List<Answer> data = state != null ? state.getData() : null;
            answerAdapter.submitList(data != null ? data : Collections.emptyList());
            answersError = state != null && state.getStatus() == UiState.Status.ERROR
                    ? state.getMessage() : null;
            if (state != null && (state.getStatus() == UiState.Status.SUCCESS
                    || state.getStatus() == UiState.Status.EMPTY)) {
                int accepted = 0;
                if (data != null) {
                    for (Answer answer : data) {
                        if (answer.isAccepted()) {
                            accepted++;
                        }
                    }
                }
                liveAnswerCount = data != null ? data.size() : 0;
                liveAcceptedCount = accepted;
                renderStats();
            }
            updateEmpty();
        });
        viewModel.getActionState().observe(this, state -> {
            if (state != null && state.getStatus() == UiState.Status.SUCCESS
                    && "profile_updated".equals(state.getData())) {
                toast(getString(R.string.profile_updated));
            }
        });
    }

    private void bindUser(User user) {
        if (user == null) {
            return;
        }
        if (boundUser == null || !user.getUid().equals(boundUser.getUid())) {
            liveQuestionCount = -1;
            liveAnswerCount = -1;
            liveAcceptedCount = -1;
        }
        boundUser = user;
        String name = user.getUsername() != null ? user.getUsername() : "user";
        ((TextView) findViewById(R.id.tvUsername)).setText(name);
        ((TextView) findViewById(R.id.tvEmail)).setText(user.getEmail());
        ((TextView) findViewById(R.id.tvAvatar)).setText(
                name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault()));
        TextView level = findViewById(R.id.tvLevelBadge);
        if (user.getLevel() != null && !user.getLevel().isEmpty()) {
            level.setText(user.getLevel());
            level.setVisibility(View.VISIBLE);
        } else {
            level.setVisibility(View.GONE);
        }
        renderStats();
    }

    /**
     * Prefers the live list sizes; falls back to the stored profile counters while those lists are
     * still loading or if their query fails offline.
     */
    private void renderStats() {
        if (boundUser == null) {
            return;
        }
        int questions = liveQuestionCount >= 0
                ? liveQuestionCount : (int) boundUser.getQuestionCount();
        int answers = liveAnswerCount >= 0
                ? liveAnswerCount : (int) boundUser.getAnswerCount();
        int accepted = liveAcceptedCount >= 0
                ? liveAcceptedCount : (int) boundUser.getAcceptedCount();
        ((TextView) findViewById(R.id.tvQuestionsAskedCount))
                .setText(String.valueOf(questions));
        ((TextView) findViewById(R.id.tvAnswersCount)).setText(String.valueOf(answers));
        ((TextView) findViewById(R.id.tvAcceptedCount)).setText(String.valueOf(accepted));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("showingQuestions", showingQuestions);
    }

    private void showTab(boolean questions) {
        showingQuestions = questions;
        recyclerQuestions.setVisibility(questions ? View.VISIBLE : View.GONE);
        recyclerAnswers.setVisibility(questions ? View.GONE : View.VISIBLE);
        styleTab(btnMyQuestions, questions);
        styleTab(btnMyAnswers, !questions);
        updateEmpty();
    }

    private void styleTab(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_chip);
        tab.setTextColor(ContextCompat.getColor(this,
                active ? R.color.accent_orange : R.color.text_secondary));
    }

    private void updateEmpty() {
        boolean questionsTab = recyclerQuestions.getVisibility() == View.VISIBLE;
        int count = questionsTab ? questionAdapter.getItemCount() : answerAdapter.getItemCount();
        String error = questionsTab ? questionsError : answersError;
        if (error != null) {
            emptyView.setText(error);
            emptyView.setVisibility(View.VISIBLE);
        } else if (count == 0) {
            emptyView.setText(R.string.state_empty);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
