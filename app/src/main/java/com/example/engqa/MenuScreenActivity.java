package com.example.engqa;

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

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));
        btnMyQuestions.setOnClickListener(v -> showTab(true));
        btnMyAnswers.setOnClickListener(v -> showTab(false));
        findViewById(R.id.btnLogout).setOnClickListener(v -> logout());
        bindBottomNav();

        observe();
    }

    @Override
    protected int selectedNavItem() {
        return NAV_PROFILE;
    }

    @Override
    protected void onStart() {
        super.onStart();
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
            updateEmpty();
        });
        viewModel.getMyAnswers().observe(this, state -> {
            List<Answer> data = state != null ? state.getData() : null;
            answerAdapter.submitList(data != null ? data : Collections.emptyList());
            updateEmpty();
        });
        viewModel.getActionState().observe(this, state -> {
            if (state != null && state.getStatus() == UiState.Status.SUCCESS
                    && "profile_updated".equals(state.getData())) {
                toast("Đã cập nhật hồ sơ");
            }
        });
    }

    private void bindUser(User user) {
        if (user == null) {
            return;
        }
        String name = user.getUsername() != null ? user.getUsername() : "user";
        ((TextView) findViewById(R.id.tvUsername)).setText(name);
        ((TextView) findViewById(R.id.tvEmail)).setText(user.getEmail());
        ((TextView) findViewById(R.id.tvAvatar)).setText(
                name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
        TextView level = findViewById(R.id.tvLevelBadge);
        if (user.getLevel() != null && !user.getLevel().isEmpty()) {
            level.setText(user.getLevel());
            level.setVisibility(View.VISIBLE);
        } else {
            level.setVisibility(View.GONE);
        }
        ((TextView) findViewById(R.id.tvQuestionsAskedCount))
                .setText(String.valueOf(user.getQuestionCount()));
        ((TextView) findViewById(R.id.tvAnswersCount))
                .setText(String.valueOf(user.getAnswerCount()));
        ((TextView) findViewById(R.id.tvAcceptedCount))
                .setText(String.valueOf(user.getAcceptedCount()));
    }

    private void showTab(boolean questions) {
        recyclerQuestions.setVisibility(questions ? View.VISIBLE : View.GONE);
        recyclerAnswers.setVisibility(questions ? View.GONE : View.VISIBLE);
        styleTab(btnMyQuestions, questions);
        styleTab(btnMyAnswers, !questions);
        updateEmpty();
    }

    private void styleTab(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.bg_button_primary : R.drawable.bg_chip);
        tab.setTextColor(ContextCompat.getColor(this,
                active ? R.color.white : R.color.text_secondary));
    }

    private void updateEmpty() {
        boolean questionsEmpty = questionAdapter.getItemCount() == 0
                && recyclerQuestions.getVisibility() == View.VISIBLE;
        boolean answersEmpty = answerAdapter.getItemCount() == 0
                && recyclerAnswers.getVisibility() == View.VISIBLE;
        emptyView.setVisibility((questionsEmpty || answersEmpty) ? View.VISIBLE : View.GONE);
    }

    private void logout() {
        new SessionManager(this).logout();
        toast(getString(R.string.logged_out));
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
