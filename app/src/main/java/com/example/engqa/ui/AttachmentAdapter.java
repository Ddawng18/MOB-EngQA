package com.example.engqa.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.engqa.R;

import java.util.ArrayList;
import java.util.List;

/** Thumbnails of images attached while composing a question, each with a remove button. */
public class AttachmentAdapter extends RecyclerView.Adapter<AttachmentAdapter.AttachmentViewHolder> {

    public interface OnAttachmentRemoveListener {
        void onRemove(Uri uri);
    }

    private final List<Uri> uris = new ArrayList<>();
    private final OnAttachmentRemoveListener listener;

    public AttachmentAdapter(OnAttachmentRemoveListener listener) {
        this.listener = listener;
    }

    public void submit(List<Uri> newUris) {
        uris.clear();
        if (newUris != null) {
            uris.addAll(newUris);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AttachmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attachment, parent, false);
        return new AttachmentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttachmentViewHolder holder, int position) {
        holder.bind(uris.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return uris.size();
    }

    static class AttachmentViewHolder extends RecyclerView.ViewHolder {

        private final ImageView thumbnail;
        private final View remove;

        AttachmentViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.ivAttachment);
            remove = itemView.findViewById(R.id.btnRemoveAttachment);
        }

        void bind(Uri uri, OnAttachmentRemoveListener listener) {
            Glide.with(thumbnail).load(uri).into(thumbnail);
            remove.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemove(uri);
                }
            });
        }
    }
}
