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

import com.example.engqa.model.Center;
import com.example.engqa.model.UiState;
import com.example.engqa.ui.CenterAdapter;
import com.example.engqa.ui.StateView;
import com.example.engqa.viewmodel.CenterViewModel;

import java.util.List;
import com.example.engqa.util.Navigation;

/** List of approved English centres with a link to request a new centre page. */
public class CenterListActivity extends BaseNavActivity {

    private CenterViewModel viewModel;
    private CenterAdapter adapter;
    private StateView stateView;
    private RecyclerView recycler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_center_list);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        viewModel = new ViewModelProvider(this).get(CenterViewModel.class);
        stateView = findViewById(R.id.stateView);
        recycler = findViewById(R.id.recyclerCenters);

        adapter = new CenterAdapter(this::openCenter);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        Navigation.wireBack(this);
        findViewById(R.id.btnRequestCenter).setOnClickListener(v ->
                startActivity(new Intent(this, CenterRequestActivity.class)));
        stateView.setOnRetryListener(v -> {
            viewModel.stop();
            viewModel.startList();
        });
        bindBottomNav();

        viewModel.getCenters().observe(this, this::render);
    }

    @Override
    protected int selectedNavItem() {
        return NAV_HOME;
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.startList();
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    private void render(UiState<List<Center>> state) {
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

    private void openCenter(Center center) {
        Intent intent = new Intent(this, CenterDetailActivity.class);
        intent.putExtra(CenterDetailActivity.EXTRA_CENTER_ID, center.getId());
        startActivity(intent);
    }
}
