package neunix.stego;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class EmbedActivity extends AppCompatActivity {

    private Uri carrierUri;
    private Uri payloadUri;

    private Bitmap carrierBitmap;

    private ImageView carrierPreview;
    private TextView tvCarrierInfo, tvPayloadInfo;
    private EditText etPassword, etTextMessage;
    private ProgressBar progress;
    private Button btnEmbed;

    private RadioGroup radioPayloadType;
    private LinearLayout layoutFile, layoutText;
    private Spinner spinnerExpansionMode;

    // 🔐 AES-GCM CONFIG
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 120000;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_embed);

        bindViews();
        setupDropdown();
        setupPickers();
        setupValidation();
    }

    private void bindViews() {
        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        tvPayloadInfo = findViewById(R.id.tvPayloadInfo);
        etPassword = findViewById(R.id.etPassword);
        etTextMessage = findViewById(R.id.etTextMessage);
        progress = findViewById(R.id.progressBar);
        btnEmbed = findViewById(R.id.btnEmbed);

        radioPayloadType = findViewById(R.id.radioPayloadType);
        layoutFile = findViewById(R.id.layoutFile);
        layoutText = findViewById(R.id.layoutText);
        spinnerExpansionMode = findViewById(R.id.spinnerExpand);
    }

    private void setupDropdown() {

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Normal", "Expand 200%", "Expand 400%"}
        );

        spinnerExpansionMode.setAdapter(adapter);

        spinnerExpansionMode.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        refreshCapacity();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
    }

    private void setupPickers() {

        ActivityResultLauncher<Intent> carrierPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {

                        carrierUri = r.getData().getData();

                        new Thread(this::loadCarrier).start();

                        validateReady();
                    }
                });

        ActivityResultLauncher<Intent> payloadPicker =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {

                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {

                        payloadUri = r.getData().getData();
                        tvPayloadInfo.setText("Payload: " + fileName(payloadUri));

                        validateReady();
                    }
                });

        findViewById(R.id.pickCarrierBtn)
                .setOnClickListener(v -> pick(carrierPicker, "image/*"));

        findViewById(R.id.pickPayloadBtn)
                .setOnClickListener(v -> pick(payloadPicker, "*/*"));

        radioPayloadType.setOnCheckedChangeListener((g, id) -> {

            boolean isText = id == R.id.radioText;

            layoutText.setVisibility(isText ? View.VISIBLE : View.GONE);
            layoutFile.setVisibility(isText ? View.GONE : View.VISIBLE);

            validateReady();
        });

        btnEmbed.setOnClickListener(v -> embed());
    }

    private void setupValidation() {
        etTextMessage.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
    }

    private final TextWatcher watcher = new TextWatcher() {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

            refreshCapacity();
            validateReady();
        }

        @Override
        public void afterTextChanged(Editable s) {}
    };

    private void validateReady() {

        boolean ready =
                carrierUri != null &&
                (radioPayloadType.getCheckedRadioButtonId() == R.id.radioText
                        ? !etTextMessage.getText().toString().trim().isEmpty()
                        : payloadUri != null);

        btnEmbed.setEnabled(ready);
        btnEmbed.setAlpha(ready ? 1f : 0.5f);
    }

    private void refreshCapacity() {

        if (carrierBitmap == null) return;

        try {

            Bitmap bmp = carrierBitmap;

            int mode = spinnerExpansionMode.getSelectedItemPosition();

            if (mode == 1) bmp = ContentAwareExpander.expand(bmp, 2);
            if (mode == 2) bmp = ContentAwareExpander.expand(bmp, 4);

            int max = StegEngineCore.getMaxPayloadSize(bmp);

            int current =
                    etTextMessage.getText()
                            .toString()
                            .getBytes(StandardCharsets.UTF_8)
                            .length;

            runOnUiThread(() -> {

                tvCarrierInfo.setText(
                        "Capacity: " +
                                Utils.formatSize(current) +
                                " / " +
                                Utils.formatSize(max)
                );

                tvCarrierInfo.setTextColor(
                        getColor(current > max ? R.color.red : R.color.green)
                );
            });

        } catch (Exception ignored) {}
    }

    private Bitmap decodeOptimized(Uri uri) throws Exception {

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

    private void loadCarrier() {

        try {

            carrierBitmap = decodeOptimized(carrierUri);

            Bitmap bmp = carrierBitmap;

            int max = StegEngineCore.getMaxPayloadSize(bmp);

            runOnUiThread(() -> {

                carrierPreview.setImageBitmap(bmp);

                tvCarrierInfo.setText(
                        "Resolution: " +
                                bmp.getWidth() +
                                " x " +
                                bmp.getHeight() +
                                "\nMax payload: " +
                                Utils.formatSize(max)
                );
            });

        } catch (Exception e) {

            toast("Carrier error: " + e.getMessage());
        }
    }

    private void embed() {

        if (carrierBitmap == null) {

            toast("Select carrier image first");
            return;
        }

        progress.setVisibility(View.VISIBLE);
        btnEmbed.setEnabled(false);

        new Thread(() -> {

            try {

                Bitmap bmp = carrierBitmap;

                int mode = spinnerExpansionMode.getSelectedItemPosition();

                if (mode == 1) bmp = ContentAwareExpander.expand(bmp, 2);
                if (mode == 2) bmp = ContentAwareExpander.expand(bmp, 4);

                byte[] payloadBytes;
                String originalName;

                if (radioPayloadType.getCheckedRadioButtonId() == R.id.radioText) {

                    String text = etTextMessage.getText().toString().trim();

                    if (text.isEmpty())
                        throw new IllegalArgumentException("Text message empty");

                    payloadBytes = text.getBytes(StandardCharsets.UTF_8);
                    originalName = "message.txt";

                } else {

                    if (payloadUri == null)
                        throw new IllegalArgumentException("Select payload file");

                    originalName = fileName(payloadUri);

                    try (InputStream in = getContentResolver().openInputStream(payloadUri);
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                        byte[] buf = new byte[8192];
                        int n;

                        while ((n = in.read(buf)) != -1)
                            baos.write(buf, 0, n);

                        payloadBytes = baos.toByteArray();
                    }
                }

                int capacity = StegEngineCore.getMaxPayloadSize(bmp);

                if (payloadBytes.length > capacity)
                    throw new IllegalArgumentException("Payload too large for this image");

                payloadBytes =
                        encryptIfNeeded(
                                payloadBytes,
                                etPassword.getText().toString()
                        );

                File outFile =
                        Utils.getTimestampedFile(
                                "stego.png",
                                "Embedded"
                        );

                try (FileOutputStream out = new FileOutputStream(outFile)) {

                    StegEngineCore.embed(
                            bmp,
                            payloadBytes,
                            originalName,
                            "",
                            out
                    );
                }

                runOnUiThread(() ->
                        toast("Embedded → " + outFile.getAbsolutePath())
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        toast("Embed failed: " + e.getMessage())
                );

            } finally {

                runOnUiThread(() -> {

                    progress.setVisibility(View.GONE);
                    btnEmbed.setEnabled(true);
                });
            }

        }).start();
    }

    private byte[] encryptIfNeeded(byte[] data, String password) throws Exception {

        if (password == null || password.isEmpty()) {

            byte[] out = new byte[data.length + 1];
            out[0] = 0;

            System.arraycopy(data, 0, out, 1, data.length);
            return out;
        }

        SecureRandom random = SecureRandom.getInstanceStrong();

        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        SecretKey key = deriveKey(password, salt);

        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(128, iv)
        );

        byte[] cipherText = cipher.doFinal(data);

        byte[] output =
                new byte[
                        salt.length +
                        iv.length +
                        cipherText.length
                ];

        System.arraycopy(salt, 0, output, 0, salt.length);
        System.arraycopy(iv, 0, output, salt.length, iv.length);
        System.arraycopy(cipherText, 0, output,
                salt.length + iv.length,
                cipherText.length);

        byte[] finalOut = new byte[output.length + 1];
        finalOut[0] = 1;

        System.arraycopy(output, 0, finalOut, 1, output.length);

        return finalOut;
    }

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {

        SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        PBEKeySpec spec =
                new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);

        SecretKey tmp = factory.generateSecret(spec);

        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    private String fileName(Uri uri) {

        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {

            if (c != null && c.moveToFirst()) {

                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                if (idx >= 0)
                    return c.getString(idx);
            }
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