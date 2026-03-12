package neunix.stego;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExtractActivity extends AppCompatActivity {

    // ===== URI =====
    private Uri carrierUri;

    // ===== Bitmap =====
    private Bitmap carrierBitmap;

    // ===== UI =====
    private ImageView carrierPreview;
    private TextView tvCarrierInfo;
    private EditText etPassword;
    private ProgressBar progressBar;
    private Button btnExtract;

    // ===== Executor =====
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ===== Picker =====
    private ActivityResultLauncher<Intent> externalPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extract);

        bindViews();
        setupPickers();

        findViewById(R.id.pickCarrierBtn)
                .setOnClickListener(v -> showPickerDialog());

        btnExtract.setOnClickListener(v -> extract());

        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());
    }

    // ================= BIND VIEWS =================
    private void bindViews() {

        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);

        etPassword = findViewById(R.id.etPassword);

        progressBar = findViewById(R.id.progressBar);
        btnExtract = findViewById(R.id.btnExtract);
    }

    // ================= PICKERS =================
    private void setupPickers() {

        externalPicker =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {

                            if (result.getResultCode() == RESULT_OK &&
                                    result.getData() != null) {

                                carrierUri = result.getData().getData();

                                new Thread(this::loadCarrier).start();
                            }
                        });
    }

    // ================= PICKER DIALOG =================
    private void showPickerDialog() {

        String[] options = {"App Files", "Other Apps / Gallery"};

        new AlertDialog.Builder(this)
                .setTitle("Select Stego Image")
                .setItems(options, (dialog, which) -> {

                    if (which == 0) {

                        pickInternalFile("Embedded", file -> {

                            carrierUri = Uri.fromFile(file);

                            new Thread(this::loadCarrier).start();
                        });

                    } else {

                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("image/*");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        externalPicker.launch(i);
                    }

                })
                .show();
    }

    // ================= INTERNAL FILE PICKER =================
    private void pickInternalFile(String subFolder, InternalFileCallback callback) {

        try {

            File dir = Utils.getBaseDir(subFolder);

            File[] files = dir.listFiles(f ->
                    f.isFile() &&
                            (f.getName().endsWith(".png") ||
                                    f.getName().endsWith(".jpg")));

            if (files == null || files.length == 0) {

                toast("No internal files found");
                return;
            }

            String[] names = new String[files.length];

            for (int i = 0; i < files.length; i++)
                names[i] = files[i].getName();

            new AlertDialog.Builder(this)
                    .setTitle("Select File")
                    .setItems(names, (dialog, which) ->
                            callback.onFileSelected(files[which]))
                    .show();

        } catch (Exception e) {

            toast("Error accessing internal files");
        }
    }

    private interface InternalFileCallback {
        void onFileSelected(File file);
    }

    // ================= LOAD IMAGE =================
    private void loadCarrier() {

        try {

            carrierBitmap = JPGtoPNG.convert(this, carrierUri);

            runOnUiThread(() -> {

                carrierPreview.setImageBitmap(carrierBitmap);

                tvCarrierInfo.setText(
                        "Resolution: " +
                                carrierBitmap.getWidth() +
                                " x " +
                                carrierBitmap.getHeight()
                );
            });

        } catch (Exception e) {

            toast("Image load error");
        }
    }

    // ================= EXTRACTION =================
    private void extract() {

        if (carrierBitmap == null) {

            toast("Select stego image first");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnExtract.setEnabled(false);

        executor.submit(() -> {

            try {

                StegEngineCore.ExtractedPayload payload =
                        StegEngineCore.extract(
                                carrierBitmap,
                                etPassword.getText().toString()
                        );

                File outFile = Utils.getTimestampedFile(
                        this,
                        payload.fileName,
                        "Extracted"
                );

                try (FileOutputStream out = new FileOutputStream(outFile)) {

                    out.write(payload.data);
                }

                runOnUiThread(() ->
                        toast("Extracted and saved to Files"));

            } catch (Exception e) {

                runOnUiThread(() ->
                        toast("Extraction failed"));
            } finally {

                runOnUiThread(() -> {

                    progressBar.setVisibility(View.GONE);
                    btnExtract.setEnabled(true);

                });
            }

        });
    }

    // ================= HELPERS =================
    private void toast(String s) {

        runOnUiThread(() ->
                Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
}