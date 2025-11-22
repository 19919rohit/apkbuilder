package com.flapchat.app.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.flapchat.app.R;
import com.flapchat.app.model.Attachment;
import com.flapchat.app.utils.ImageLoader;
import java.util.List;

public class AttachmentPreviewAdapter extends RecyclerView.Adapter<AttachmentPreviewAdapter.AttachmentViewHolder> {

    private final List<Attachment> attachments;
    private final Context context;

    public AttachmentPreviewAdapter(Context context, List<Attachment> attachments) {
        this.context = context;
        this.attachments = attachments;
    }

    @NonNull
    @Override
    public AttachmentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attachment_preview, parent, false);
        return new AttachmentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttachmentViewHolder holder, int position) {
        Attachment attachment = attachments.get(position);
        ImageLoader.load(context, attachment.getThumbnailUrl(), holder.previewImage);
        holder.previewImage.setOnClickListener(v -> attachment.open(context));
    }

    @Override
    public int getItemCount() {
        return attachments.size();
    }

    static class AttachmentViewHolder extends RecyclerView.ViewHolder {
        ImageView previewImage;

        public AttachmentViewHolder(@NonNull View itemView) {
            super(itemView);
            previewImage = itemView.findViewById(R.id.preview_image);
        }
    }
}