package com.example.engqa.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.R;
import com.example.engqa.model.Category;

/** Horizontal category filter chips shown on the feed. */
public class CategoryChipAdapter extends ListAdapter<Category, CategoryChipAdapter.ChipViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    private static final DiffUtil.ItemCallback<Category> DIFF =
            new DiffUtil.ItemCallback<Category>() {
                @Override
                public boolean areItemsTheSame(@NonNull Category oldItem, @NonNull Category newItem) {
                    return key(oldItem).equals(key(newItem));
                }

                @Override
                public boolean areContentsTheSame(@NonNull Category oldItem,
                                                  @NonNull Category newItem) {
                    return key(oldItem).equals(key(newItem));
                }

                private String key(Category category) {
                    return category.getId() == null ? "" : category.getId();
                }
            };

    private final OnCategoryClickListener listener;
    private String selectedId;

    public CategoryChipAdapter(String initialSelectedId, OnCategoryClickListener listener) {
        super(DIFF);
        this.selectedId = initialSelectedId;
        this.listener = listener;
    }

    public void setSelectedId(String selectedId) {
        this.selectedId = selectedId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_chip, parent, false);
        return new ChipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChipViewHolder holder, int position) {
        holder.bind(getItem(position), selectedId, listener);
    }

    static class ChipViewHolder extends RecyclerView.ViewHolder {

        private final TextView chip;

        ChipViewHolder(@NonNull View itemView) {
            super(itemView);
            chip = itemView.findViewById(R.id.tvChip);
        }

        void bind(Category category, String selectedId, OnCategoryClickListener listener) {
            chip.setText(category.getName());
            boolean active = category.getId() == null
                    ? selectedId == null
                    : category.getId().equals(selectedId);
            chip.setBackgroundResource(active
                    ? R.drawable.bg_chip_active : R.drawable.bg_chip);
            chip.setTextColor(ContextCompat.getColor(itemView.getContext(),
                    active ? R.color.accent_orange : R.color.text_secondary));
            chip.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }
    }
}
