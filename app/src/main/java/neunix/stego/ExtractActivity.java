package neunix.stego;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.FileOutputStream;
import java.io.InputStream;

public class ExtractActivity extends AppCompatActivity {

    private Uri carrierUri;
    private EditText etPassword, etOutput;
    private ProgressBar progress;
    private Button btnExtract;
    private ImageView carrierPreview;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_extract);

        etPassword = findViewById(R.id.etPassword);
        etOutput = findViewById(R.id.etOutputFileName);
        progress = findViewById(R.id.progressBar);
        btnExtract = findViewById(R.id.btnExtract);
        carrierPreview = findViewById(R.id.carrierPreview);

        ActivityResultLauncher<Intent> carrierPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        carrierUri = r.getData().getData();
                        showCarrierPreview();
                    }
                });

        findViewById(R.id.pickCarrierBtn).setOnClickListener(v ->
                pick(carrierPicker, "image/*"));

        btnExtract.setOnClickListener(v -> extract());
    }

    private void showCarrierPreview() {
        try {
            Bitmap bmp = loadBitmap(carrierUri);
            carrierPreview.setImageBitmap(bmp);
        } catch (Exception e) {
            toast("Cannot show preview: " + e.getMessage());
        }
    }

    private void extract() {
        runAsync(() -> {
            try {
                if (carrierUri == null) {
                    toast("Select a stego image first!");
                    return;
                }

                Bitmap bmp = loadBitmap(carrierUri);
                byte[] data = StegEngineCore.extract(bmp, etPassword.getText().toString());

                String name = etOutput.getText().toString();
                if (name.isEmpty()) name = "extracted.bin";

                FileOutputStream fos = Utils.getOutputFileStream(this, name, "StegoBox");
                fos.write(data);
                fos.close();

                toast("Extracted → " + name);

            } catch (Exception e) {
                toast("Extract failed: " + e.getMessage());
            }
        });
    }

    private void runAsync(Runnable r) {
        progress.setVisibility(View.VISIBLE);
        btnExtract.setEnabled(false);

        new Thread(() -> {
            try { r.run(); }
            catch (Exception e) { toast("Error: " + e.getMessage()); }
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                btnExtract.setEnabled(true);
            });
        }).start();
    }

    private Bitmap loadBitmap(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in);
        }
    }

    private void pick(ActivityResultLauncher<Intent> l, String t) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType(t);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        l.launch(i);
    }

    private void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
    }
}