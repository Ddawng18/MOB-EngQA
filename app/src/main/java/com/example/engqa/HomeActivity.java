package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.data.QuestionRepository;
import com.example.engqa.model.Category;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.ui.CategoryChipAdapter;
import com.example.engqa.ui.QuestionAdapter;
import com.example.engqa.ui.StateView;
import com.example.engqa.viewmodel.HomeViewModel;

import java.util.ArrayList;
import java.util.List;

/** Feed screen: realtime question list, category filter, sort, keyword search and paging. */
public class HomeActivity extends BaseNavActivity {

    private HomeViewModel viewModel;
    private QuestionAdapter questionAdapter;
    private CategoryChipAdapter categoryAdapter;
    private CategoryChipAdapter sortAdapter;
    private StateView stateView;
    private ProgressBar progressMore;
    private TextView cacheHint;
    private RecyclerView recyclerQuestions;
    private boolean loadingMore;

    private static final String[][ ] SORTS = {
            {"newest", "Mới nhất"},
            {"popular", "Phổ biến"},
            {"unanswered", "Chưa trả lời"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        stateView = findViewById(R.id.stateView);
        progressMore = findViewById(R.id.progressMore);
        cacheHint = findViewById(R.id.tvCacheHint);
        recyclerQuestions = findViewById(R.id.recyclerQuestions);

        setupFeed();
        setupCategoryChips();
        setupSortChips();
        setupSearch();

        findViewById(R.id.btnNotifications).setOnClickListener(v -> {
            if (new SessionManager(this).isLoggedIn()) {
                startActivity(new Intent(this, NotificationActivity.class));
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });
        findViewById(R.id.btnProfile).setOnClickListener(v -> onProfileClicked());

        stateView.setOnRetryListener(v -> {
            viewModel.stop();
            viewModel.start();
        });
        bindBottomNav();
    }

    @Override
    protected int selectedNavItem() {
        return NAV_HOME;
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    private void setupFeed() {
        questionAdapter = new QuestionAdapter(this::openQuestion);
        recyclerQuestions.setLayoutManager(new LinearLayoutManager(this));
        recyclerQuestions.setAdapter(questionAdapter);
        recyclerQuestions.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loadingMore) {
                    return;
                }
                LinearLayoutManager manager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager == null) {
                    return;
                }
                if (manager.findLastVisibleItemPosition()
                        >= questionAdapter.getItemCount() - 3) {
                    viewModel.loadMore();
                }
            }
        });

        viewModel.getFeed().observe(this, this::renderFeed);
        viewModel.getLoadingMore().observe(this, more ->
                progressMore.setVisibility(Boolean.TRUE.equals(more) ? View.VISIBLE : View.GONE));
    }

    private void renderFeed(UiState<List<Question>> state) {
        if (state == null) {
            return;
        }
        switch (state.getStatus()) {
            case LOADING:
                stateView.showLoading();
                recyclerQuestions.setVisibility(View.GONE);
                break;
            case SUCCESS:
                stateView.showContent();
                recyclerQuestions.setVisibility(View.VISIBLE);
                questionAdapter.submitList(state.getData());
                cacheHint.setVisibility(state.isFromCache() ? View.VISIBLE : View.GONE);
                break;
            case EMPTY:
                stateView.showEmpty();
                recyclerQuestions.setVisibility(View.GONE);
                break;
            case ERROR:
            default:
                stateView.showError(state.getMessage());
                recyclerQuestions.setVisibility(View.GONE);
                break;
        }
    }

    private void setupCategoryChips() {
        RecyclerView recycler = findViewById(R.id.recyclerCategories);
        categoryAdapter = new CategoryChipAdapter(null, category -> {
            viewModel.setCategory(category.getId());
            categoryAdapter.setSelectedId(category.getId());
        });
        recycler.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        recycler.setAdapter(categoryAdapter);

        viewModel.getCategories().observe(this, categories -> {
            List<Category> items = new ArrayList<>();
            items.add(new Category(null, getString(R.string.filter_all), 0));
            if (categories != null) {
                items.addAll(categories);
            }
            categoryAdapter.submitList(items);
        });
    }

    private void setupSortChips() {
        RecyclerView recycler = findViewById(R.id.recyclerSort);
        sortAdapter = new CategoryChipAdapter(QuestionRepository.SORT_NEWEST, category -> {
            viewModel.setSort(category.getId());
            sortAdapter.setSelectedId(category.getId());
        });
        recycler.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        recycler.setAdapter(sortAdapter);

        List<Category> items = new ArrayList<>();
        for (String[] sort : SORTS) {
            items.add(new Category(sort[0], sort[1], 0));
        }
        sortAdapter.submitList(items);
    }

    private void setupSearch() {
        EditText search = findViewById(R.id.etSearch);
        search.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence text) {
                viewModel.setKeyword(text.toString());
            }
        });
    }

    private void openQuestion(Question question) {
        Intent intent = new Intent(this, QuestionDetailActivity.class);
        intent.putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID, question.getId());
        startActivity(intent);
    }

    private void onProfileClicked() {
        if (!new SessionManager(this).isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        startActivity(new Intent(this, MenuScreenActivity.class));
    }

    private abstract static class SimpleTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public final void onTextChanged(CharSequence s, int start, int before, int count) {
            onTextChanged(s);
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
        }

        public abstract void onTextChanged(CharSequence text);
    }
}
