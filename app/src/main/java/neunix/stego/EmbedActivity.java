package neunix.stego;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;

public class EmbedActivity extends AppCompatActivity {

    private Uri carrierUri, payloadUri;
    private ImageView carrierPreview;
    private TextView tvCarrierInfo, tvPayloadInfo;
    private EditText etPassword, etTextMessage;
    private ProgressBar progress;
    private Button btnEmbed, btnUseText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_embed);

        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        tvPayloadInfo = findViewById(R.id.tvPayloadInfo);
        etPassword = findViewById(R.id.etPassword);
        etTextMessage = findViewById(R.id.etTextMessage);
        progress = findViewById(R.id.progressBar);
        btnEmbed = findViewById(R.id.btnEmbed);
        btnUseText = findViewById(R.id.btnUseText);

        ActivityResultLauncher<Intent> carrierPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        carrierUri = r.getData().getData();
                        loadCarrier();
                    }
                });

        ActivityResultLauncher<Intent> payloadPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        payloadUri = r.getData().getData();
                        tvPayloadInfo.setText("Payload: " + fileName(payloadUri));
                    }
                });

        findViewById(R.id.pickCarrierBtn).setOnClickListener(v -> pick(carrierPicker, "image/*"));
        findViewById(R.id.pickPayloadBtn).setOnClickListener(v -> pick(payloadPicker, "*/*"));

        // Use entered text instead of file
        btnUseText.setOnClickListener(v -> {
            String text = etTextMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                payloadUri = null; // ignore file
                tvPayloadInfo.setText("Text payload: " + (text.length() > 30 ? text.substring(0, 30) + "..." : text));
            } else {
                toast("Enter text to embed!");
            }
        });

        btnEmbed.setOnClickListener(v -> embed());
    }

    private void loadCarrier() {
        try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            carrierPreview.setImageBitmap(bmp);

            int max = StegEngineCore.getMaxPayloadSize(bmp);
            tvCarrierInfo.setText("Carrier: " + fileName(carrierUri) + "\nMax payload: " + Utils.formatSize(max));
        } catch (Exception e) {
            toast("Error loading carrier: " + e.getMessage());
        }
    }

    private void embed() {
        if (carrierUri == null) {
            toast("Select carrier image first!");
            return;
        }

        progress.setVisibility(View.VISIBLE);
        btnEmbed.setEnabled(false);

        new Thread(() -> {
            try {
                Bitmap bmp;
                try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
                    bmp = BitmapFactory.decodeStream(in);
                }

                byte[] payloadBytes;
                String originalName;

                if (payloadUri != null) {
                    // File payload
                    originalName = fileName(payloadUri);
                    try (InputStream in = getContentResolver().openInputStream(payloadUri);
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
                        payloadBytes = baos.toByteArray();
                    }
                } else {
                    // Text payload
                    String text = etTextMessage.getText().toString();
                    payloadBytes = text.getBytes("UTF-8");
                    originalName = "message.txt";
                }

                // Output stego image
                File outFile = Utils.getTimestampedFile("stego.png", "Embedded");
                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    StegEngineCore.embed(bmp, payloadBytes, originalName, etPassword.getText().toString(), out);
                }

                runOnUiThread(() -> toast("Payload embedded → " + outFile.getAbsolutePath()));

            } catch (Exception e) {
                runOnUiThread(() -> toast("Embed failed: " + e.getMessage()));
            } finally {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnEmbed.setEnabled(true);
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