package com.example.engqa.ui;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.R;
import com.example.engqa.media.MediaCompressor;
import com.example.engqa.model.StoredMedia;

import java.util.ArrayList;
import java.util.List;

/** Horizontal thumbnails for media stored in Firestore (Base64 images). */
public class MediaImageAdapter
        extends RecyclerView.Adapter<MediaImageAdapter.MediaViewHolder> {

    public interface OnMediaClickListener {
        void onMediaClick(StoredMedia media);
    }

    private final List<StoredMedia> items = new ArrayList<>();
    private final OnMediaClickListener listener;

    public MediaImageAdapter(OnMediaClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<StoredMedia> media) {
        items.clear();
        if (media != null) {
            items.addAll(media);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MediaViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attachment, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MediaViewHolder extends RecyclerView.ViewHolder {

        private final ImageView image;
        private final View remove;

        MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.ivAttachment);
            remove = itemView.findViewById(R.id.btnRemoveAttachment);
        }

        void bind(StoredMedia media, OnMediaClickListener listener) {
            remove.setVisibility(View.GONE);
            Bitmap bitmap = MediaCompressor.decodeBitmap(media.getData());
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
            } else {
                image.setImageDrawable(null);
            }
            image.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMediaClick(media);
                }
            });
        }
    }
}
