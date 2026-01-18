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

public class EmbedActivity extends AppCompatActivity {

    private Uri carrierUri, payloadUri;
    private TextView tvCarrierInfo, tvPayloadInfo;
    private EditText etPassword, etOutput;
    private ProgressBar progress;
    private Button btnEmbed;
    private ImageView carrierPreview;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_embed);

        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        tvPayloadInfo = findViewById(R.id.tvPayloadInfo);
        carrierPreview = findViewById(R.id.carrierPreview);
        etPassword = findViewById(R.id.etPassword);
        etOutput = findViewById(R.id.etOutputFileName);
        progress = findViewById(R.id.progressBar);
        btnEmbed = findViewById(R.id.btnEmbed);

        ActivityResultLauncher<Intent> carrierPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        carrierUri = r.getData().getData();
                        showCarrierPreview();
                        analyzeCarrier();
                    }
                });

        ActivityResultLauncher<Intent> payloadPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        payloadUri = r.getData().getData();
                        tvPayloadInfo.setText("Payload: " + fileName(payloadUri));
                    }
                });

        findViewById(R.id.pickCarrierBtn).setOnClickListener(v ->
                pick(carrierPicker, "image/*"));

        findViewById(R.id.pickPayloadBtn).setOnClickListener(v ->
                pick(payloadPicker, "*/*"));

        btnEmbed.setOnClickListener(v -> embed());
    }

    private void showCarrierPreview() {
        try {
            Bitmap bmp = loadBitmap(carrierUri);
            carrierPreview.setImageBitmap(bmp);
        } catch (Exception e) {
            toast("Cannot show preview: " + e.getMessage());
        }
    }

    private void analyzeCarrier() {
        try {
            Bitmap bmp = loadBitmap(carrierUri);
            int max = StegEngineCore.getMaxPayloadSize(bmp);
            tvCarrierInfo.setText("Carrier: " + fileName(carrierUri) +
                    "\nMax payload: " + max + " bytes");
        } catch (Exception e) {
            toast("Error analyzing carrier: " + e.getMessage());
        }
    }

    private void embed() {
        runAsync(() -> {
            try {
                if (carrierUri == null || payloadUri == null) {
                    toast("Select both carrier and payload first!");
                    return;
                }

                Bitmap bmp = loadBitmap(carrierUri);
                byte[] payload = readBytes(payloadUri);

                String name = etOutput.getText().toString();
                if (name.isEmpty()) name = "stego.png";

                File outFile = Utils.getOutputFile(this, name, "StegoBox"); // Custom save to Pictures/StegoBox
                try (OutputStream out = new FileOutputStream(outFile)) {
                    StegEngineCore.embed(bmp, payload, etPassword.getText().toString(), out);
                }

                toast("Embedded → " + outFile.getAbsolutePath());

            } catch (Exception e) {
                toast("Embed failed: " + e.getMessage());
            }
        });
    }

    private void runAsync(Runnable r) {
        progress.setVisibility(View.VISIBLE);
        btnEmbed.setEnabled(false);

        new Thread(() -> {
            try { r.run(); }
            catch (Exception e) { toast("Error: " + e.getMessage()); }
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                btnEmbed.setEnabled(true);
            });
        }).start();
    }

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
                return c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        } catch (Exception ignored) {}
        return "file";
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