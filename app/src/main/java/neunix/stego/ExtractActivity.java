package neunix.stego;

// Android core
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

// AndroidX for Activity Results
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

// Java I/O
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// Java Utilities
import java.util.Arrays;

public class ExtractActivity extends AppCompatActivity {

    private Uri carrierUri;
    private ImageView carrierPreview;
    private TextView tvCarrierInfo;
    private EditText etPassword;
    private ProgressBar progress;
    private Button btnExtract;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extract);

        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        etPassword = findViewById(R.id.etPassword);
        progress = findViewById(R.id.progressBar);
        btnExtract = findViewById(R.id.btnExtract);

        ActivityResultLauncher<Intent> carrierPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        carrierUri = r.getData().getData();
                        loadCarrierPreview();
                    }
                });

        findViewById(R.id.pickCarrierBtn).setOnClickListener(v ->
                pick(carrierPicker, "image/*"));

        btnExtract.setOnClickListener(v -> extract());
    }

    private void loadCarrierPreview() {
        try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            carrierPreview.setImageBitmap(bmp);
            tvCarrierInfo.setText("Carrier: " + fileName(carrierUri));
        } catch (Exception e) {
            toast("Error loading carrier: " + e.getMessage());
        }
    }

    private void extract() {
        if (carrierUri == null) {
            toast("Select a carrier image first!");
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

                byte[] data = StegEngineCore.extract(bmp, etPassword.getText().toString());

                int nameLen = data[0] & 0xFF;
                String originalName = new String(data, 1, nameLen, "UTF-8");
                byte[] payload = Arrays.copyOfRange(data, 1 + nameLen, data.length);

                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "StegoBox");
                if (!dir.exists()) dir.mkdirs();

                File outFile = new File(dir, originalName);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(payload);
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
            if (c != null && c.moveToFirst()) {
                return c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        } catch (Exception ignored) {}
        return "file";
    }

    private void pick(ActivityResultLauncher<Intent> l, String type) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType(type);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        l.launch(i);
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }
}