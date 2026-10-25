package com.example.engqa.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.engqa.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Clickable question images. The same adapter powers the small attachment strip in the compose
 * screen and the full-width gallery in the detail screen; only the item layout differs.
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    public interface OnImageClickListener {
        void onImageClick(String url);
    }

    private final List<String> urls = new ArrayList<>();
    private final OnImageClickListener listener;
    @LayoutRes
    private final int itemLayout;

    public ImageAdapter(OnImageClickListener listener) {
        this(listener, R.layout.item_attachment);
    }

    public ImageAdapter(OnImageClickListener listener, @LayoutRes int itemLayout) {
        this.listener = listener;
        this.itemLayout = itemLayout;
    }

    public void submit(List<String> newUrls) {
        urls.clear();
        if (newUrls != null) {
            urls.addAll(newUrls);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(itemLayout, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        holder.bind(urls.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return urls.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {

        private final ImageView image;
        private final View remove;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.ivAttachment);
            remove = itemView.findViewById(R.id.btnRemoveAttachment);
        }

        void bind(String url, OnImageClickListener listener) {
            if (remove != null) {
                remove.setVisibility(View.GONE);
            }
            Glide.with(image).load(url).fitCenter().override(1080, 1920).into(image);
            image.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onImageClick(url);
                }
            });
        }
    }
}
