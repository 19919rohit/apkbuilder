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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmbedActivity extends AppCompatActivity {

    private Uri carrierUri, payloadUri;
    private Bitmap carrierBitmap;

    private ImageView carrierPreview;
    private TextView tvCarrierInfo, tvFilePayloadInfo;
    private EditText etPassword, etTextMessage;
    private ProgressBar progress;
    private Button btnEmbed;

    private RadioGroup radioPayloadType;
    private LinearLayout layoutFile, layoutText;
    private Spinner spinnerExpansionMode;

    private final Map<Integer, Bitmap> expansionCache = new HashMap<>();
    private final ExecutorService executor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private ActivityResultLauncher<Intent> carrierPicker, payloadPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_embed);

        bindViews();
        setupDropdown();
        setupPickers();
        setupPayloadSwitch();
        setupValidation();

        btnEmbed.setOnClickListener(v -> embed());
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    private void bindViews() {
        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        tvFilePayloadInfo = findViewById(R.id.tvFilePayloadInfo);
        etPassword = findViewById(R.id.etPassword);
        etTextMessage = findViewById(R.id.etTextMessage);
        progress = findViewById(R.id.progressBar);
        btnEmbed = findViewById(R.id.btnEmbed);
        radioPayloadType = findViewById(R.id.radioPayloadType);
        layoutFile = findViewById(R.id.layoutFile);
        layoutText = findViewById(R.id.layoutText);
        spinnerExpansionMode = findViewById(R.id.spinnerExpand);

        findViewById(R.id.pickCarrierBtn)
                .setOnClickListener(v -> pick(carrierPicker, "image/*"));

        findViewById(R.id.pickPayloadBtn)
                .setOnClickListener(v -> pick(payloadPicker, "*/*"));
    }

    private void setupDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                new String[]{"Normal", "Expand 25%", "Expand 50%", "Expand 100%"}
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerExpansionMode.setAdapter(adapter);

        spinnerExpansionMode.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        refreshCapacity();
                    }
                    public void onNothingSelected(AdapterView<?> p) {}
                });
    }

    private void setupPayloadSwitch() {
        radioPayloadType.setOnCheckedChangeListener((g, id) -> {
            boolean isText = id == R.id.radioText;

            layoutText.setVisibility(isText ? View.VISIBLE : View.GONE);
            layoutFile.setVisibility(isText ? View.GONE : View.VISIBLE);

            if (isText) {
                payloadUri = null;
                tvFilePayloadInfo.setText("No file selected");
            }

            refreshCapacity();
            validateReady();
        });
    }

    private void setupPickers() {
        carrierPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        carrierUri = r.getData().getData();
                        expansionCache.clear();
                        executor.execute(this::loadCarrier);
                        validateReady();
                    }
                });

        payloadPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        payloadUri = r.getData().getData();
                        tvFilePayloadInfo.setText(fileName(payloadUri));
                        refreshCapacity();
                        validateReady();
                    }
                });
    }

    private void setupValidation() {
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                refreshCapacity();
                validateReady();
            }
        };
        etTextMessage.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
    }

    private void validateReady() {
        boolean ready =
                carrierUri != null &&
                        (radioPayloadType.getCheckedRadioButtonId() == R.id.radioText
                                ? !etTextMessage.getText().toString().trim().isEmpty()
                                : payloadUri != null);

        btnEmbed.setEnabled(ready);
        btnEmbed.setAlpha(ready ? 1f : 0.5f);
    }

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

    private void refreshCapacity() {
        if (carrierBitmap == null) return;

        int mode = spinnerExpansionMode.getSelectedItemPosition();

        executor.execute(() -> {
            Bitmap bmp;

            synchronized (expansionCache) {
                bmp = expansionCache.get(mode);
            }

            if (bmp == null) {
                int factor = mapModeToFactor(mode);

                bmp = (factor == 0)
                        ? carrierBitmap
                        : ContentAwareExpander.expand(carrierBitmap, factor);

                bmp = sanitize(bmp);

                synchronized (expansionCache) {
                    expansionCache.put(mode, bmp);
                }
            }

            int max = StegEngineCore.getMaxPayloadSize(bmp);

            int current = radioPayloadType.getCheckedRadioButtonId() == R.id.radioText
                    ? etTextMessage.getText().toString().getBytes(StandardCharsets.UTF_8).length
                    : payloadUri == null ? 0 : getFileSize(payloadUri);

            final Bitmap finalBmp = bmp;
            final int finalCurrent = current;

            runOnUiThread(() -> {
                carrierPreview.setImageBitmap(finalBmp);

                tvCarrierInfo.setText(
                        "Payload: " + Utils.formatSize(finalCurrent) +
                                "\nCapacity: " + Utils.formatSize(max)
                );

                tvCarrierInfo.setTextColor(
                        getColor(finalCurrent > max ? R.color.red : R.color.green)
                );
            });
        });
    }

    private void loadCarrier() {
        try {
            String name = fileName(carrierUri).toLowerCase();

            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                carrierBitmap = sanitize(JPGtoPNG.convert(this, carrierUri));
            } else {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

                try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
                    carrierBitmap = sanitize(BitmapFactory.decodeStream(in, null, opts));
                }
            }

            refreshCapacity();

        } catch (Exception e) {
            e.printStackTrace();
            toast("Carrier load failed");
        }
    }

    private void embed() {
        progress.setVisibility(View.VISIBLE);
        btnEmbed.setEnabled(false);

        executor.execute(() -> {
            try {
                int mode = spinnerExpansionMode.getSelectedItemPosition();

                Bitmap bmp = expansionCache.get(mode);
                if (bmp == null) bmp = carrierBitmap;

                bmp = sanitize(bmp);

                byte[] payloadBytes;
                String name;

                if (radioPayloadType.getCheckedRadioButtonId() == R.id.radioText) {
                    payloadBytes = etTextMessage.getText().toString()
                            .getBytes(StandardCharsets.UTF_8);
                    name = "message.txt";
                } else {
                    name = fileName(payloadUri);

                    try (InputStream in = getContentResolver().openInputStream(payloadUri);
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            baos.write(buf, 0, n);
                        }

                        payloadBytes = baos.toByteArray();
                    }
                }

                int max = StegEngineCore.getMaxPayloadSize(bmp);

                if (payloadBytes.length > max) {
                    int finalMax = max;
                    runOnUiThread(() -> new AlertDialog.Builder(this)
                            .setTitle("Too Large")
                            .setMessage(
                                    "Payload: " + Utils.formatSize(payloadBytes.length) +
                                            "\nCapacity: " + Utils.formatSize(finalMax)
                            )
                            .setPositiveButton("OK", null)
                            .show());
                    return;
                }

                File out = Utils.getTimestampedFile(this, "stego.png", Utils.DIR_EMBEDDED);

                try (FileOutputStream fos = new FileOutputStream(out)) {
                    StegEngineCore.embed(
                            bmp,
                            payloadBytes,
                            name,
                            etPassword.getText().toString(),
                            fos
                    );
                }

                runOnUiThread(() -> toast("Embedded successfully"));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("Hiding failed: " + e.getMessage()));
            } finally {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    btnEmbed.setEnabled(true);
                });
            }
        });
    }

    private int mapModeToFactor(int i) {
        return i == 1 ? 1 : i == 2 ? 2 : i == 3 ? 4 : 0;
    }

    private int getFileSize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) return (int) c.getLong(idx);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String fileName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return "file";
    }

    private void pick(ActivityResultLauncher<Intent> l, String type) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        l.launch(i);
    }

    // ✅ ONLY CHANGE: using Toaster
    private void toast(String s) {
        runOnUiThread(() ->
                Toaster.show(this, s)
        );
    }
}