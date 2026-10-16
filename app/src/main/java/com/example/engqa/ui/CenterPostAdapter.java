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
import com.example.engqa.model.CenterPost;
import com.example.engqa.util.TimeUtils;

/** Renders a centre's posts. */
public class CenterPostAdapter extends ListAdapter<CenterPost, CenterPostAdapter.PostViewHolder> {

    private static final DiffUtil.ItemCallback<CenterPost> DIFF =
            new DiffUtil.ItemCallback<CenterPost>() {
                @Override
                public boolean areItemsTheSame(@NonNull CenterPost oldItem,
                                               @NonNull CenterPost newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull CenterPost oldItem,
                                                  @NonNull CenterPost newItem) {
                    String a = oldItem.getTitle();
                    String b = newItem.getTitle();
                    return a == null ? b == null : a.equals(b);
                }
            };

    public CenterPostAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_center_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {

        private final TextView title;
        private final TextView body;
        private final TextView time;
        private final ImageView image;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvPostTitle);
            body = itemView.findViewById(R.id.tvPostBody);
            time = itemView.findViewById(R.id.tvPostTime);
            image = itemView.findViewById(R.id.ivPostImage);
        }

        void bind(CenterPost post) {
            title.setText(post.getTitle());
            body.setText(post.getBody());
            time.setText(TimeUtils.relative(post.getCreatedAt()));
            if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
                image.setVisibility(View.VISIBLE);
                Glide.with(image).load(post.getImageUrl()).into(image);
            } else {
                image.setVisibility(View.GONE);
            }
        }
    }
}
