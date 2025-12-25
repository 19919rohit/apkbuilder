package com.neunix.appstore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    List<AppItem> appList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create RecyclerView programmatically
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        setContentView(recyclerView);

        // Dummy apps
        appList = new ArrayList<>();
        appList.add(new AppItem("Neunix Chat", "v1.2", "https://via.placeholder.com/150", "Demo messaging app."));
        appList.add(new AppItem("Neunix Music", "v2.0", "https://via.placeholder.com/150", "Stream fake music tracks."));
        appList.add(new AppItem("Neunix Notes", "v0.9", "https://via.placeholder.com/150", "Take notes quickly."));

        recyclerView.setAdapter(new AppAdapter(appList));
    }

    // Model class inside MainActivity
    class AppItem {
        String name, version, imageUrl, description;
        AppItem(String name, String version, String imageUrl, String description) {
            this.name = name;
            this.version = version;
            this.imageUrl = imageUrl;
            this.description = description;
        }
    }

    // Adapter class inside MainActivity
    class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

        List<AppItem> items;
        AppAdapter(List<AppItem> items) { this.items = items; }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            AppItem app = items.get(position);
            holder.name.setText(app.name);
            holder.version.setText(app.version);
            holder.desc.setText(app.description);
            Picasso.get().load(app.imageUrl).placeholder(R.drawable.ic_launcher).into(holder.image);
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, version, desc;
            ImageView image;
            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.app_name);
                version = itemView.findViewById(R.id.app_version);
                desc = itemView.findViewById(R.id.app_desc);
                image = itemView.findViewById(R.id.app_screenshot);
            }
        }
    }
}