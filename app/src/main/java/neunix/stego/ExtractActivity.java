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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExtractActivity extends AppCompatActivity {

    private Uri carrierUri;
    private Bitmap carrierBitmap;

    private ImageView carrierPreview;
    private TextView tvCarrierInfo;
    private EditText etPassword;
    private ProgressBar progressBar;
    private Button btnExtract;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<Intent> picker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extract);

        bind();
        setupPicker();

        findViewById(R.id.pickCarrierBtn)
                .setOnClickListener(v -> pick());

        btnExtract.setOnClickListener(v -> extract());

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    // ================= UI =================

    private void bind() {
        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        etPassword = findViewById(R.id.etPassword);
        progressBar = findViewById(R.id.progressBar);
        btnExtract = findViewById(R.id.btnExtract);
    }

    // ================= PICKER =================

    private void setupPicker() {
        picker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        carrierUri = r.getData().getData();
                        executor.execute(this::loadImage);
                    }
                });
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        picker.launch(i);
    }

    // ================= IMAGE LOAD =================

    private void loadImage() {
        try {
            String name = getName(carrierUri).toLowerCase();

            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                carrierBitmap = sanitize(JPGtoPNG.convert(this, carrierUri));
            } else {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

                try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
                    carrierBitmap = sanitize(BitmapFactory.decodeStream(in, null, opts));
                }
            }

            runOnUiThread(() -> {
                carrierPreview.setImageBitmap(carrierBitmap);
                tvCarrierInfo.setText(
                        "Resolution: " +
                                carrierBitmap.getWidth() + " x " +
                                carrierBitmap.getHeight()
                );
            });

        } catch (Exception e) {
            e.printStackTrace();
            toast("Failed to load image");
        }
    }

    // ================= SANITIZE =================

    private Bitmap sanitize(Bitmap input) {
        if (input == null) throw new RuntimeException("Bitmap null");

        if (input.getConfig() != Bitmap.Config.ARGB_8888) {
            input = input.copy(Bitmap.Config.ARGB_8888, true);
        }

        if (!input.isMutable()) {
            input = input.copy(Bitmap.Config.ARGB_8888, true);
        }

        return input;
    }

    // ================= SAFE EXTRACT =================

    private static class ExtractResult {
        boolean success;
        String error;
        StegEngineCore.ExtractedData data;
    }

    private ExtractResult safeExtract(Bitmap bmp, String password) {

        ExtractResult r = new ExtractResult();

        try {
            StegEngineCore.ExtractedData data =
                    StegEngineCore.extract(bmp, password);

            if (data == null || data.data == null || data.data.length == 0) {
                r.error = "Not a Stegora image";
                return r;
            }

            r.success = true;
            r.data = data;
            return r;

        } catch (Exception e) {

            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();

            if (msg.contains("password") || msg.contains("decrypt")) {
                r.error = "Wrong password";
            } else if (msg.contains("header") || msg.contains("magic")) {
                r.error = "Not a Stegora image";
            } else if (msg.contains("integrity") || msg.contains("corrupt")) {
                r.error = "Wrong password";
            } else {
                r.error = "Not a Stegora image";
            }

            return r;
        }
    }

    private boolean isPasswordMissing(String password, String error) {
        return (password == null || password.isEmpty())
                && error.equals("Wrong password");
    }

    // ================= EXTRACT =================

    private void extract() {

        if (carrierBitmap == null) {
            toast("Select stego image first");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnExtract.setEnabled(false);

        executor.execute(() -> {

            try {

                Bitmap bmp = sanitize(carrierBitmap);
                String password = etPassword.getText().toString();

                ExtractResult result = safeExtract(bmp, password);

                if (!result.success) {

                    String error = result.error;

                    if (isPasswordMissing(password, error)) {
                        error = "This image is password protected";
                    }

                    String finalError = error;

                    runOnUiThread(() -> toast(finalError));
                    return;
                }

                File outFile = Utils.getTimestampedFile(
                        this,
                        result.data.filename,
                        Utils.DIR_EXTRACTED
                );

                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    out.write(result.data.data);
                }

                runOnUiThread(() ->
                        toast("Extracted successfully"));

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() ->
                        toast("Not a Stegora image"));

            } finally {

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnExtract.setEnabled(true);
                });
            }
        });
    }

    // ================= HELPERS =================

    private String getName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {}
        return "file";
    }

    private void toast(String s) {
        runOnUiThread(() ->
                Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
}