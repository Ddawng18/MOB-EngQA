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
import com.example.engqa.model.Center;

/** Renders the list of approved centres. */
public class CenterAdapter extends ListAdapter<Center, CenterAdapter.CenterViewHolder> {

    public interface OnCenterClickListener {
        void onCenterClick(Center center);
    }

    private static final DiffUtil.ItemCallback<Center> DIFF = new DiffUtil.ItemCallback<Center>() {
        @Override
        public boolean areItemsTheSame(@NonNull Center oldItem, @NonNull Center newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Center oldItem, @NonNull Center newItem) {
            return eq(oldItem.getName(), newItem.getName())
                    && eq(oldItem.getDescription(), newItem.getDescription())
                    && eq(oldItem.getAddress(), newItem.getAddress());
        }

        private boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    };

    private final OnCenterClickListener listener;

    public CenterAdapter(OnCenterClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public CenterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_center, parent, false);
        return new CenterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CenterViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class CenterViewHolder extends RecyclerView.ViewHolder {

        private final ImageView logo;
        private final TextView name;
        private final TextView description;
        private final TextView address;

        CenterViewHolder(@NonNull View itemView) {
            super(itemView);
            logo = itemView.findViewById(R.id.ivCenterLogo);
            name = itemView.findViewById(R.id.tvCenterName);
            description = itemView.findViewById(R.id.tvCenterDescription);
            address = itemView.findViewById(R.id.tvCenterAddress);
        }

        void bind(Center center, OnCenterClickListener listener) {
            name.setText(center.getName());
            description.setText(center.getDescription());
            address.setText(center.getAddress());
            if (center.getLogoUrl() != null && !center.getLogoUrl().isEmpty()) {
                Glide.with(logo).load(center.getLogoUrl()).into(logo);
            } else {
                logo.setImageDrawable(null);
            }
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCenterClick(center);
                }
            });
        }
    }
}
