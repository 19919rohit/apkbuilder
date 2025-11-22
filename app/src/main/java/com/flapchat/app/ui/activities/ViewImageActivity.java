package com.flapchat.app.ui.activities;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.flapchat.app.R;
import com.flapchat.app.utils.ImageLoader;

public class ViewImageActivity extends AppCompatActivity {

    private ImageView fullImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_image);

        fullImageView = findViewById(R.id.image_full);

        String imageUrl = getIntent().getStringExtra("IMAGE_URL");
        if (imageUrl != null) {
            ImageLoader.load(this, imageUrl, fullImageView);
        }
    }
}