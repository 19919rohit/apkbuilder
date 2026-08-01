package neunix.pagevibe;

import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class NotesActivity extends AppCompatActivity {

    public static final String EXTRA_PDF_URI  = "extra_pdf_uri";
    public static final String EXTRA_PDF_NAME = "extra_pdf_name";

    private static final String[] COLOR_HEX = { "#FFEE00", "#44DD88", "#4488FF", "#FF6EC7", "#FF9944" };

    private PdfNotesManager notesManager;
    private Uri pdfUri;
    private final List<PdfNotesManager.NoteEntry> entries = new ArrayList<>();
    private NotesAdapter adapter;
    private View emptyState;
    private ThemeManager themeManager;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);
        rootView = findViewById(android.R.id.content);
        themeManager = new ThemeManager(this);

        String uriString = getIntent().getStringExtra(EXTRA_PDF_URI);
        String name = getIntent().getStringExtra(EXTRA_PDF_NAME);
        pdfUri = uriString != null ? Uri.parse(uriString) : null;
        notesManager = new PdfNotesManager(this);

        ((TextView) findViewById(R.id.notesPdfName)).setText(name != null ? name : "PDF");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddNote).setOnClickListener(v -> showEditDialog(null));

        RecyclerView recycler = findViewById(R.id.notesRecycler);
        emptyState = findViewById(R.id.notesEmptyState);
        adapter = new NotesAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        reload();
        applyTheme();
    }
    
    @Override
protected void onResume() {
    super.onResume();
    applyTheme();
}

private void applyTheme() {
    ThemeApplier.apply(rootView, themeManager.getActiveTheme());
    adapter.notifyDataSetChanged();
}

    private void reload() {
        entries.clear();
        if (pdfUri != null) {
            List<PdfNotesManager.NoteEntry> loaded = notesManager.getEntries(pdfUri);
            for (int i = loaded.size() - 1; i >= 0; i--) entries.add(loaded.get(i)); // newest first
        }
        adapter.notifyDataSetChanged();
        emptyState.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showEditDialog(PdfNotesManager.NoteEntry existing) {
        if (pdfUri == null) return;

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_edit_note, null);
        EditText input = content.findViewById(R.id.noteInput);
        if (existing != null) input.setText(existing.text);
        input.setSelection(input.getText().length());

        int[] dotIds = { R.id.noteColorYellow, R.id.noteColorGreen, R.id.noteColorBlue, R.id.noteColorPink, R.id.noteColorOrange };
        final String[] selectedColor = { existing != null ? existing.colorHex : COLOR_HEX[0] };
        for (int i = 0; i < dotIds.length; i++) {
            String hex = COLOR_HEX[i];
            content.findViewById(dotIds[i]).setOnClickListener(v -> selectedColor[0] = hex);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(existing == null ? "Add note" : "Edit note")
                .setView(content)
                .setPositiveButton("Save", (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    if (existing == null) notesManager.addEntry(pdfUri, text, selectedColor[0]);
                    else notesManager.updateEntry(pdfUri, existing.id, text, selectedColor[0]);
                    reload();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.whitenButtons(dialog);
        dialog.show();
    }

    private void confirmDelete(PdfNotesManager.NoteEntry entry) {
        AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Delete note?")
                .setMessage("This note will be permanently removed.")
                .setPositiveButton("Delete", (d, w) -> { notesManager.deleteEntry(pdfUri, entry.id); reload(); })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.applyDestructiveConfirm(dialog);
        dialog.show();
    }

    private class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            PdfNotesManager.NoteEntry entry = entries.get(pos);
            ThemeApplier.applyToSingleView(
        h.itemView,
        themeManager.getActiveTheme()
);
            h.text.setText(entry.text);
            h.time.setText(DateFormat.format("MMM d, yyyy · h:mm a", entry.updatedAt));
            try { h.colorDot.setBackgroundColor(Color.parseColor(entry.colorHex)); } catch (Throwable ignored) {}

            h.itemView.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION) showEditDialog(entries.get(p));
            });
            h.delete.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p != RecyclerView.NO_POSITION) confirmDelete(entries.get(p));
            });
        }

        @Override public int getItemCount() { return entries.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView text, time;
            View colorDot, delete;
            VH(View v) {
                super(v);
                text     = v.findViewById(R.id.noteCardText);
                time     = v.findViewById(R.id.noteCardTime);
                colorDot = v.findViewById(R.id.noteCardColorDot);
                delete   = v.findViewById(R.id.noteCardDelete);
            }
        }
    }
}