package neunix.stego;

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
import java.util.*;
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

    // 🔥 Smart toast control
    private final Map<String, Integer> toastCount = new HashMap<>();
    private final Map<String, Long> toastTime = new HashMap<>();
    private static final long TOAST_COOLDOWN = 1500;
    private static final int MAX_REPEAT = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extract);

        bind();
        setupPicker();

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
    }

    // ================= PICK =================

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

    // ================= INTERNAL =================

    private void pickInternal() {
        try {
            File dir = Utils.getBaseDir(this, Utils.DIR_EMBEDDED);

            File[] files = dir.listFiles(f ->
                    f.isFile() &&
                            (f.getName().endsWith(".png") || f.getName().endsWith(".jpg"))
            );

            if (files == null || files.length == 0) {
                toast("No Stegora images found");
                return;
            }

            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            RecyclerView rv = new RecyclerView(this);
            rv.setLayoutManager(new LinearLayoutManager(this));

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Stegora Images")
                    .setView(rv)
                    .create();

            rv.setAdapter(new InternalAdapter(files, file -> {
                carrierUri = Uri.fromFile(file);
                executor.execute(this::loadImage);
                dialog.dismiss();
            }));

            dialog.show();

        } catch (Exception e) {
            toast("Failed to load files");
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

    // ================= LOAD =================

    private void loadImage() {
        try {
            try (InputStream in = getContentResolver().openInputStream(carrierUri)) {
                carrierBitmap = sanitize(BitmapFactory.decodeStream(in));
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
            toast("Failed to load image");
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

    // ================= EXTRACT =================

    private void extract() {

        if (carrierBitmap == null) {
            toast("Select image first");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnExtract.setEnabled(false);

        executor.execute(() -> {
            try {

                String password = etPassword.getText().toString();
                if (password == null) password = "";

                StegEngineCore.ExtractedData data =
                        StegEngineCore.extract(carrierBitmap, password);

                File outFile = Utils.getTimestampedFile(
                        this,
                        data.filename,
                        Utils.DIR_EXTRACTED
                );

                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    out.write(data.data);
                }

                // ✅ Smart success message
                if (data.passwordProtected) {
                    toast("Extraction successful");
                } else {
                    if (!password.isEmpty()) {
                        toast("Extraction successful (password not required)");
                    } else {
                        toast("Extraction successful");
                    }
                }

            } catch (RuntimeException e) {

                String code = e.getMessage();

                switch (code) {

                    case "NOT_STEGO":
                        toast("Not a Stegora image");
                        break;

                    case "PASSWORD_REQUIRED":
                        toast("This image is password protected");
                        break;

                    case "WRONG_PASSWORD":
                        toast("Wrong password");
                        break;

                    case "CORRUPTED":
                        toast("Image modified or corrupted");
                        break;

                    default:
                        toast("Extraction failed");
                        break;
                }

            } catch (Exception e) {
                toast("Extraction failed");

            } finally {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnExtract.setEnabled(true);
                });
            }
        });
    }

    // ================= UTILS =================

    private String getName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {}
        return "file";
    }

    // ================= SMART TOAST =================

    private void toast(String msg) {

        long now = System.currentTimeMillis();

        int count = toastCount.getOrDefault(msg, 0);
        long lastTime = toastTime.getOrDefault(msg, 0L);

        if (now - lastTime > TOAST_COOLDOWN) {
            count = 0;
        }

        if (count >= MAX_REPEAT) return;

        toastCount.put(msg, count + 1);
        toastTime.put(msg, now);

        runOnUiThread(() ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        );
    }
}