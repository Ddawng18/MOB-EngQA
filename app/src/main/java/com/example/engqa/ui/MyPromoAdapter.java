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
import com.example.engqa.model.Promo;
import com.example.engqa.util.TimeUtils;

/** A centre's own promos, with the moderation status of each one. */
public class MyPromoAdapter extends ListAdapter<Promo, MyPromoAdapter.PromoViewHolder> {

    private static final DiffUtil.ItemCallback<Promo> DIFF =
            new DiffUtil.ItemCallback<Promo>() {
                @Override
                public boolean areItemsTheSame(@NonNull Promo oldItem, @NonNull Promo newItem) {
                    return oldItem.getPromoId() != null
                            && oldItem.getPromoId().equals(newItem.getPromoId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Promo oldItem, @NonNull Promo newItem) {
                    return oldItem.getStatus() != null
                            && oldItem.getStatus().equals(newItem.getStatus());
                }
            };

    public MyPromoAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public PromoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PromoViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_promo, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PromoViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class PromoViewHolder extends RecyclerView.ViewHolder {

        private final TextView center;
        private final TextView title;
        private final TextView body;
        private final TextView status;
        private final TextView time;

        PromoViewHolder(@NonNull View itemView) {
            super(itemView);
            center = itemView.findViewById(R.id.tvMyPromoCenter);
            title = itemView.findViewById(R.id.tvMyPromoTitle);
            body = itemView.findViewById(R.id.tvMyPromoBody);
            status = itemView.findViewById(R.id.tvMyPromoStatus);
            time = itemView.findViewById(R.id.tvMyPromoTime);
        }

        void bind(Promo promo) {
            center.setText(promo.getCenterName());
            title.setText(promo.getTitle());
            body.setText(promo.getBody());
            body.setVisibility(promo.getBody() == null || promo.getBody().isEmpty()
                    ? View.GONE : View.VISIBLE);
            time.setText(TimeUtils.relative(promo.getCreatedAt()));
            bindStatus(promo);
        }

        private void bindStatus(Promo promo) {
            String status = promo.getStatus();
            int text;
            int background;
            int color;
            if (Promo.STATUS_APPROVED.equals(status)
                    && promo.isExpired(System.currentTimeMillis())) {
                text = R.string.promo_status_expired;
                background = R.drawable.bg_chip;
                color = R.color.warning_amber;
            } else if (Promo.STATUS_APPROVED.equals(status)) {
                text = R.string.promo_status_approved;
                background = R.drawable.bg_chip_success;
                color = R.color.accepted_green;
            } else if (Promo.STATUS_REJECTED.equals(status)) {
                text = R.string.promo_status_rejected;
                background = R.drawable.bg_chip;
                color = R.color.error_red;
            } else if (Promo.STATUS_HIDDEN.equals(status)) {
                text = R.string.promo_status_hidden;
                background = R.drawable.bg_chip;
                color = R.color.text_secondary;
            } else {
                text = R.string.promo_status_pending;
                background = R.drawable.bg_chip;
                color = R.color.accent_orange;
            }
            this.status.setText(text);
            this.status.setBackgroundResource(background);
            this.status.setTextColor(ContextCompat.getColor(itemView.getContext(), color));
        }
    }
}
