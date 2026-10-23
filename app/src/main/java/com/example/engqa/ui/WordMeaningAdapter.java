package com.example.engqa.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.R;
import com.example.engqa.model.WordEntry;

/** One row per part of speech on the Word detail screen. */
public class WordMeaningAdapter
        extends ListAdapter<WordEntry.Meaning, WordMeaningAdapter.MeaningViewHolder> {

    private static final DiffUtil.ItemCallback<WordEntry.Meaning> DIFF =
            new DiffUtil.ItemCallback<WordEntry.Meaning>() {
                @Override
                public boolean areItemsTheSame(@NonNull WordEntry.Meaning oldItem,
                                               @NonNull WordEntry.Meaning newItem) {
                    return key(oldItem).equals(key(newItem));
                }

                @Override
                public boolean areContentsTheSame(@NonNull WordEntry.Meaning oldItem,
                                                  @NonNull WordEntry.Meaning newItem) {
                    return key(oldItem).equals(key(newItem));
                }

                private String key(WordEntry.Meaning meaning) {
                    String pos = meaning.getPartOfSpeech() != null
                            ? meaning.getPartOfSpeech() : "";
                    String first = "";
                    if (meaning.getDefinitions() != null
                            && !meaning.getDefinitions().isEmpty()
                            && meaning.getDefinitions().get(0).getText() != null) {
                        first = meaning.getDefinitions().get(0).getText();
                    }
                    return pos + "|" + first;
                }
            };

    public WordMeaningAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public MeaningViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MeaningViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word_meaning, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MeaningViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class MeaningViewHolder extends RecyclerView.ViewHolder {

        private final TextView partOfSpeech;
        private final TextView definitions;
        private final TextView example;

        MeaningViewHolder(@NonNull View itemView) {
            super(itemView);
            partOfSpeech = itemView.findViewById(R.id.tvMeaningPartOfSpeech);
            definitions = itemView.findViewById(R.id.tvMeaningDefinitions);
            example = itemView.findViewById(R.id.tvMeaningExample);
        }

        void bind(WordEntry.Meaning meaning) {
            String pos = meaning.getPartOfSpeech();
            partOfSpeech.setText(pos != null ? pos : "");
            partOfSpeech.setVisibility(pos != null && !pos.isEmpty() ? View.VISIBLE : View.GONE);

            StringBuilder builder = new StringBuilder();
            String firstExample = null;
            if (meaning.getDefinitions() != null) {
                for (WordEntry.Definition definition : meaning.getDefinitions()) {
                    if (definition.getText() == null || definition.getText().isEmpty()) {
                        continue;
                    }
                    if (builder.length() > 0) {
                        builder.append("\n\n");
                    }
                    builder.append("• ").append(definition.getText());
                    if (firstExample == null && definition.getExample() != null
                            && !definition.getExample().isEmpty()) {
                        firstExample = definition.getExample();
                    }
                }
            }
            definitions.setText(builder.toString());

            if (firstExample != null) {
                example.setText(itemView.getContext().getString(R.string.word_example, firstExample));
                example.setVisibility(View.VISIBLE);
            } else {
                example.setVisibility(View.GONE);
            }
        }
    }
}
