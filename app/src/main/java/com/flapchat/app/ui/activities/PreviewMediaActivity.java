package com.flapchat.app.ui.activities;

import android.net.Uri;
import android.os.Bundle;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import com.flapchat.app.R;

public class PreviewMediaActivity extends AppCompatActivity {

    private VideoView videoPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_media);

        videoPreview = findViewById(R.id.video_preview);

        String mediaUri = getIntent().getStringExtra("MEDIA_URI");
        if (mediaUri != null) {
            videoPreview.setVideoURI(Uri.parse(mediaUri));
            videoPreview.start();
        }
    }
}