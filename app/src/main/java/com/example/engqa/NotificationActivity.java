package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.model.AppNotification;
import com.example.engqa.model.UiState;
import com.example.engqa.ui.NotificationAdapter;
import com.example.engqa.ui.StateView;
import com.example.engqa.viewmodel.NotificationViewModel;

import java.util.List;
import com.example.engqa.util.Navigation;

/** In-app notification list; tapping a row opens the related question, promo or center. */
public class NotificationActivity extends BaseNavActivity {

    private NotificationViewModel viewModel;
    private NotificationAdapter adapter;
    private StateView stateView;
    private RecyclerView recycler;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_notification);
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

        viewModel = new ViewModelProvider(this).get(NotificationViewModel.class);
        stateView = findViewById(R.id.stateView);
        recycler = findViewById(R.id.recyclerNotifications);

        adapter = new NotificationAdapter(this::openNotification);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        Navigation.wireBack(this);
        findViewById(R.id.btnMarkAllRead).setOnClickListener(v -> viewModel.markAllRead());
        stateView.setOnRetryListener(v -> {
            viewModel.stop();
            viewModel.start(uid);
        });
        bindBottomNav();

        viewModel.getNotifications().observe(this, this::render);
    }

    @Override
    protected int selectedNavItem() {
        return NAV_HOME;
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

    private void render(UiState<List<AppNotification>> state) {
        if (state == null) {
            return;
        }
        switch (state.getStatus()) {
            case LOADING:
                stateView.showLoading();
                recycler.setVisibility(View.GONE);
                break;
            case SUCCESS:
                stateView.showContent();
                recycler.setVisibility(View.VISIBLE);
                adapter.submitList(state.getData());
                break;
            case EMPTY:
                stateView.showEmpty();
                recycler.setVisibility(View.GONE);
                break;
            case ERROR:
            default:
                stateView.showError(state.getMessage());
                recycler.setVisibility(View.GONE);
                break;
        }
    }

    private void openNotification(AppNotification notification) {
        viewModel.markRead(notification);
        Intent intent = null;
        if (notification.getQuestionId() != null) {
            intent = new Intent(this, QuestionDetailActivity.class);
            intent.putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID,
                    notification.getQuestionId());
            if (notification.getAnswerId() != null) {
                intent.putExtra(QuestionDetailActivity.EXTRA_FOCUS_ANSWER_ID,
                        notification.getAnswerId());
            }
        } else if (notification.getPromoId() != null) {
            intent = new Intent(this, PromoDetailActivity.class);
            intent.putExtra(PromoDetailActivity.EXTRA_PROMO_ID, notification.getPromoId());
        } else if (notification.getCenterId() != null) {
            intent = new Intent(this, CenterDetailActivity.class);
            intent.putExtra(CenterDetailActivity.EXTRA_CENTER_ID, notification.getCenterId());
        }
        if (intent != null) {
            startActivity(intent);
        }
    }
}
