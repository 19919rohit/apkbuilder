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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_embedded_files);

        // Bind views
        recyclerView = findViewById(R.id.recyclerEmbeddedFiles);
        emptyText = findViewById(R.id.emptyText);
        ImageView back = findViewById(R.id.backButton);
        back.setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadFiles();
    }

    private void loadFiles() {
        File dir = new File(getExternalFilesDir(null), "Embedded");
        if (!dir.exists()) dir.mkdirs();

        File[] filesArr = dir.listFiles();
        if (filesArr == null || filesArr.length == 0) {
            emptyText.setVisibility(View.VISIBLE);
            return;
        }

        List<File> fileList = new ArrayList<>(Arrays.asList(filesArr));

        EmbeddedFilesAdapter adapter = new EmbeddedFilesAdapter(this, fileList,
            () -> emptyText.setVisibility(View.VISIBLE)
        );
        recyclerView.setAdapter(adapter);
        emptyText.setVisibility(View.GONE);
    }
}