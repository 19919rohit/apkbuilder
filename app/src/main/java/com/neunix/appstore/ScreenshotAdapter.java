package com.neunix.appstore;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;

import java.util.List;

public class ScreenshotAdapter
        extends RecyclerView.Adapter<ScreenshotAdapter.Holder> {

    private final List<String> screenshots;

    public ScreenshotAdapter(List<String> screenshots) {
        this.screenshots = screenshots;
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup p, int v) {
        View view = LayoutInflater.from(p.getContext())
                .inflate(R.layout.item_screenshot, p, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(Holder h, int pos) {
        Picasso.get().load(screenshots.get(pos)).into(h.image);
    }

    @Override
    public int getItemCount() { return screenshots.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView image;
        Holder(View v) {
            super(v);
            image = v.findViewById(R.id.screenshotImage);
        }
    }
}