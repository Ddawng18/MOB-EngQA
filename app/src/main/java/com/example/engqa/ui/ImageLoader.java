package com.example.engqa.ui;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.example.engqa.data.Callback;
import com.example.engqa.data.ImageRepository;
import com.example.engqa.util.ImageRefs;

public final class ImageLoader {

    private ImageLoader() {
    }

    public static void load(ImageView view, String value, Runnable onError) {
        if (view == null) {
            return;
        }
        if (ImageRefs.isRef(value)) {
            view.setTag(value);
            ImageRepository repository = new ImageRepository();
            repository.loadBytes(value, new Callback<byte[]>() {
                @Override
                public void onSuccess(byte[] bytes) {
                    if (value.equals(view.getTag()) && view.isAttachedToWindow()) {
                        Glide.with(view).load(bytes).into(view);
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (onError != null) {
                        onError.run();
                    }
                }
            });
        } else {
            Glide.with(view).load(value).into(view);
        }
    }
}
