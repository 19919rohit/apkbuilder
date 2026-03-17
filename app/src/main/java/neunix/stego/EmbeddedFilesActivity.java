package neunix.stego;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmbeddedFilesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private List<File> fileList = new ArrayList<>();
    private FileAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_embedded_files);

        ImageView back = findViewById(R.id.backButton);
        recyclerView = findViewById(R.id.recyclerEmbeddedFiles);
        emptyText = findViewById(R.id.emptyText);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        back.setOnClickListener(v -> finish());

        loadFiles();
    }

    private void loadFiles() {

        try {
            File dir = new File(getExternalFilesDir(null), "Embedded");
            if (!dir.exists()) dir.mkdirs();

            File[] files = dir.listFiles(file ->
                    file.isFile() &&
                            (file.getName().toLowerCase().endsWith(".png") ||
                             file.getName().toLowerCase().endsWith(".jpg") ||
                             file.getName().toLowerCase().endsWith(".jpeg"))
            );

            if (files == null || files.length == 0) {
                showEmpty();
                return;
            }

            // Sort latest → oldest
            Arrays.sort(files, (f1, f2) ->
                    Long.compare(f2.lastModified(), f1.lastModified()));

            fileList = new ArrayList<>(Arrays.asList(files));
            adapter.submitList(fileList);

            recyclerView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);

        } catch (Exception e) {
            e.printStackTrace();
            emptyText.setText("Error loading files");
            showEmpty();
        }
    }

    private void showEmpty() {
        recyclerView.setVisibility(View.GONE);
        emptyText.setVisibility(View.VISIBLE);
    }
}