package com.neunix.appstore;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SearchView searchView;
    private BottomNavigationView bottomNav;
    private AppAdapter adapter;

    private final List<AppModel> allApps = new ArrayList<>();
    private final List<AppModel> visibleApps = new ArrayList<>();

    private static final String APPS_JSON_URL =
            "https://raw.githubusercontent.com/19919rohit/Neunix-Store/main/apps.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecycler();
        setupSearch();
        setupBottomNavigation();

        loadAppsFromGithub();
    }

    /* ---------------- INIT ---------------- */

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        searchView = findViewById(R.id.searchView);
        bottomNav = findViewById(R.id.bottomNav);
    }

    private void setupRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter(visibleApps, this::openAppDetails);
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String text) {
                filterApps(text);
                return true;
            }
        });
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(this::onBottomSelected);
    }

    /* ---------------- DATA LOADING ---------------- */

    private void loadAppsFromGithub() {
        new Thread(() -> {
            try {
                URL url = new URL(APPS_JSON_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(conn.getInputStream()));

                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();

                parseApps(json.toString());

                runOnUiThread(() -> {
                    visibleApps.clear();
                    visibleApps.addAll(allApps);
                    adapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Failed to load apps. Check internet.",
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void parseApps(String json) throws Exception {
        JSONArray array = new JSONArray(json);
        allApps.clear();

        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);

            AppModel app = new AppModel(
                    o.getString("name"),
                    o.getString("package"),
                    o.getString("version"),
                    o.getString("category"),
                    o.getString("description"),
                    o.getString("icon"),
                    o.getString("apk")
            );
            allApps.add(app);
        }
    }

    /* ---------------- FILTERING ---------------- */

    private void filterApps(String query) {
        visibleApps.clear();

        if (TextUtils.isEmpty(query)) {
            visibleApps.addAll(allApps);
        } else {
            String q = query.toLowerCase();
            for (AppModel app : allApps) {
                if (app.name.toLowerCase().contains(q)
                        || app.category.toLowerCase().contains(q)) {
                    visibleApps.add(app);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    /* ---------------- BOTTOM NAV ---------------- */

    private boolean onBottomSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        visibleApps.clear();

        if (id == R.id.nav_all) {
            visibleApps.addAll(allApps);
        }

        else if (id == R.id.nav_categories) {
            // Default category example (AI)
            for (AppModel app : allApps) {
                if ("AI".equalsIgnoreCase(app.category)) {
                    visibleApps.add(app);
                }
            }
        }

        else if (id == R.id.nav_downloads) {
            Set<String> history = DownloadManagerHelper.getHistory(this);
            for (String json : history) {
                visibleApps.add(AppModel.fromJson(json));
            }
        }

        adapter.notifyDataSetChanged();
        return true;
    }

    /* ---------------- CLICK HANDLING ---------------- */

    private void openAppDetails(AppModel app) {
        Intent intent = new Intent(this, AppDetailActivity.class);
        intent.putExtra("app", app);
        startActivity(intent);
    }
}