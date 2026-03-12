package neunix.stego;

import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnEmbed;
    private Button btnExtract;
    private Button btnFiles;   // NEW

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        makeStatusBarTransparent();

        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        btnEmbed = findViewById(R.id.btnGoEmbed);
        btnExtract = findViewById(R.id.btnGoExtract);
        btnFiles = findViewById(R.id.btnGoFiles);   // NEW
    }

    private void setupClickListeners() {

        btnEmbed.setOnClickListener(v -> openActivity(EmbedActivity.class));

        btnExtract.setOnClickListener(v -> openActivity(ExtractActivity.class));

        btnFiles.setOnClickListener(v -> openActivity(FilesActivity.class)); // NEW
    }

    private void openActivity(Class<?> activity) {
        Intent intent = new Intent(MainActivity.this, activity);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void makeStatusBarTransparent() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }
}