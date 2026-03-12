package neunix.stego;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class FilesActivity extends AppCompatActivity {

    private ImageView backButton;
    private Button btnEmbedded;
    private Button btnExtracted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_files);

        bindViews();
        setupListeners();
    }

    // ================= BIND VIEWS =================
    private void bindViews() {
        backButton = findViewById(R.id.backButton);
        btnEmbedded = findViewById(R.id.btnEmbeddedFiles);
        btnExtracted = findViewById(R.id.btnExtractedFiles);
    }

    // ================= LISTENERS =================
    private void setupListeners() {

        backButton.setOnClickListener(v -> finish());

        btnEmbedded.setOnClickListener(v ->
                openActivity(EmbeddedFilesActivity.class, v)
        );

        btnExtracted.setOnClickListener(v ->
                openActivity(ExtractedFilesActivity.class, v)
        );
    }

    // ================= SAFE NAVIGATION =================
    private void openActivity(Class<?> target, View clickedView) {

        // Prevent double taps
        clickedView.setEnabled(false);

        Intent intent = new Intent(this, target);
        startActivity(intent);

        // optional animation (if defined)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        clickedView.postDelayed(() -> clickedView.setEnabled(true), 400);
    }
}