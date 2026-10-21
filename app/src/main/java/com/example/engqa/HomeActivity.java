package com.example.engqa;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.data.Callback;
import com.example.engqa.data.QuestionRepository;
import com.example.engqa.data.UserRepository;
import com.example.engqa.model.Category;
import com.example.engqa.model.FeedItem;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.ui.CategoryChipAdapter;
import com.example.engqa.ui.FeedAdapter;
import com.example.engqa.ui.StateView;
import com.example.engqa.util.FeedMixer;
import com.example.engqa.util.PermissionHelper;
import com.example.engqa.viewmodel.FeedViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Home feed: a single RecyclerView that interleaves Q&amp;A questions and approved centre promos,
 * with category chips (including "Trung tâm"), sort, keyword search, paging and a role-aware
 * profile {@link PopupMenu}.
 */
public class HomeActivity extends BaseNavActivity {

    private FeedViewModel viewModel;
    private FeedAdapter feedAdapter;
    private CategoryChipAdapter categoryAdapter;
    private CategoryChipAdapter sortAdapter;
    private StateView stateView;
    private ProgressBar progressMore;
    private TextView cacheHint;
    private RecyclerView recyclerView;
    private RecyclerView sortRecycler;

    private final UserRepository userRepository = new UserRepository();
    private User currentUser;

    private static boolean notificationPermissionRequestedThisSession = false;

    private static final String[][] SORTS = {
            {QuestionRepository.SORT_NEWEST, "Mới nhất"},
            {QuestionRepository.SORT_POPULAR, "Phổ biến"},
            {QuestionRepository.SORT_UNANSWERED, "Chưa trả lời"}
    };

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Permission result handled
            });

    private final ActivityResultLauncher<Intent> createPromoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // Toast is already shown inside CreatePromoActivity
            });

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

        viewModel = new ViewModelProvider(this).get(FeedViewModel.class);
        stateView = findViewById(R.id.stateView);
        progressMore = findViewById(R.id.progressMore);
        cacheHint = findViewById(R.id.tvCacheHint);
        recyclerView = findViewById(R.id.recyclerQuestions);
        sortRecycler = findViewById(R.id.recyclerSort);

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
        View profileButton = findViewById(R.id.btnProfile);
        profileButton.setOnClickListener(this::showProfileMenu);

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
        loadCurrentUser();
        checkNotificationPermission();
    }

    private void checkNotificationPermission() {
        if (!notificationPermissionRequestedThisSession
                && new SessionManager(this).isLoggedIn()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !PermissionHelper.hasNotifications(this)) {
            notificationPermissionRequestedThisSession = true;
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    private void loadCurrentUser() {
        String uid = userRepository.currentUid();
        if (uid == null) {
            currentUser = null;
            return;
        }
        userRepository.getUser(uid, new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
            }

            @Override
            public void onError(Exception e) {
                // Keep the previous value; the menu falls back to the visitor state.
            }
        });
    }

    // ------------------------------------------------------------------ feed

    private void setupFeed() {
        feedAdapter = new FeedAdapter(new FeedAdapter.OnFeedActionListener() {
            @Override
            public void onQuestionClick(Question question) {
                openQuestion(question);
            }

            @Override
            public void onPromoClick(Promo promo) {
                openPromo(promo);
            }
        });

        int columns = Math.max(1, getResources().getInteger(R.integer.feed_columns));
        recyclerView.setLayoutManager(new GridLayoutManager(this, columns));
        recyclerView.setAdapter(feedAdapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || Boolean.TRUE.equals(viewModel.getLoadingMore().getValue())) {
                    return;
                }
                RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
                int lastVisible = -1;
                if (manager instanceof GridLayoutManager) {
                    lastVisible = ((GridLayoutManager) manager).findLastVisibleItemPosition();
                } else if (manager instanceof LinearLayoutManager) {
                    lastVisible = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
                }
                if (lastVisible >= feedAdapter.getItemCount() - 3) {
                    viewModel.loadMore();
                }
            }
        });

        viewModel.getFeed().observe(this, this::renderFeed);
        viewModel.getLoadingMore().observe(this, more ->
                progressMore.setVisibility(Boolean.TRUE.equals(more) ? View.VISIBLE : View.GONE));
    }

    private void renderFeed(UiState<List<FeedItem>> state) {
        if (state == null) {
            return;
        }
        switch (state.getStatus()) {
            case LOADING:
                stateView.showLoading();
                recyclerView.setVisibility(View.GONE);
                break;
            case SUCCESS:
                stateView.showContent();
                recyclerView.setVisibility(View.VISIBLE);
                feedAdapter.submitList(state.getData());
                cacheHint.setVisibility(state.isFromCache() ? View.VISIBLE : View.GONE);
                break;
            case EMPTY:
                stateView.showEmpty();
                recyclerView.setVisibility(View.GONE);
                break;
            case ERROR:
            default:
                stateView.showError(state.getMessage());
                recyclerView.setVisibility(View.GONE);
                break;
        }
    }

    // ------------------------------------------------------------------ filters

    private void setupCategoryChips() {
        RecyclerView recycler = findViewById(R.id.recyclerCategories);
        categoryAdapter = new CategoryChipAdapter(viewModel.getCategoryId(), category -> {
            viewModel.setCategory(category.getId());
            categoryAdapter.setSelectedId(category.getId());
            sortRecycler.setVisibility(FeedMixer.FILTER_CENTER.equals(category.getId())
                    ? View.GONE : View.VISIBLE);
        });
        recycler.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        recycler.setAdapter(categoryAdapter);

        viewModel.getCategories().observe(this, categories -> {
            List<Category> items = new ArrayList<>();
            items.add(new Category(null, getString(R.string.filter_all), 0));
            if (categories != null) {
                items.addAll(categories);
            }
            items.add(new Category(FeedMixer.FILTER_CENTER,
                    getString(R.string.promo_filter_center), Integer.MAX_VALUE));
            categoryAdapter.submitList(items);
            categoryAdapter.setSelectedId(viewModel.getCategoryId());
            sortRecycler.setVisibility(
                    FeedMixer.FILTER_CENTER.equals(viewModel.getCategoryId())
                            ? View.GONE : View.VISIBLE);
        });
    }

    private void setupSortChips() {
        sortAdapter = new CategoryChipAdapter(QuestionRepository.SORT_NEWEST, category -> {
            viewModel.setSort(category.getId());
            sortAdapter.setSelectedId(category.getId());
        });
        sortRecycler.setLayoutManager(
                new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        sortRecycler.setAdapter(sortAdapter);

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

    // ------------------------------------------------------------------ navigation

    private void openQuestion(Question question) {
        Intent intent = new Intent(this, QuestionDetailActivity.class);
        intent.putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID, question.getId());
        startActivity(intent);
    }

    private void openPromo(Promo promo) {
        Intent intent = new Intent(this, PromoDetailActivity.class);
        intent.putExtra(PromoDetailActivity.EXTRA_PROMO_ID, promo.getPromoId());
        startActivity(intent);
    }

    /** Profile menu: login / create promo (centres only) / center list / saved / logout. */
    private void showProfileMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_profile, popup.getMenu());

        boolean loggedIn = new SessionManager(this).isLoggedIn();
        boolean isCenter = currentUser != null
                && (User.ROLE_CENTER.equals(currentUser.getRole())
                    || currentUser.getCenterId() != null);

        popup.getMenu().findItem(R.id.action_login).setVisible(!loggedIn);
        popup.getMenu().findItem(R.id.action_create_promo).setVisible(loggedIn && isCenter);
        if (popup.getMenu().findItem(R.id.action_center_list) != null) {
            popup.getMenu().findItem(R.id.action_center_list).setVisible(true);
        }
        popup.getMenu().findItem(R.id.action_saved_promos).setVisible(true);
        popup.getMenu().findItem(R.id.action_logout).setVisible(loggedIn);

        popup.setOnMenuItemClickListener(this::onProfileMenuClick);
        popup.show();
    }

    private boolean onProfileMenuClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_login) {
            startActivity(new Intent(this, LoginActivity.class));
            return true;
        }
        if (id == R.id.action_create_promo) {
            createPromoLauncher.launch(new Intent(this, CreatePromoActivity.class));
            return true;
        }
        if (id == R.id.action_center_list) {
            startActivity(new Intent(this, CenterListActivity.class));
            return true;
        }
        if (id == R.id.action_saved_promos) {
            startActivity(new Intent(this, SavedPromosActivity.class));
            return true;
        }
        if (id == R.id.action_logout) {
            new SessionManager(this).logout();
            Toast.makeText(this, R.string.logged_out, Toast.LENGTH_SHORT).show();
            recreate();
            return true;
        }
        return false;
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
