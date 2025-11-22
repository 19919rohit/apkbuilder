package com.flapchat.app.ui.components;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.flapchat.app.R;
import com.flapchat.app.model.Reaction;
import com.flapchat.app.ui.adapters.ReactionAdapter;
import java.util.List;

public class ReactionPopup {

    private final Context context;
    private final PopupWindow popupWindow;

    public ReactionPopup(Context context, List<Reaction> reactions) {
        this.context = context;

        View contentView = LayoutInflater.from(context).inflate(R.layout.view_reaction_popup, null);
        RecyclerView recyclerView = contentView.findViewById(R.id.reactions_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setAdapter(new ReactionAdapter(context, reactions));

        popupWindow = new PopupWindow(contentView,
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT, true);
    }

    public void show(View anchor) {
        popupWindow.showAsDropDown(anchor, 0, -anchor.getHeight(), Gravity.TOP);
    }

    public void dismiss() {
        popupWindow.dismiss();
    }
}