package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.engqa.model.Center;
import com.example.engqa.model.UiState;
import com.example.engqa.ui.CenterPostAdapter;
import com.example.engqa.ui.CourseAdapter;
import com.example.engqa.ui.StateView;
import com.example.engqa.viewmodel.CenterViewModel;
import com.example.engqa.util.Navigation;

/** Centre page: profile, courses and posts, with an edit shortcut for the manager. */
public class CenterDetailActivity extends BaseNavActivity {

    public static final String EXTRA_CENTER_ID = "com.example.engqa.extra.CENTER_ID";

    private CenterViewModel viewModel;
    private CourseAdapter courseAdapter;
    private CenterPostAdapter postAdapter;
    private StateView stateView;
    private String centerId;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_center_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        centerId = getIntent().getStringExtra(EXTRA_CENTER_ID);
        uid = new SessionManager(this).getUserId();
        if (TextUtils.isEmpty(centerId)) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(CenterViewModel.class);
        stateView = findViewById(R.id.stateView);

        courseAdapter = new CourseAdapter();
        RecyclerView courses = findViewById(R.id.recyclerCourses);
        courses.setLayoutManager(new LinearLayoutManager(this));
        courses.setAdapter(courseAdapter);
        courses.setNestedScrollingEnabled(false);

        postAdapter = new CenterPostAdapter();
        RecyclerView posts = findViewById(R.id.recyclerPosts);
        posts.setLayoutManager(new LinearLayoutManager(this));
        posts.setAdapter(postAdapter);
        posts.setNestedScrollingEnabled(false);

        Navigation.wireBack(this);
        findViewById(R.id.btnEditCenter).setOnClickListener(v ->
                startActivity(new Intent(this, CenterEditActivity.class)
                        .putExtra(CenterEditActivity.EXTRA_CENTER_ID, centerId)));
        stateView.setOnRetryListener(v -> {
            viewModel.stop();
            viewModel.startDetail(centerId);
        });
        bindBottomNav();

        viewModel.getCenter().observe(this, this::renderCenter);
        viewModel.getCourses().observe(this, state -> {
            if (state != null && state.getData() != null) {
                courseAdapter.submitList(state.getData());
            }
        });
        viewModel.getPosts().observe(this, state -> {
            if (state != null && state.getData() != null) {
                postAdapter.submitList(state.getData());
            }
        });
    }

    @Override
    protected int selectedNavItem() {
        return NAV_HOME;
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

    private void renderCenter(UiState<Center> state) {
        if (state == null) {
            return;
        }
        switch (state.getStatus()) {
            case LOADING:
                stateView.showLoading();
                break;
            case SUCCESS:
                stateView.showContent();
                bind(state.getData());
                break;
            case ERROR:
            default:
                stateView.showError(state.getMessage());
                break;
        }
    }

    private void bind(Center center) {
        if (center == null) {
            return;
        }
        ((TextView) findViewById(R.id.tvCenterName)).setText(center.getName());
        ((TextView) findViewById(R.id.tvCenterDescription)).setText(center.getDescription());
        ((TextView) findViewById(R.id.tvCenterAddress)).setText(center.getAddress());
        ((TextView) findViewById(R.id.tvCenterPhone)).setText(center.getPhone());
        ((TextView) findViewById(R.id.tvCenterEmail)).setText(center.getEmail());

        ImageView logo = findViewById(R.id.ivCenterLogo);
        if (center.getLogoUrl() != null && !center.getLogoUrl().isEmpty()) {
            Glide.with(this).load(center.getLogoUrl()).into(logo);
        }

        boolean isManager = uid != null && center.getManagerUids() != null
                && center.getManagerUids().contains(uid);
        findViewById(R.id.btnEditCenter).setVisibility(isManager ? View.VISIBLE : View.GONE);
    }
}
