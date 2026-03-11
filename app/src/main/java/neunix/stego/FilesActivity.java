package neunix.stego;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class FilesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_files);

        ImageView back = findViewById(R.id.backButton);
        Button embedded = findViewById(R.id.btnEmbeddedFiles);
        Button extracted = findViewById(R.id.btnExtractedFiles);

        back.setOnClickListener(v -> finish());

        embedded.setOnClickListener(v ->
                startActivity(new Intent(this, EmbeddedFilesActivity.class))
        );

        extracted.setOnClickListener(v ->
                startActivity(new Intent(this, ExtractedFilesActivity.class))
        );
    }
}