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
import java.util.zip.GZIPInputStream;

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

        bindViews();
        setupPickers();
        setupButtons();
    }

    private void bindViews() {
        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        etPassword = findViewById(R.id.etPassword);
        progress = findViewById(R.id.progressBar);
        btnExtract = findViewById(R.id.btnExtract);
    }

    private void setupPickers() {

        ActivityResultLauncher<Intent> picker =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        r -> {
                            if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                                carrierUri = r.getData().getData();
                                new Thread(this::loadCarrier).start();
                            }
                        }
                );

        findViewById(R.id.pickCarrierBtn)
                .setOnClickListener(v -> pick(picker, "image/*"));
    }

    private void setupButtons() {
        btnExtract.setOnClickListener(v -> extract());
    }

    /* ================= IMAGE LOADING ================= */

    private void loadCarrier() {
        try {
            Bitmap bmp = decodeOptimized(carrierUri);
            if (bmp == null) throw new IOException("Invalid image");

            runOnUiThread(() -> {
                carrierPreview.setImageBitmap(bmp);
                tvCarrierInfo.setText("Carrier: " + fileName(carrierUri) +
                        "\nResolution: " + bmp.getWidth() + " x " + bmp.getHeight());
            });

        } catch (Exception e) {
            toast("Error: " + e.getMessage());
        }
    }

    private Bitmap decodeOptimized(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }

        int scale = 1;
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);

        while (maxDim / scale > 1600) scale *= 2;

        BitmapFactory.Options real = new BitmapFactory.Options();
        real.inSampleSize = scale;

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, real);
        }
    }

    /* ================= EXTRACTION ================= */

    private void extract() {
        if (carrierUri == null) {
            toast("Select stego image first");
            return;
        }

        progress.setVisibility(View.VISIBLE);
        btnExtract.setEnabled(false);

        new Thread(() -> {
            try {
                Bitmap bmp = decodeOptimized(carrierUri);
                if (bmp == null)
                    throw new IOException("Invalid stego image");

                // Extract using the same engine as EmbedActivity
                StegEngineCore.ExtractedData ex =
                        StegEngineCore.extract(
                                bmp,
                                etPassword.getText().toString().trim()
                        );

                // ======== GZIP decompression ========
                byte[] extractedData = decompressGzip(ex.data);

                File outFile = Utils.getTimestampedFile(ex.fileName, "Extracted");

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(extractedData);
                }

                runOnUiThread(() ->
                        toast("Extracted → " + outFile.getAbsolutePath())
                );

            } catch (Exception e) {
                runOnUiThread(() ->
                        toast("Extract failed: " + e.getMessage())
                );

            } finally {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnExtract.setEnabled(true);
                });
            }

        }).start();
    }

    /* ================= GZIP HELPERS ================= */

    private byte[] decompressGzip(byte[] data) throws IOException {

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzip = new GZIPInputStream(bais);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int n;
            while ((n = gzip.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    /* ================= URI HELPERS ================= */

    private String fileName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst())
                return c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        }
        return "file";
    }

    private void pick(ActivityResultLauncher<Intent> l, String type) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        l.launch(i);
    }

    private void toast(String s) {
        runOnUiThread(() ->
                Toast.makeText(this, s, Toast.LENGTH_LONG).show()
        );
    }
}