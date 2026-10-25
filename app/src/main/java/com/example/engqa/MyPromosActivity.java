package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.model.UiState;
import com.example.engqa.ui.MyPromoAdapter;
import com.example.engqa.util.Navigation;
import com.example.engqa.viewmodel.MyPromosViewModel;

/** A centre's own promos and their approval state. */
public class MyPromosActivity extends AppCompatActivity {

    private MyPromosViewModel viewModel;
    private MyPromoAdapter adapter;
    private RecyclerView recycler;
    private TextView emptyView;
    private View progress;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_screen);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_my_promos);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        uid = new SessionManager(this).getUserId();
        if (uid == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        recycler = findViewById(R.id.recyclerMyPromos);
        emptyView = findViewById(R.id.tvMyPromosEmpty);
        progress = findViewById(R.id.progressMyPromos);
        adapter = new MyPromoAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        Navigation.wireBack(this);

        viewModel = new ViewModelProvider(this).get(MyPromosViewModel.class);
        viewModel.getPromos().observe(this, this::render);
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

    private void render(UiState<java.util.List<com.example.engqa.model.Promo>> state) {
        if (state == null) {
            return;
        }
        progress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        switch (state.getStatus()) {
            case SUCCESS:
                adapter.submitList(state.getData());
                recycler.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                break;
            case EMPTY:
                adapter.submitList(java.util.Collections.emptyList());
                recycler.setVisibility(View.GONE);
                emptyView.setText(R.string.my_promos_empty);
                emptyView.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                adapter.submitList(java.util.Collections.emptyList());
                recycler.setVisibility(View.GONE);
                emptyView.setText(state.getMessage());
                emptyView.setVisibility(View.VISIBLE);
                if (state.getMessage() != null) {
                    Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
                }
                break;
            case LOADING:
            default:
                break;
        }
    }
}
