package neunix.pageflow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

        Button openReader = findViewById(R.id.openReader);

        openReader.setOnClickListener(v -> {

            Intent intent =
                    new Intent(MainActivity.this, PdfActivity.class);

            startActivity(intent);
        });
    }
}