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
import com.example.engqa.model.AppNotification;
import com.example.engqa.util.TimeUtils;

/** Renders the notification list. Tapping an item opens its related content. */
public class NotificationAdapter
        extends ListAdapter<AppNotification, NotificationAdapter.NotificationViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(AppNotification notification);
    }

    private static final DiffUtil.ItemCallback<AppNotification> DIFF =
            new DiffUtil.ItemCallback<AppNotification>() {
                @Override
                public boolean areItemsTheSame(@NonNull AppNotification oldItem,
                                               @NonNull AppNotification newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AppNotification oldItem,
                                                  @NonNull AppNotification newItem) {
                    return oldItem.isRead() == newItem.isRead()
                            && eq(oldItem.getBody(), newItem.getBody());
                }

                private boolean eq(String a, String b) {
                    return a == null ? b == null : a.equals(b);
                }
            };

    private final OnNotificationClickListener listener;

    public NotificationAdapter(OnNotificationClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {

        private final View unreadDot;
        private final TextView title;
        private final TextView body;
        private final TextView time;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            unreadDot = itemView.findViewById(R.id.vUnread);
            title = itemView.findViewById(R.id.tvNotificationTitle);
            body = itemView.findViewById(R.id.tvNotificationBody);
            time = itemView.findViewById(R.id.tvNotificationTime);
        }

        void bind(AppNotification notification, OnNotificationClickListener listener) {
            title.setText(notification.getTitle());
            body.setText(notification.getBody());
            time.setText(TimeUtils.relative(notification.getCreatedAt()));
            unreadDot.setVisibility(notification.isRead() ? View.INVISIBLE : View.VISIBLE);
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationClick(notification);
                }
            });
        }
    }
}
