package neunix.stego;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.*;

public class ExtractedFilesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extracted_files);

        recyclerView = findViewById(R.id.recyclerExtractedFiles);
        emptyText = findViewById(R.id.emptyText);
        ImageView back = findViewById(R.id.backButton);

        back.setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFiles();
    }

    private void loadFiles() {

        File base = getExternalFilesDir(null);
        if (base == null) {
            showEmpty("Storage not available");
            return;
        }

        File dir = new File(base, "Extracted");
        if (!dir.exists()) dir.mkdirs();

        File[] filesArr = dir.listFiles();

        if (filesArr == null || filesArr.length == 0) {
            showEmpty("No files found");
            return;
        }

        List<File> fileList = new ArrayList<>(Arrays.asList(filesArr));

        // 🔥 sort latest first
        Collections.sort(fileList, (a, b) ->
                Long.compare(b.lastModified(), a.lastModified())
        );

        ExtractedFilesAdapter adapter = new ExtractedFilesAdapter(
                this,
                fileList,
                () -> showEmpty("No files left")
        );

        recyclerView.setAdapter(adapter);
        recyclerView.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
    }

    private void showEmpty(String msg) {
        emptyText.setText(msg);
        emptyText.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }
}