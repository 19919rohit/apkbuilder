package neunix.stego;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;

public class ExtractActivity extends AppCompatActivity {

    private Uri carrierUri;
    private ImageView carrierPreview;
    private TextView tvCarrierInfo;
    private EditText etPassword;
    private ProgressBar progress;
    private Button btnExtract;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_extract);

        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        etPassword = findViewById(R.id.etPassword);
        progress = findViewById(R.id.progressBar);
        btnExtract = findViewById(R.id.btnExtract);

        ActivityResultLauncher<Intent> picker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        carrierUri = r.getData().getData();
                        loadCarrier();
                    }
                });

        findViewById(R.id.pickCarrierBtn).setOnClickListener(v -> pick(picker, "image/*"));
        btnExtract.setOnClickListener(v -> extract());
    }

    private void loadCarrier() {
        try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            carrierPreview.setImageBitmap(bmp);
            tvCarrierInfo.setText("Carrier: " + fileName(carrierUri));
        } catch (Exception e) {
            toast("Error: " + e.getMessage());
        }
    }

    private void extract() {
        if (carrierUri == null) {
            toast("Select stego image");
            return;
        }

        progress.setVisibility(View.VISIBLE);
        btnExtract.setEnabled(false);

        new Thread(() -> {
            try {
                Bitmap bmp;
                try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
                    bmp = BitmapFactory.decodeStream(in);
                }

                StegEngineCore.ExtractedData ex =
                        StegEngineCore.extract(bmp, etPassword.getText().toString());

                File outFile = Utils.getTimestampedFile(ex.fileName, "Extracted");
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(ex.data);
                }

                runOnUiThread(() -> toast("Extracted → " + outFile.getAbsolutePath()));

            } catch (Exception e) {
                runOnUiThread(() -> toast("Extract failed: " + e.getMessage()));
            } finally {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnExtract.setEnabled(true);
                });
            }
        }).start();
    }

    private String fileName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst())
                return c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }
        return "file";
    }

    private void pick(ActivityResultLauncher<Intent> l, String type) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        l.launch(i);
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }
}