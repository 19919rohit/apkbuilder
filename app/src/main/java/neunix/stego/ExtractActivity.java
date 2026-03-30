package neunix.stego;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.AEADBadTagException;

public class ExtractActivity extends AppCompatActivity {

    private Uri carrierUri;
    private Bitmap carrierBitmap;

    private ImageView carrierPreview;
    private TextView tvCarrierInfo;
    private EditText etPassword;
    private ProgressBar progressBar;
    private Button btnExtract;

    // 🔥 NEW UI
    private LinearLayout textContainer;
    private TextView tvExtractedText;
    private ImageView btnCopy;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<Intent> picker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extract);

        bind();
        setupPicker();
        setupCopy();

        findViewById(R.id.pickCarrierBtn).setOnClickListener(v -> pick());
        btnExtract.setOnClickListener(v -> extract());
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    private void bind() {
        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        etPassword = findViewById(R.id.etPassword);
        progressBar = findViewById(R.id.progressBar);
        btnExtract = findViewById(R.id.btnExtract);

        // NEW
        textContainer = findViewById(R.id.textContainer);
        tvExtractedText = findViewById(R.id.tvExtractedText);
        btnCopy = findViewById(R.id.btnCopy);
    }

    private void setupCopy() {
        btnCopy.setOnClickListener(v -> {
            String text = tvExtractedText.getText().toString();

            if (text.isEmpty() || text.equals("No text extracted")) {
                Toaster.show(this, "Nothing to copy");
                return;
            }

            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            ClipData clip = ClipData.newPlainText("Stegora Text", text);
            clipboard.setPrimaryClip(clip);

            Toaster.show(this, "Copied to clipboard");
        });
    }

    private void pick() {
        String[] options = {"Stegora Images", "Gallery / Other Apps"};

        new AlertDialog.Builder(this)
                .setTitle("Select Image Source")
                .setItems(options, (d, which) -> {
                    if (which == 0) pickInternal();
                    else pickExternal();
                })
                .show();
    }

    private void pickExternal() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        picker.launch(i);
    }

    private void pickInternal() {
        try {
            File dir = Utils.getBaseDir(this, Utils.DIR_EMBEDDED);
            File[] files = dir.listFiles(f ->
                    f.isFile() &&
                            (f.getName().endsWith(".png") || f.getName().endsWith(".jpg"))
            );

            if (files == null || files.length == 0) {
                Toaster.show(this, "No embedded images found");
                return;
            }

            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            RecyclerView rv = new RecyclerView(this);
            rv.setLayoutManager(new LinearLayoutManager(this));

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Embedded Images")
                    .setView(rv)
                    .create();

            rv.setAdapter(new InternalAdapter(files, file -> {
                carrierUri = Uri.fromFile(file);
                executor.execute(this::loadImage);
                dialog.dismiss();
            }));

            dialog.show();

        } catch (Exception e) {
            Toaster.show(this, "Failed to load files");
        }
    }

    private static class InternalAdapter extends RecyclerView.Adapter<InternalAdapter.VH> {

        interface ClickListener { void onClick(File file); }

        private final File[] files;
        private final ClickListener listener;

        InternalAdapter(File[] files, ClickListener l) {
            this.files = files;
            this.listener = l;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_extract_img_file, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            File file = files[pos];
            h.name.setText(file.getName());

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 8;

            h.preview.setImageBitmap(
                    BitmapFactory.decodeFile(file.getAbsolutePath(), opts)
            );

            h.itemView.setOnClickListener(v -> listener.onClick(file));
        }

        @Override
        public int getItemCount() {
            return files.length;
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView preview;
            TextView name;

            VH(View v) {
                super(v);
                preview = v.findViewById(R.id.imagePreview);
                name = v.findViewById(R.id.tvFileName);
            }
        }
    }

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

    private void loadImage() {
        try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            carrierBitmap = sanitize(bmp);

            runOnUiThread(() -> {
                carrierPreview.setImageBitmap(carrierBitmap);
                tvCarrierInfo.setText(
                        "Resolution: " +
                                carrierBitmap.getWidth() + " x " +
                                carrierBitmap.getHeight()
                );
            });

        } catch (Exception e) {
            Toaster.show(this, "Failed to load image");
        }
    }

    private Bitmap sanitize(Bitmap input) {
        if (input == null) throw new RuntimeException("Bitmap null");
        if (input.getConfig() != Bitmap.Config.ARGB_8888)
            input = input.copy(Bitmap.Config.ARGB_8888, true);
        if (!input.isMutable())
            input = input.copy(Bitmap.Config.ARGB_8888, true);
        return input;
    }

    private void extract() {

        if (carrierBitmap == null) {
            Toaster.show(this, "Select an image first");
            return;
        }

        final String password = etPassword.getText().toString();
        final Bitmap bmpFinal = carrierBitmap;

        progressBar.setVisibility(View.VISIBLE);
        btnExtract.setEnabled(false);

        executor.execute(() -> {
            try {

                final StegEngineCore.ExtractedData data =
                        StegEngineCore.extract(bmpFinal, password);

                runOnUiThread(() -> {

                    boolean isText = data.filename.toLowerCase().startsWith("message");

                    textContainer.setVisibility(View.VISIBLE);

                    if (isText) {
                        String text = new String(data.data, StandardCharsets.UTF_8);
                        tvExtractedText.setText(text);
                        Toaster.show(this, "Message extracted successfully");
                    } else {
                        tvExtractedText.setText(
                                "This payload is not text.\n\nSaved to files. Check Extracted tab."
                        );
                        Toaster.show(this, "File extracted successfully");
                    }

                    try {
                        File outFile = Utils.getTimestampedFile(
                                this,
                                data.filename,
                                Utils.DIR_EXTRACTED
                        );

                        try (FileOutputStream out = new FileOutputStream(outFile)) {
                            out.write(data.data);
                        }

                    } catch (Exception e) {
                        Toaster.show(this, "Saved but file write failed");
                    }
                });

            } catch (RuntimeException e) {

                runOnUiThread(() -> {
                    Throwable cause = e.getCause();
                    String msg = e.getMessage() == null ? "" : e.getMessage();

                    if ("NOT_STEGO".equals(msg)) {
                        Toaster.show(this, "No hidden data found");
                    } else if ("PASSWORD_REQUIRED".equals(msg)) {
                        Toaster.show(this, "Password required");
                    } else if ("WRONG_PASSWORD".equals(msg) || (cause instanceof AEADBadTagException)) {
                        Toaster.show(this, "Incorrect password");
                    } else if ("CORRUPTED".equals(msg)) {
                        Toaster.show(this, "Data corrupted or modified");
                    } else {
                        Toaster.show(this, "Extraction failed");
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toaster.show(this, "Extraction failed"));

            } finally {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnExtract.setEnabled(true);
                });
            }
        });
    }

    private String getName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {}
        return "file";
    }
}