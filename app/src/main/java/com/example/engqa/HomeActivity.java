package com.example.engqa;

import java.util.Locale;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.NetworkUtils;
import com.example.engqa.data.QuestionRepository;
import com.example.engqa.model.AppNotification;
import com.example.engqa.model.Category;
import com.example.engqa.model.FeedItem;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.model.WordEntry;
import com.example.engqa.notification.NotificationWatcher;
import com.example.engqa.ui.CategoryChipAdapter;
import com.example.engqa.ui.FeedAdapter;
import com.example.engqa.ui.StateView;
import com.example.engqa.util.FeedMixer;
import com.example.engqa.util.PermissionHelper;
import com.example.engqa.util.PrefsHelper;
import com.example.engqa.viewmodel.FeedViewModel;
import com.example.engqa.viewmodel.NotificationViewModel;
import com.example.engqa.worker.PendingQuestionWorker;

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
    private RecyclerView recyclerView;
    private RecyclerView sortRecycler;
    private TextView profileButton;
    private TextView notificationBadge;
    private TextView offlineBanner;
    private NotificationViewModel notificationViewModel;

    private User currentUser;
    private boolean pendingReceiverRegistered;
    private boolean showingServerError;
    private boolean blockedShown;

    /** Dynamic receiver: refreshes the feed after a queued post finishes uploading. */
    private final BroadcastReceiver pendingPostedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(HomeActivity.this, R.string.pending_posted_body,
                    Toast.LENGTH_SHORT).show();
            viewModel.stop();
            viewModel.start();
        }
    };

    private static final String[][] SORTS = {
            {QuestionRepository.SORT_NEWEST, "Mới nhất"},
            {QuestionRepository.SORT_POPULAR, "Phổ biến"},
            {QuestionRepository.SORT_UNANSWERED, "Chưa trả lời"}
    };

    private final ActivityResultLauncher<Intent> createPromoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, R.string.promo_submitted, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted) {
                            // Keep the stored preference in step with the OS; Settings can retry.
                            PrefsHelper.setNotificationsEnabled(this, false);
                            NotificationWatcher.stop();
                        }
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
        notificationViewModel = new ViewModelProvider(this).get(NotificationViewModel.class);
        stateView = findViewById(R.id.stateView);
        progressMore = findViewById(R.id.progressMore);
        recyclerView = findViewById(R.id.recyclerQuestions);
        sortRecycler = findViewById(R.id.recyclerSort);
        notificationBadge = findViewById(R.id.tvNotificationBadge);

        setupFeed();
        setupCategoryChips();
        setupSortChips();
        setupSearch();
        setupOfflineBanner();

        findViewById(R.id.notificationButton).setOnClickListener(v -> {
            if (new SessionManager(this).isLoggedIn()) {
                startActivity(new Intent(this, NotificationActivity.class));
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });
        profileButton = findViewById(R.id.btnProfile);
        profileButton.setOnClickListener(this::showProfileMenu);

        stateView.setOnRetryListener(v -> {
            viewModel.stop();
            viewModel.start();
        });
        bindBottomNav();
        viewModel.getCurrentUser().observe(this, this::bindCurrentUser);
        notificationViewModel.getNotifications().observe(this, this::bindNotificationBadge);
        maybeRequestNotificationPermission();
    }

    /** Ask for POST_NOTIFICATIONS once on Android 13+; Settings can re-request later. */
    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (PrefsHelper.hasAskedNotifications(this)) {
            return;
        }
        if (PermissionHelper.hasNotifications(this)) {
            return;
        }
        PrefsHelper.setAskedNotifications(this, true);
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    protected int selectedNavItem() {
        return NAV_HOME;
    }

    @Override
    protected boolean isTabActivity() {
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        String uid = new SessionManager(this).getUserId();
        if (uid == null) {
            // The feed requires authentication; never leave a signed-out user on stale content.
            goToLogin();
            return;
        }
        viewModel.start();
        notificationViewModel.start(uid);
        if (!pendingReceiverRegistered) {
            ContextCompat.registerReceiver(this, pendingPostedReceiver,
                    new IntentFilter(PendingQuestionWorker.ACTION_PENDING_POSTED),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            pendingReceiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (pendingReceiverRegistered) {
            unregisterReceiver(pendingPostedReceiver);
            pendingReceiverRegistered = false;
        }
        // Keep the feed listeners/ViewModel state so returning from a detail screen or a
        // configuration change does not restart the first page. onCleared() detaches them.
        notificationViewModel.stop();
    }

    /** Red badge with the unread notification count (hidden at zero). */
    private void bindNotificationBadge(UiState<List<AppNotification>> state) {
        int unread = 0;
        if (state != null && state.getData() != null) {
            for (AppNotification notification : state.getData()) {
                if (!notification.isRead()) {
                    unread++;
                }
            }
        }
        if (notificationBadge == null) {
            return;
        }
        if (unread <= 0) {
            notificationBadge.setVisibility(View.GONE);
        } else {
            notificationBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            notificationBadge.setVisibility(View.VISIBLE);
        }
    }

    private void bindCurrentUser(User user) {
        currentUser = user;
        if (user != null) {
            if (user.isDeleted()) {
                showBlocked(AccountBlockedActivity.MODE_DELETED);
                return;
            }
            if (user.isTimedOut()) {
                showBlocked(AccountBlockedActivity.MODE_TIMEOUT);
                return;
            }
        }
        if (profileButton == null) {
            return;
        }
        String name = user != null && user.getUsername() != null ? user.getUsername() : "";
        profileButton.setText(name.isEmpty()
                ? getString(R.string.avatar_placeholder)
                : String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault()));
    }

    /** Moderation gate: the whole app is unreachable for a timed-out or deleted account. */
    private void showBlocked(String mode) {
        if (blockedShown) {
            return;
        }
        blockedShown = true;
        Intent intent = new Intent(this, AccountBlockedActivity.class)
                .putExtra(AccountBlockedActivity.EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (currentUser != null) {
            intent.putExtra(AccountBlockedActivity.EXTRA_TIMEOUT_COUNT,
                    currentUser.getTimeoutCount());
            intent.putExtra(AccountBlockedActivity.EXTRA_WARNING_COUNT,
                    currentUser.getWarningCount());
            if (currentUser.getTimeoutUntil() != null) {
                intent.putExtra(AccountBlockedActivity.EXTRA_TIMEOUT_UNTIL,
                        currentUser.getTimeoutUntil().getTime());
            }
        }
        startActivity(intent);
        finish();
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

            @Override
            public void onWordClick(WordEntry word) {
                Intent intent = new Intent(HomeActivity.this, WordDetailActivity.class);
                intent.putExtra(WordDetailActivity.EXTRA_WORD,
                        word != null ? word.getWord() : "");
                startActivity(intent);
            }
        });

        // Reddit-style feed: always one full-width column, even on wide screens.
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(feedAdapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || Boolean.TRUE.equals(viewModel.getLoadingMore().getValue())) {
                    return;
                }
                LinearLayoutManager manager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null
                        && manager.findLastVisibleItemPosition()
                            >= feedAdapter.getItemCount() - 3) {
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
                updateOfflineBanner(state.isFromCache());
                break;
            case EMPTY:
                if (viewModel.isUnsearchableQuery()) {
                    stateView.showEmpty(R.string.search_too_short);
                } else if (viewModel.hasSearchQuery()) {
                    stateView.showEmpty(R.string.search_no_results);
                } else {
                    stateView.showEmpty();
                }
                recyclerView.setVisibility(View.GONE);
                break;
            case ERROR:
            default:
                if (!NetworkUtils.isOnline(this)) {
                    // The dedicated offline/error screen retries automatically when the network
                    // returns; the inline error state remains for non-network failures.
                    if (!showingServerError) {
                        showingServerError = true;
                        Intent intent = new Intent(this, ServerErrorActivity.class);
                        intent.putExtra(ServerErrorActivity.EXTRA_AUTO_RETRY, true);
                        startActivity(intent);
                        finish();
                    }
                    return;
                }
                stateView.showError(state.getMessage());
                recyclerView.setVisibility(View.GONE);
                updateOfflineBanner(false);
                break;
        }
    }

    /** A compact, dismissible-looking hint above the feed when Room/Firestore served old data. */
    private void setupOfflineBanner() {
        LinearLayout root = findViewById(R.id.homeRoot);
        if (root == null) {
            return;
        }
        offlineBanner = new TextView(this);
        offlineBanner.setText(R.string.home_offline_banner);
        offlineBanner.setTextColor(getColor(R.color.text_body));
        offlineBanner.setTextSize(12);
        offlineBanner.setBackgroundColor(getColor(R.color.bg_card));
        int horizontal = (int) (12 * getResources().getDisplayMetrics().density);
        int vertical = (int) (6 * getResources().getDisplayMetrics().density);
        offlineBanner.setPadding(horizontal, vertical, horizontal, vertical);
        offlineBanner.setVisibility(View.GONE);
        root.addView(offlineBanner, 1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void updateOfflineBanner(boolean visible) {
        if (offlineBanner != null) {
            offlineBanner.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        // One horizontal, scrollable row in both orientations. A grid inside the short landscape
        // rail was clipped and hid categories below the second row.
        recycler.setLayoutManager(
                new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
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
        sortAdapter = new CategoryChipAdapter(viewModel.getSort(), category -> {
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

    /** Profile menu: login / create promo (centres only) / saved / logout. */
    private void showProfileMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_profile, popup.getMenu());

        boolean loggedIn = new SessionManager(this).isLoggedIn();
        boolean isCenter = currentUser != null
                && User.ROLE_CENTER.equals(currentUser.getRole())
                && currentUser.getCenterId() != null;

        popup.getMenu().findItem(R.id.action_login).setVisible(!loggedIn);
        popup.getMenu().findItem(R.id.action_create_promo).setVisible(loggedIn && isCenter);
        popup.getMenu().findItem(R.id.action_my_promos).setVisible(loggedIn && isCenter);
        popup.getMenu().findItem(R.id.action_centers).setVisible(true);
        popup.getMenu().findItem(R.id.action_saved_promos).setVisible(true);
        popup.getMenu().findItem(R.id.action_settings).setVisible(true);
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
        if (id == R.id.action_my_promos) {
            startActivity(new Intent(this, MyPromosActivity.class));
            return true;
        }
        if (id == R.id.action_centers) {
            startActivity(new Intent(this, CenterListActivity.class));
            return true;
        }
        if (id == R.id.action_saved_promos) {
            startActivity(new Intent(this, SavedPromosActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_logout) {
            new SessionManager(this).logout();
            Toast.makeText(this, R.string.logged_out, Toast.LENGTH_SHORT).show();
            goToLogin();
            return true;
        }
        return false;
    }

    /** Clears the whole task so no signed-in screen or cached profile can be reached with Back. */
    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
