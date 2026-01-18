package neunix.stego;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnGoEmbed).setOnClickListener(v ->
                startActivity(new Intent(this, EmbedActivity.class)));

        findViewById(R.id.btnGoExtract).setOnClickListener(v ->
                startActivity(new Intent(this, ExtractActivity.class)));
    }
}