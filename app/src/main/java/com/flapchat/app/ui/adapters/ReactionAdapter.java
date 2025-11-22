package com.flapchat.app.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.flapchat.app.R;
import com.flapchat.app.model.Reaction;
import java.util.List;

public class ReactionAdapter extends RecyclerView.Adapter<ReactionAdapter.ReactionViewHolder> {

    private final List<Reaction> reactions;
    private final Context context;

    public ReactionAdapter(Context context, List<Reaction> reactions) {
        this.context = context;
        this.reactions = reactions;
    }

    @NonNull
    @Override
    public ReactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_reaction, parent, false);
        return new ReactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReactionViewHolder holder, int position) {
        Reaction reaction = reactions.get(position);
        holder.reactionIcon.setImageResource(reaction.getIconRes());
    }

    @Override
    public int getItemCount() {
        return reactions.size();
    }

    static class ReactionViewHolder extends RecyclerView.ViewHolder {
        ImageView reactionIcon;

        public ReactionViewHolder(@NonNull View itemView) {
            super(itemView);
            reactionIcon = itemView.findViewById(R.id.reaction_icon);
        }
    }
}