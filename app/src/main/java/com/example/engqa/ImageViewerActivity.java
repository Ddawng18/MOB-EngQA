package com.example.engqa;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

/** Full-screen image viewer with pinch-to-zoom support (via the ImageView scale type). */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "com.example.engqa.extra.IMAGE_URL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(android.graphics.Color.BLACK),
                SystemBarStyle.dark(android.graphics.Color.BLACK));
        setContentView(R.layout.activity_image_viewer);

        ImageView image = findViewById(R.id.ivFullImage);
        String url = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        if (url != null) {
            Glide.with(this).load(url).into(image);
        }
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
    }
}
