package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.data.Callback;
import com.example.engqa.data.local.SavedPromo;
import com.example.engqa.ui.SavedPromoAdapter;
import com.example.engqa.viewmodel.SavedViewModel;

import java.util.List;
import com.example.engqa.util.Navigation;

/** Lists promos saved locally in Room; tapping opens the detail, the × removes it. */
public class SavedPromosActivity extends AppCompatActivity {

    private SavedViewModel viewModel;
    private SavedPromoAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_saved_promos);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.savedRoot),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        viewModel = new ViewModelProvider(this).get(SavedViewModel.class);
        emptyView = findViewById(R.id.tvSavedEmpty);

        adapter = new SavedPromoAdapter(new SavedPromoAdapter.Listener() {
            @Override
            public void onOpen(SavedPromo promo) {
                Intent intent = new Intent(SavedPromosActivity.this,
                        PromoDetailActivity.class);
                intent.putExtra(PromoDetailActivity.EXTRA_PROMO_ID, promo.getPromoId());
                startActivity(intent);
            }

            @Override
            public void onRemove(SavedPromo promo) {
                viewModel.remove(promo.getPromoId(), new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                    }

                    @Override
                    public void onError(Exception e) {
                    }
                });
            }
        });

        RecyclerView recycler = findViewById(R.id.recyclerSaved);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        Navigation.wireBack(this);

        viewModel.getSavedPromos().observe(this, this::render);
    }

    private void render(List<SavedPromo> promos) {
        adapter.submitList(promos);
        boolean empty = promos == null || promos.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
}
