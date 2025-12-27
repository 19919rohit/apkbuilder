package com.neunix.appstore;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DownloadedAppAdapter
        extends RecyclerView.Adapter<DownloadedAppAdapter.Holder> {

    public interface OnInstallClick {
        void install(DownloadedApp app);
    }

    private final List<DownloadedApp> list;
    private final OnInstallClick listener;

    public DownloadedAppAdapter(List<DownloadedApp> list, OnInstallClick listener) {
        this.list = list;
        this.listener = listener;
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_downloaded_app, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(Holder h, int pos) {
        DownloadedApp app = list.get(pos);
        h.name.setText(app.name);
        h.install.setOnClickListener(v -> listener.install(app));
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView name;
        Button install;

        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.downloadName);
            install = v.findViewById(R.id.installBtn);
        }
    }
}