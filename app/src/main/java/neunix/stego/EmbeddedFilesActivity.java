package neunix.stego;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmbeddedFilesActivity extends AppCompatActivity {

    ListView listView;
    TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_embedded_files);

        ImageView back = findViewById(R.id.backButton);
        listView = findViewById(R.id.listEmbeddedFiles);
        emptyText = findViewById(R.id.emptyText);

        back.setOnClickListener(v -> finish());

        loadFiles();
    }

    private void loadFiles() {

        File dir = new File(getExternalFilesDir(null), "embedded");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        File[] files = dir.listFiles();

        if (files == null || files.length == 0) {
            emptyText.setVisibility(TextView.VISIBLE);
            return;
        }

        List<File> fileList = new ArrayList<>(Arrays.asList(files));

        FileAdapter adapter = new FileAdapter(this, fileList);

        listView.setAdapter(adapter);
    }
}