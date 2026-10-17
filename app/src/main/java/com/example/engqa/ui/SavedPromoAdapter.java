package com.example.engqa.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.engqa.R;
import com.example.engqa.data.local.SavedPromo;

/** Adapter for the local "Bài đã lưu" list. */
public class SavedPromoAdapter
        extends ListAdapter<SavedPromo, SavedPromoAdapter.SavedViewHolder> {

    public interface Listener {
        void onOpen(SavedPromo promo);

        void onRemove(SavedPromo promo);
    }

    private static final DiffUtil.ItemCallback<SavedPromo> DIFF =
            new DiffUtil.ItemCallback<SavedPromo>() {
                @Override
                public boolean areItemsTheSame(@NonNull SavedPromo oldItem,
                                               @NonNull SavedPromo newItem) {
                    return oldItem.getPromoId().equals(newItem.getPromoId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SavedPromo oldItem,
                                                  @NonNull SavedPromo newItem) {
                    return oldItem.getSavedAt() == newItem.getSavedAt()
                            && eq(oldItem.getTitle(), newItem.getTitle());
                }

                private boolean eq(String a, String b) {
                    return a == null ? b == null : a.equals(b);
                }
            };

    private final Listener listener;

    public SavedPromoAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public SavedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SavedViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_saved_promo, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SavedViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class SavedViewHolder extends RecyclerView.ViewHolder {

        private final ImageView image;
        private final TextView center;
        private final TextView title;
        private final ImageView remove;

        SavedViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.ivSavedImage);
            center = itemView.findViewById(R.id.tvSavedCenter);
            title = itemView.findViewById(R.id.tvSavedTitle);
            remove = itemView.findViewById(R.id.btnRemoveSaved);
        }

        void bind(SavedPromo promo, Listener listener) {
            center.setText(promo.getCenterName());
            title.setText(promo.getTitle());
            if (promo.getImageUrl() != null && !promo.getImageUrl().isEmpty()) {
                Glide.with(image).load(promo.getImageUrl()).centerCrop().into(image);
            } else {
                image.setImageDrawable(null);
            }
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOpen(promo);
                }
            });
            remove.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemove(promo);
                }
            });
        }
    }
}
