package com.example.engqa;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.engqa.data.Callback;
import com.example.engqa.data.FirestoreMediaRepository;
import com.example.engqa.media.MediaCompressor;
import com.example.engqa.model.StoredMedia;

/** Full-screen image viewer: Firestore media (Base64) or a legacy URL. */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "com.example.engqa.extra.IMAGE_URL";
    public static final String EXTRA_QUESTION_ID = "com.example.engqa.extra.QUESTION_ID";
    public static final String EXTRA_MEDIA_ID = "com.example.engqa.extra.MEDIA_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(android.graphics.Color.BLACK),
                SystemBarStyle.dark(android.graphics.Color.BLACK));
        setContentView(R.layout.activity_image_viewer);

        ImageView image = findViewById(R.id.ivFullImage);
        ProgressBar progress = findViewById(R.id.progressImage);
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        String questionId = getIntent().getStringExtra(EXTRA_QUESTION_ID);
        String mediaId = getIntent().getStringExtra(EXTRA_MEDIA_ID);
        String url = getIntent().getStringExtra(EXTRA_IMAGE_URL);

        if (questionId != null && mediaId != null) {
            progress.setVisibility(View.VISIBLE);
            new FirestoreMediaRepository().load(questionId, mediaId, new Callback<StoredMedia>() {
                @Override
                public void onSuccess(StoredMedia media) {
                    progress.setVisibility(View.GONE);
                    if (media == null) {
                        Toast.makeText(ImageViewerActivity.this, R.string.state_error,
                                Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    android.graphics.Bitmap bitmap = MediaCompressor.decodeBitmap(media.getData());
                    if (bitmap == null) {
                        Toast.makeText(ImageViewerActivity.this, R.string.state_error,
                                Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    image.setImageBitmap(bitmap);
                }

                @Override
                public void onError(Exception e) {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(ImageViewerActivity.this, R.string.state_error,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        } else if (url != null) {
            Glide.with(this).load(url).into(image);
        }
    }
}
