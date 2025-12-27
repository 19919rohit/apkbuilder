package com.neunix.appstore;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private final List<AppModel> apps;
    private final OnAppClickListener listener;
    private final Context context;

    public interface OnAppClickListener {
        void onAppClick(AppModel app);
    }

    public AppAdapter(Context context, List<AppModel> apps, OnAppClickListener listener) {
        this.apps = apps;
        this.listener = listener;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppModel app = apps.get(position);

        holder.name.setText(app.name);
        holder.category.setText(app.category);
        holder.desc.setText(app.description);
        holder.version.setText("v" + app.version);
        holder.size.setText(app.size != null ? app.size : "—");

        // Load icon with placeholder & error fallback
        Picasso.get()
                .load(app.icon)
                .placeholder(R.drawable.ic_launcher)
                .error(R.drawable.ic_launcher)
                .into(holder.icon);

        // Determine app status
        String status = getAppStatus(app);
        holder.status.setText(status);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAppClick(app);
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    private String getAppStatus(AppModel app) {
        try {
            PackageManager pm = context.getPackageManager();
            int installedVersion;
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    installedVersion = (int) pm.getPackageInfo(app.packageName, 0).getLongVersionCode();
                } else {
                    installedVersion = pm.getPackageInfo(app.packageName, 0).versionCode;
                }
            } catch (PackageManager.NameNotFoundException e) {
                installedVersion = -1;
            }

            if (installedVersion == -1) return "Not Installed";
            if (installedVersion < app.versionCode) return "Update Available";
            return "Installed";

        } catch (Exception e) {
            return "Unknown";
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView icon;
        TextView name, version, desc, category, status, size;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            icon = itemView.findViewById(R.id.app_icon);
            name = itemView.findViewById(R.id.app_name);
            version = itemView.findViewById(R.id.app_version);
            desc = itemView.findViewById(R.id.app_desc);
            category = itemView.findViewById(R.id.app_category);
            status = itemView.findViewById(R.id.app_status); // optional TextView in layout
            size = itemView.findViewById(R.id.app_size); // optional TextView in layout
        }
    }
}