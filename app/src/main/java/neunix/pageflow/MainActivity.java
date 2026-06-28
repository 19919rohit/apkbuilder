package neunix.pageflow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME  = "pageflow_prefs";
    private static final String KEY_RECENT  = "recent_files";
    private static final int    MAX_RECENT  = 10;

    private ListView      recentList;
    private View          emptyState;
    private View          btnOpenPdf;

    private final List<RecentFile> recentFiles = new ArrayList<>();
    private RecentAdapter          adapter;

    private ActivityResultLauncher<Intent> pickerLauncher;

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        bindViews();
        registerPicker();
        setupRecent();
        setupButtons();

        Intent incoming = getIntent();
        if (Intent.ACTION_VIEW.equals(incoming.getAction())
                && incoming.getData() != null) {
            Uri uri  = incoming.getData();
            String name = FileUtils.getFileName(this, uri);
            addToRecent(uri, name);
            openReader(uri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecent();
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    // =========================================================
    // BIND
    // =========================================================

    private void bindViews() {
        recentList = findViewById(R.id.recentList);
        emptyState = findViewById(R.id.emptyState);
        btnOpenPdf = findViewById(R.id.btnOpenPdf);
    }

    private void setupButtons() {
        btnOpenPdf.setOnClickListener(v -> openFilePicker());
    }

    // =========================================================
    // FILE PICKER
    // =========================================================

    private void registerPicker() {
        pickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK) return;
                    Intent data = result.getData();
                    if (data == null || data.getData() == null) return;

                    Uri uri = data.getData();

                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (Exception ignored) { }

                    String name = FileUtils.getFileName(this, uri);
                    addToRecent(uri, name);
                    openReader(uri);
                }
        );
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/pdf");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
          | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        pickerLauncher.launch(i);
    }

    // =========================================================
    // READER
    // =========================================================

    private void openReader(Uri uri) {
        Intent i = new Intent(this, PdfActivity.class);
        i.setData(uri);
        startActivity(i);
    }

    // =========================================================
    // RECENT FILES LIST
    // =========================================================

    private void setupRecent() {
        loadRecent();
        adapter = new RecentAdapter();
        recentList.setAdapter(adapter);
        recentList.setOnItemClickListener((parent, view, position, id) -> {
            RecentFile file = recentFiles.get(position);
            addToRecent(file.uri, file.name);
            openReader(file.uri);
        });
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = recentFiles.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE  : View.GONE);
        recentList.setVisibility(empty ? View.GONE     : View.VISIBLE);
    }

    // =========================================================
    // RECENT PERSISTENCE
    // =========================================================

    private void loadRecent() {
        recentFiles.clear();
        try {
            SharedPreferences prefs =
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_RECENT, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                recentFiles.add(new RecentFile(
                        Uri.parse(obj.getString("uri")),
                        obj.getString("name"),
                        obj.optLong("timestamp", 0L)
                ));
            }
        } catch (Exception ignored) { }
    }

    private void saveRecent() {
        try {
            JSONArray arr = new JSONArray();
            for (RecentFile f : recentFiles) {
                JSONObject obj = new JSONObject();
                obj.put("uri",       f.uri.toString());
                obj.put("name",      f.name);
                obj.put("timestamp", f.timestamp);
                arr.put(obj);
            }
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_RECENT, arr.toString())
                    .apply();
        } catch (Exception ignored) { }
    }

    private void addToRecent(Uri uri, String name) {
        recentFiles.removeIf(f -> f.uri.equals(uri));
        recentFiles.add(0, new RecentFile(uri, name, System.currentTimeMillis()));

        if (recentFiles.size() > MAX_RECENT) {
            recentFiles.subList(MAX_RECENT, recentFiles.size()).clear();
        }

        saveRecent();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
            updateEmptyState();
        }
    }

    private void removeFromRecent(int position) {
        if (position < 0 || position >= recentFiles.size()) return;
        RecentFile removed = recentFiles.get(position);
        FileUtils.evictCacheForUri(this, removed.uri);
        recentFiles.remove(position);
        saveRecent();
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    // =========================================================
    // DATA MODEL
    // =========================================================

    private static class RecentFile {
        final Uri    uri;
        final String name;
        final long   timestamp;

        RecentFile(Uri uri, String name, long timestamp) {
            this.uri       = uri;
            this.name      = name;
            this.timestamp = timestamp;
        }

        String relativeTime() {
            long diff = System.currentTimeMillis() - timestamp;
            if (diff < 60_000L)         return "Just now";
            if (diff < 3_600_000L)      return (diff / 60_000L)     + " min ago";
            if (diff < 86_400_000L)     return (diff / 3_600_000L)  + " hr ago";
            if (diff < 7 * 86_400_000L) return (diff / 86_400_000L) + " days ago";
            return "A while ago";
        }

        String initial() {
            return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        }
    }

    // =========================================================
    // ADAPTER
    // =========================================================

    private class RecentAdapter extends BaseAdapter {

        @Override public int    getCount()         { return recentFiles.size(); }
        @Override public Object getItem(int pos)   { return recentFiles.get(pos); }
        @Override public long   getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder;

            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_recent_file, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            RecentFile file = recentFiles.get(position);
            holder.initial.setText(file.initial());
            holder.name.setText(file.name);
            holder.time.setText(file.relativeTime());
            holder.btnRemove.setOnClickListener(v -> removeFromRecent(position));

            return convertView;
        }
    }

    private static class ViewHolder {
        final TextView    initial;
        final TextView    name;
        final TextView    time;
        final ImageButton btnRemove;

        ViewHolder(View v) {
            initial   = v.findViewById(R.id.fileInitial);
            name      = v.findViewById(R.id.fileName);
            time      = v.findViewById(R.id.fileTime);
            btnRemove = v.findViewById(R.id.btnRemove);
        }
    }
}