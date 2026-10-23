package com.example.engqa.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.R;

import java.util.ArrayList;
import java.util.List;

/** Clickable image thumbnails on the question detail screen. */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    public interface OnImageClickListener {
        void onImageClick(String url);
    }

    private final List<String> urls = new ArrayList<>();
    private final OnImageClickListener listener;

    public ImageAdapter(OnImageClickListener listener) {
        this.listener = listener;
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
                .inflate(R.layout.item_attachment, parent, false);
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
            remove.setVisibility(View.GONE);
            ImageLoader.load(image, url, null);
            image.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onImageClick(url);
                }
            });
        }
    }
}
