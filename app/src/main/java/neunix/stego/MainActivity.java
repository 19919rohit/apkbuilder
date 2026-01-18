package neunix.stego;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;

public class MainActivity extends AppCompatActivity {

    private Uri carrierUri, payloadUri;

    private ImageView carrierPreview;
    private TextView tvCarrierInfo, tvPayloadInfo, statusText;
    private EditText etPassword, etOutput;
    private ProgressBar progress;
    private Button btnEmbed, btnExtract;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        tvPayloadInfo = findViewById(R.id.tvPayloadInfo);
        statusText = findViewById(R.id.statusText);
        etPassword = findViewById(R.id.etPassword);
        etOutput = findViewById(R.id.etOutputFileName);
        progress = findViewById(R.id.progressBar);
        btnEmbed = findViewById(R.id.btnEmbed);
        btnExtract = findViewById(R.id.btnExtract);

        ActivityResultLauncher<Intent> carrierPicker =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        r -> {
                            if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                                carrierUri = r.getData().getData();
                                showCarrierPreview();
                                analyzeCarrier();
                            }
                        });

        ActivityResultLauncher<Intent> payloadPicker =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        r -> {
                            if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                                payloadUri = r.getData().getData();
                                tvPayloadInfo.setText("Payload: " + fileName(payloadUri));
                            }
                        });

        findViewById(R.id.btnSelectCarrier)
                .setOnClickListener(v -> pick(carrierPicker, "image/*"));

        findViewById(R.id.btnSelectPayload)
                .setOnClickListener(v -> pick(payloadPicker, "*/*"));

        btnEmbed.setOnClickListener(v -> embed());
        btnExtract.setOnClickListener(v -> extract());
    }

    /* ---------------- UI helpers ---------------- */

    private void showCarrierPreview() {
        try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            carrierPreview.setImageBitmap(bmp);
        } catch (Exception e) {
            toast("Preview failed: " + e.getMessage());
        }
    }

    private void analyzeCarrier() {
        try {
            Bitmap bmp = loadBitmap(carrierUri);
            int max = StegEngineCore.getMaxPayloadSize(bmp);
            tvCarrierInfo.setText(
                    "Carrier: " + fileName(carrierUri) +
                    "\nMax payload: " + max + " bytes"
            );
        } catch (Exception e) {
            toast("Analyze error: " + e.getMessage());
        }
    }

    /* ---------------- Embed ---------------- */

    private void embed() {
        runAsync(() -> {
            if (carrierUri == null || payloadUri == null) {
                toast("Select carrier and payload first");
                return;
            }

            try {
                Bitmap bmp = loadBitmap(carrierUri);
                byte[] payload = readBytes(payloadUri);

                String name = etOutput.getText().toString().trim();
                if (name.isEmpty()) name = "stego.png";

                Uri outUri = createImageOutput(name);
                try (OutputStream out =
                             getContentResolver().openOutputStream(outUri)) {

                    StegEngineCore.embed(
                            bmp,
                            payload,
                            etPassword.getText().toString(),
                            out
                    );
                }

                toast("Saved to Pictures/StegoBox/" + name);

            } catch (Exception e) {
                toast("Embed failed: " + e.getMessage());
            }
        });
    }

    /* ---------------- Extract ---------------- */

    private void extract() {
        runAsync(() -> {
            if (carrierUri == null) {
                toast("Select a stego image first");
                return;
            }

            try {
                Bitmap bmp = loadBitmap(carrierUri);
                byte[] data =
                        StegEngineCore.extract(bmp, etPassword.getText().toString());

                String name = etOutput.getText().toString().trim();
                if (name.isEmpty()) name = "extracted.bin";

                Uri outUri = createBinaryOutput(name);
                try (OutputStream out =
                             getContentResolver().openOutputStream(outUri)) {
                    out.write(data);
                }

                toast("Extracted to Downloads/StegoBox/" + name);

            } catch (Exception e) {
                toast("Extract failed: " + e.getMessage());
            }
        });
    }

    /* ---------------- Storage ---------------- */

    private Uri createImageOutput(String name) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/StegoBox");

        return getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
    }

    private Uri createBinaryOutput(String name) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
        v.put(MediaStore.Downloads.RELATIVE_PATH,
                "Download/StegoBox");

        return getContentResolver()
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
    }

    /* ---------------- Utils ---------------- */

    private Bitmap loadBitmap(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in);
        }
    }

    private byte[] readBytes(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private String fileName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(
                        c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                );
            }
        }
        return "file";
    }

    private void pick(ActivityResultLauncher<Intent> l, String t) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType(t);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        l.launch(i);
    }

    private void runAsync(Runnable r) {
        runOnUiThread(() -> {
            progress.setVisibility(View.VISIBLE);
            btnEmbed.setEnabled(false);
            btnExtract.setEnabled(false);
        });

        new Thread(() -> {
            r.run();
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                btnEmbed.setEnabled(true);
                btnExtract.setEnabled(true);
            });
        }).start();
    }

    private void toast(String m) {
        runOnUiThread(() ->
                Toast.makeText(this, m, Toast.LENGTH_LONG).show());
    }
}