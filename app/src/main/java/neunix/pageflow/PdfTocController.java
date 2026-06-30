package neunix.pageflow;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PdfTocController {

    public interface OnEntrySelected { void onSelected(int page); }

    private final PdfReaderController reader;
    private final View sheetBackdrop, sheet;
    private final ImageButton btnClose, triggerButton;
    private final RecyclerView recycler;
    private final OnEntrySelected onSelected;
    private final TocAdapter adapter;
    private final List<PdfTextExtractor.TocEntry> entries = new ArrayList<>();

    public PdfTocController(Context context, PdfReaderController reader,
                             View sheetBackdrop, View sheet, ImageButton btnClose,
                             ImageButton triggerButton, RecyclerView recycler,
                             OnEntrySelected onSelected) {
        this.reader = reader;
        this.sheetBackdrop = sheetBackdrop;
        this.sheet = sheet;
        this.btnClose = btnClose;
        this.triggerButton = triggerButton;
        this.recycler = recycler;
        this.onSelected = onSelected;

        adapter = new TocAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);

        triggerButton.setOnClickListener(v -> show());
        btnClose.setOnClickListener(v -> hide());
        sheetBackdrop.setOnClickListener(v -> hide());

        TooltipUtil.apply(triggerButton, "Table of contents");
        TooltipUtil.apply(btnClose, "Close contents");
    }

    /** Load real PDF outline if present; otherwise build evenly-spaced page milestones. */
    public void buildFor(List<PdfTextExtractor.TocEntry> realOutline, int totalPages) {
        entries.clear();
        if (realOutline != null && !realOutline.isEmpty()) {
            entries.addAll(realOutline);
        } else {
            int step = totalPages <= 20 ? 1 : totalPages / 20;
            for (int i = 0; i < totalPages; i += step) {
                entries.add(new PdfTextExtractor.TocEntry("Page " + (i + 1), i, 0));
            }
            if (totalPages > 1) {
                int last = totalPages - 1;
                boolean already = false;
                for (PdfTextExtractor.TocEntry e : entries) if (e.page == last) { already = true; break; }
                if (!already) entries.add(new PdfTextExtractor.TocEntry("Page " + totalPages, last, 0));
            }
        }
        adapter.notifyDataSetChanged();
    }

    public void show() {
        sheetBackdrop.setVisibility(View.VISIBLE);
        sheetBackdrop.setAlpha(0f);
        sheetBackdrop.animate().alpha(1f).setDuration(180).start();
        sheet.setVisibility(View.VISIBLE);
        sheet.animate().translationY(0).setDuration(300).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    public void hide() {
        sheetBackdrop.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> sheetBackdrop.setVisibility(View.GONE)).start();
        sheet.animate().translationY(3000).setDuration(260)
                .withEndAction(() -> sheet.setVisibility(View.GONE)).start();
    }

    private class TocAdapter extends RecyclerView.Adapter<TocAdapter.VH> {
        @Override
        public VH onCreateViewHolder(ViewGroup parent, int type) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            PdfTextExtractor.TocEntry e = entries.get(pos);
            int paddingStart = 56 + (Math.max(e.depth, 0) * 28);
            h.tv.setPadding(paddingStart, 22, 40, 22);
            h.tv.setText(e.title);
            h.tv.setTextColor(e.depth == 0 ? Color.parseColor("#E8E8E8") : Color.parseColor("#888888"));
            h.tv.setTextSize(e.depth == 0 ? 14f : 13f);
            h.tv.setOnClickListener(v -> { hide(); onSelected.onSelected(e.page); });
        }

        @Override public int getItemCount() { return entries.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(TextView v) { super(v); tv = v; }
        }
    }
}