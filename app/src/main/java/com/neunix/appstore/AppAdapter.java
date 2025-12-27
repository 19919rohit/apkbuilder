package com.neunix.appstore;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private final List<AppModel> apps;
    private final OnAppClickListener listener;

    public interface OnAppClickListener {
        void onAppClick(AppModel app);
    }

    public AppAdapter(List<AppModel> apps, OnAppClickListener listener) {
        this.apps = apps;
        this.listener = listener;
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
        holder.version.setText("v" + app.version);
        holder.desc.setText(app.description);
        holder.category.setText(app.category);

        // Optional status & size
        if (holder.status != null) {
            holder.status.setVisibility(View.GONE); // or set status if available
        }
        if (holder.size != null) {
            holder.size.setVisibility(View.GONE); // or set size if available
        }

        Picasso.get()
                .load(app.icon)
                .placeholder(R.drawable.ic_launcher)
                .error(R.drawable.ic_launcher)
                .into(holder.icon);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAppClick(app);
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
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
            status = itemView.findViewById(R.id.app_status); // optional
            size = itemView.findViewById(R.id.app_size);     // optional
        }
    }
}