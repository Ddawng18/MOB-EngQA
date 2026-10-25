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
import com.example.engqa.model.Promo;
import com.example.engqa.util.TimeUtils;

/** Pending promo posts in the moderation console. */
public class AdminPromoAdapter extends ListAdapter<Promo, AdminPromoAdapter.PromoViewHolder> {

    public interface Listener {
        void onApprove(Promo promo);

        void onReject(Promo promo);
    }

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

    private final Listener listener;

    public AdminPromoAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public PromoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PromoViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_promo, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PromoViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class PromoViewHolder extends RecyclerView.ViewHolder {

        private final TextView center;
        private final TextView title;
        private final TextView body;
        private final TextView meta;
        private final TextView author;
        private final TextView time;

        PromoViewHolder(@NonNull View itemView) {
            super(itemView);
            center = itemView.findViewById(R.id.tvPromoCenter);
            title = itemView.findViewById(R.id.tvPromoTitle);
            body = itemView.findViewById(R.id.tvPromoBody);
            meta = itemView.findViewById(R.id.tvPromoMeta);
            author = itemView.findViewById(R.id.tvPromoAuthor);
            time = itemView.findViewById(R.id.tvPromoTime);
        }

        void bind(Promo promo, Listener listener) {
            center.setText(promo.getCenterName());
            title.setText(promo.getTitle());
            body.setText(promo.getBody());
            body.setVisibility(promo.getBody() == null || promo.getBody().isEmpty()
                    ? View.GONE : View.VISIBLE);
            meta.setText(join(promo));
            author.setVisibility(View.GONE);
            time.setText(TimeUtils.relative(promo.getCreatedAt()));

            itemView.findViewById(R.id.btnApprovePromo)
                    .setOnClickListener(v -> listener.onApprove(promo));
            itemView.findViewById(R.id.btnRejectPromo)
                    .setOnClickListener(v -> listener.onReject(promo));
        }

        private String join(Promo promo) {
            StringBuilder builder = new StringBuilder();
            append(builder, promo.getType());
            append(builder, promo.getLevel());
            append(builder, promo.getFee());
            return builder.toString();
        }

        private void append(StringBuilder builder, String value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(value);
        }
    }
}
