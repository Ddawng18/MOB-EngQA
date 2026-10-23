package com.example.engqa;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
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

import com.example.engqa.media.AudioPlayerManager;
import com.example.engqa.model.UiState;
import com.example.engqa.model.WordEntry;
import com.example.engqa.ui.StateView;
import com.example.engqa.ui.WordMeaningAdapter;
import com.example.engqa.viewmodel.WordOfDayViewModel;

/** Full definitions, examples and pronunciation for the daily word. */
public class WordDetailActivity extends AppCompatActivity {

    public static final String EXTRA_WORD = "com.example.engqa.extra.WORD";

    private WordOfDayViewModel viewModel;
    private WordMeaningAdapter adapter;
    private AudioPlayerManager player;
    private ImageView audioButton;
    private StateView stateView;
    private String word;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_word_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.wordDetailRoot),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        word = getIntent().getStringExtra(EXTRA_WORD);
        if (word == null || word.trim().isEmpty()) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(WordOfDayViewModel.class);
        stateView = findViewById(R.id.stateView);
        audioButton = findViewById(R.id.btnWordAudio);

        adapter = new WordMeaningAdapter();
        RecyclerView recycler = findViewById(R.id.recyclerMeanings);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        recycler.setNestedScrollingEnabled(false);

        player = new AudioPlayerManager();
        player.setListener((playing, positionMs, durationMs) ->
                audioButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        stateView.setOnRetryListener(v -> viewModel.loadWord(word, true));

        viewModel.getWord().observe(this, this::render);
        viewModel.loadWord(word, false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.release();
        }
    }

    private void render(UiState<WordEntry> state) {
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

    private void bind(WordEntry entry) {
        if (entry == null) {
            return;
        }
        ((TextView) findViewById(R.id.tvDetailWord)).setText(entry.getWord());
        ((TextView) findViewById(R.id.tvDetailPhonetic)).setText(
                entry.getPhonetic() != null ? entry.getPhonetic() : "");
        adapter.submitList(entry.getMeanings());

        boolean hasAudio = entry.getAudioUrl() != null && !entry.getAudioUrl().isEmpty();
        audioButton.setVisibility(hasAudio ? View.VISIBLE : View.GONE);
        if (hasAudio) {
            audioButton.setOnClickListener(v -> player.toggle(entry.getAudioUrl()));
        }
    }
}
