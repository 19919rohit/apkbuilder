package neunix.pagevibe;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class LicenseActivity extends AppCompatActivity {

    private RecyclerView licenseList;
    private final List<LicenseItem> licenses = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);

        // Bind Back Button
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Setup List
        licenseList = findViewById(R.id.licenseList);
        licenseList.setLayoutManager(new LinearLayoutManager(this));

        loadLicensesFromAssets();

        LicenseAdapter adapter = new LicenseAdapter(this, licenses, item -> showLicenseDialog(item));
        licenseList.setAdapter(adapter);
    }

    private void loadLicensesFromAssets() {
        try {
            // Looks in src/main/assets/liscenes/
            String[] files = getAssets().list("licenses");
            if (files == null) return;

            for (String filename : files) {
                if (filename.endsWith(".txt")) {
                    parseLicenseFile("licenses/" + filename, filename);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load licenses", Toast.LENGTH_SHORT).show();
        }
    }

    private void parseLicenseFile(String path, String originalFilename) {
        StringBuilder fullContent = new StringBuilder();
        String title = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(path)))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    if (line.trim().startsWith("TITLE:")) {
                        title = line.replace("TITLE:", "").trim();
                        continue; // Skip printing the raw TITLE line in the dialog body
                    }
                }
                fullContent.append(line).append("\n");
            }

            // Fallback to filename if TITLE: marker doesn't exist
            if (title == null || title.isEmpty()) {
                title = originalFilename.replace(".txt", "");
            }

            licenses.add(new LicenseItem(title, fullContent.toString().trim()));

        } catch (Exception ignored) {}
    }

    private void showLicenseDialog(LicenseItem item) {
    TextView messageView = new TextView(this);
    messageView.setText(item.content);
    messageView.setTextColor(Color.parseColor("#A0A0A0")); 
    messageView.setTextSize(13f);
    messageView.setLineSpacing(0f, 1.3f);
    messageView.setPadding(56, 32, 56, 32);

    android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
    scrollView.addView(messageView);

    AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(item.title)
            .setView(scrollView)
            .setPositiveButton("Close", (d, which) -> d.dismiss())
            .create();

    // ════════════════════════════════════════════════════════════════════
    // SAFETY FIX: Run layout styling modifications inside the active show listener
    // ════════════════════════════════════════════════════════════════════
    dialog.setOnShowListener(dialogInterface -> {
        android.widget.Button closeButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (closeButton != null) {
            closeButton.setTextColor(Color.WHITE); // Deep contrast pure white
            closeButton.setTextSize(14f);           // Optional: matches lists for clean uniformity
        }
    });

    dialog.show();
}


    // Model Definition
    private static class LicenseItem {
        String title;
        String content;

        LicenseItem(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    private interface OnItemClickListener {
        void onItemClick(LicenseItem item);
    }

    // Recycler Adapter Component
    private static class LicenseAdapter extends RecyclerView.Adapter<LicenseAdapter.VH> {
        private final Context context;
        private final List<LicenseItem> items;
        private final OnItemClickListener listener;

        LicenseAdapter(Context context, List<LicenseItem> items, OnItemClickListener listener) {
            this.context = context;
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.widget.LinearLayout card = new android.widget.LinearLayout(context);
            card.setOrientation(android.widget.LinearLayout.VERTICAL);
            card.setPadding(48, 48, 48, 48);
            
            // Reuses your theme's card component background styling
            card.setBackgroundResource(R.drawable.bg_continue_card);

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            
            // Generate clean spacing separators between listing items
            lp.setMargins(0, 0, 0, 32); 
            card.setLayoutParams(lp);

            card.setClickable(true);
            card.setFocusable(true);

            TextView tv = new TextView(context);
            tv.setTextSize(14f);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
    
            tv.setTextColor(Color.parseColor("#F0F0F0")); // Clean dark-theme contrast white text
            card.addView(tv);

            return new VH(card, tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            LicenseItem item = items.get(position);
            holder.titleText.setText(item.title);
            holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView titleText;
            VH(@NonNull View itemView, TextView titleText) {
                super(itemView);
                this.titleText = titleText;
            }
        }
    }
}
