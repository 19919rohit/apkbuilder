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

/**
 * ExtractActivity for Stegora
 * Automatically detects text messages and displays them
 * Saves payloads exactly as embedded
 */
public class ExtractActivity extends AppCompatActivity {

    private Uri carrierUri;
    private ImageView carrierPreview;
    private TextView tvCarrierInfo;
    private TextView extractedMessage;
    private EditText etPassword;
    private ProgressBar progress;
    private Button btnExtract;
    private Button selectImageBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extract);

        bindViews();
        setupPickers();
        setupButtons();

        // Back button
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());
    }

    private void bindViews() {
        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        extractedMessage = findViewById(R.id.extractedMessage);
        etPassword = findViewById(R.id.etPassword);
        progress = findViewById(R.id.progressBar);
        btnExtract = findViewById(R.id.btnExtract);
        selectImageBtn = findViewById(R.id.selectImageBtn);
    }

    private void setupPickers() {
        ActivityResultLauncher<Intent> picker =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                carrierUri = result.getData().getData();
                                new Thread(this::loadCarrier).start();
                            }
                        }
                );

        selectImageBtn.setOnClickListener(v -> pick(picker, "image/*"));
    }

    private void setupButtons() {
        btnExtract.setOnClickListener(v -> extract());
    }

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

    private void extract() {
        if (carrierUri == null) {
            toast("Select stego image first");
            return;
        }

        progress.setVisibility(View.VISIBLE);
        btnExtract.setEnabled(false);
        extractedMessage.setText("");

        new Thread(() -> {
            try {
                Bitmap bmp = decodeOptimized(carrierUri);
                if (bmp == null) throw new IOException("Invalid stego image");

                StegEngineCore.ExtractedData ex =
                        StegEngineCore.extract(
                                bmp,
                                etPassword.getText().toString().trim()
                        );

                byte[] payloadData = ex.data;
                String fileName = ex.fileName;

                // Save payload to Extracted folder
                File outFile = Utils.getTimestampedFile(this, fileName, "Extracted");
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(payloadData);
                }

                // Detect if payload is UTF-8 text
                String textPreview = null;
                try {
                    textPreview = new String(payloadData, "UTF-8");
                    // Only display if printable
                    if (!textPreview.chars().allMatch(c -> c >= 9 && c <= 126 || c == 10 || c == 13)) {
                        textPreview = null;
                    }
                } catch (Exception ignored) {}

                final String finalTextPreview = textPreview;
                runOnUiThread(() -> {
                    toast("Extracted and saved to files");
                    if (finalTextPreview != null) {
                        extractedMessage.setText(finalTextPreview);
                    } else {
                        extractedMessage.setText("Payload saved as file: " + outFile.getName());
                    }
                });

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
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
}