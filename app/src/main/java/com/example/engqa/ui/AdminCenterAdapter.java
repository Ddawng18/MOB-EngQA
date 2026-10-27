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
import com.example.engqa.model.Center;
import com.example.engqa.util.TimeUtils;

/** Pending centre requests in the moderation console. */
public class AdminCenterAdapter extends ListAdapter<Center, AdminCenterAdapter.CenterViewHolder> {

    public interface Listener {
        void onApprove(Center center);

        void onReject(Center center);
    }

    private static final DiffUtil.ItemCallback<Center> DIFF =
            new DiffUtil.ItemCallback<Center>() {
                @Override
                public boolean areItemsTheSame(@NonNull Center oldItem, @NonNull Center newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Center oldItem, @NonNull Center newItem) {
                    return oldItem.getName() != null && oldItem.getName().equals(newItem.getName())
                            && oldItem.getStatus() != null
                            && oldItem.getStatus().equals(newItem.getStatus());
                }
            };

    private final Listener listener;

    public AdminCenterAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public CenterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CenterViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_center, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CenterViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class CenterViewHolder extends RecyclerView.ViewHolder {

        private final TextView name;
        private final TextView address;
        private final TextView description;
        private final TextView owner;
        private final TextView time;

        CenterViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvCenterName);
            address = itemView.findViewById(R.id.tvCenterAddress);
            description = itemView.findViewById(R.id.tvCenterDescription);
            owner = itemView.findViewById(R.id.tvCenterOwner);
            time = itemView.findViewById(R.id.tvCenterTime);
        }

        void bind(Center center, Listener listener) {
            name.setText(center.getName());
            String location = center.getAddress();
            if (center.getCity() != null && !center.getCity().isEmpty()) {
                location = location == null || location.isEmpty()
                        ? center.getCity() : location + ", " + center.getCity();
            }
            address.setText(location);
            description.setText(center.getDescription());
            description.setVisibility(center.getDescription() == null
                    || center.getDescription().isEmpty() ? View.GONE : View.VISIBLE);
            owner.setVisibility(View.GONE);
            time.setText(TimeUtils.relative(center.getCreatedAt()));

            itemView.findViewById(R.id.btnApproveCenter)
                    .setOnClickListener(v -> listener.onApprove(center));
            itemView.findViewById(R.id.btnRejectCenter)
                    .setOnClickListener(v -> listener.onReject(center));
        }
    }
}
