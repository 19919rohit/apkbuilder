package com.flapchat.app.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.flapchat.app.R;
import com.flapchat.app.model.Message;
import com.flapchat.app.utils.ImageLoader;
import com.flapchat.app.ui.activities.ViewImageActivity;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private final List<Message> messages;
    private final Context context;

    public ChatAdapter(Context context, List<Message> messages) {
        this.context = context;
        this.messages = messages;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);

        // Text messages
        if (!message.isCode() && !message.isImage()) {
            holder.textMessage.setVisibility(View.VISIBLE);
            holder.codeMessage.setVisibility(View.GONE);
            holder.imageMessage.setVisibility(View.GONE);
            holder.textMessage.setText(message.getContent());
        }

        // Code block
        if (message.isCode()) {
            holder.textMessage.setVisibility(View.GONE);
            holder.codeMessage.setVisibility(View.VISIBLE);
            holder.imageMessage.setVisibility(View.GONE);
            holder.codeMessage.setTypeface(Typeface.MONOSPACE);
            holder.codeMessage.setText(message.getContent());
            holder.codeMessage.setMovementMethod(new ScrollingMovementMethod());
        }

        // Image message
        if (message.isImage()) {
            holder.textMessage.setVisibility(View.GONE);
            holder.codeMessage.setVisibility(View.GONE);
            holder.imageMessage.setVisibility(View.VISIBLE);
            ImageLoader.load(context, message.getContent(), holder.imageMessage);
            holder.imageMessage.setOnClickListener(v -> {
                Intent intent = new Intent(context, ViewImageActivity.class);
                intent.putExtra("IMAGE_URL", message.getContent());
                context.startActivity(intent);
            });
        }

        // TODO: Add message status, reactions, swipe gestures, timestamps, online/offline indicators
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        TextView codeMessage;
        ImageView imageMessage;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message);
            codeMessage = itemView.findViewById(R.id.code_message);
            imageMessage = itemView.findViewById(R.id.image_message);
        }
    }
}