package neunix.stego;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;

import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;

public class MainActivity extends AppCompatActivity {

    private Uri carrierUri, payloadUri;
    private TextView tvCarrierInfo, tvPayloadInfo, statusText;
    private EditText etPassword, etOutput;
    private ProgressBar progress;
    private Button btnEmbed, btnExtract;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        tvPayloadInfo = findViewById(R.id.tvPayloadInfo);
        statusText = findViewById(R.id.statusText);
        etPassword = findViewById(R.id.etPassword);
        etOutput = findViewById(R.id.etOutputFileName);
        progress = findViewById(R.id.progressBar);
        btnEmbed = findViewById(R.id.btnEmbed);
        btnExtract = findViewById(R.id.btnExtract);

        ActivityResultLauncher<Intent> carrierPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == Activity.RESULT_OK) {
                        carrierUri = r.getData().getData();
                        analyzeCarrier();
                    }
                });

        ActivityResultLauncher<Intent> payloadPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == Activity.RESULT_OK) {
                        payloadUri = r.getData().getData();
                        tvPayloadInfo.setText("Payload: " + fileName(payloadUri));
                    }
                });

        findViewById(R.id.btnSelectCarrier).setOnClickListener(v ->
                pick(carrierPicker, "image/*"));

        findViewById(R.id.btnSelectPayload).setOnClickListener(v ->
                pick(payloadPicker, "*/*"));

        btnEmbed.setOnClickListener(v -> embed());
        btnExtract.setOnClickListener(v -> extract());
    }

    private void analyzeCarrier() {
        try {
            Bitmap bmp = loadBitmap(carrierUri);
            int max = StegEngineCore.getMaxPayloadSize(bmp);
            tvCarrierInfo.setText("Carrier: " + fileName(carrierUri) +
                    "\nMax payload: " + max + " bytes");
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    private void embed() {
        runAsync(() -> {
            Bitmap bmp = loadBitmap(carrierUri);
            byte[] payload = readBytes(payloadUri);
            String name = etOutput.getText().toString();
            if (name.isEmpty()) name = "stego.png";

            OutputStream out = openFileOutput(name, MODE_PRIVATE);
            StegEngineCore.embed(bmp, payload, etPassword.getText().toString(), out);
            out.close();
            toast("Embedded → " + name);
        });
    }

    private void extract() {
        runAsync(() -> {
            Bitmap bmp = loadBitmap(carrierUri);
            byte[] data = StegEngineCore.extract(bmp, etPassword.getText().toString());
            String name = etOutput.getText().toString();
            if (name.isEmpty()) name = "extracted.bin";

            FileOutputStream fos = openFileOutput(name, MODE_PRIVATE);
            fos.write(data);
            fos.close();
            toast("Extracted → " + name);
        });
    }

    private void runAsync(Runnable r) {
        progress.setVisibility(View.VISIBLE);
        btnEmbed.setEnabled(false);
        btnExtract.setEnabled(false);

        new Thread(() -> {
            try { r.run(); }
            catch (Exception e) { toast(e.getMessage()); }
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                btnEmbed.setEnabled(true);
                btnExtract.setEnabled(true);
            });
        }).start();
    }

    private Bitmap loadBitmap(Uri uri) throws IOException {
        return BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
    }

    private byte[] readBytes(Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private String fileName(Uri uri) {
        Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null && c.moveToFirst()) {
            String n = c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            c.close();
            return n;
        }
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