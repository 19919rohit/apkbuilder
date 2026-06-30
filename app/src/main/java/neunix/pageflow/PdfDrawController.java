package neunix.pageflow;

import android.graphics.Color;
import android.view.View;
import android.widget.ImageButton;

public class PdfDrawController {

    private final DrawingView drawingView;
    private final GalleryZoomView zoomHost;
    private final View toolbar;
    private final ImageButton triggerButton;
    private boolean active = false;

    public PdfDrawController(DrawingView drawingView, GalleryZoomView zoomHost,
                              View toolbar, ImageButton triggerButton,
                              ImageButton btnUndo, ImageButton btnClear,
                              ImageButton penThin, ImageButton penThick,
                              View colorRed, View colorBlue, View colorYellow,
                              View colorWhite, View colorGreen,
                              Runnable onClearCurrentPage) {
        this.drawingView = drawingView;
        this.zoomHost = zoomHost;
        this.toolbar = toolbar;
        this.triggerButton = triggerButton;

        // Link drawing view to the zoom container so touch coordinates are
        // converted into content space — this is what makes drawing work
        // correctly WHILE zoomed in, instead of forcing a zoom reset.
        drawingView.attachZoomHost(zoomHost);

        triggerButton.setOnClickListener(v -> toggle());
        btnUndo.setOnClickListener(v -> drawingView.undoLastStroke());
        btnClear.setOnClickListener(v -> { drawingView.clearAll(); onClearCurrentPage.run(); });

        penThin.setOnClickListener(v -> drawingView.setPenWidth(4f));
        penThick.setOnClickListener(v -> drawingView.setPenWidth(14f));

        colorRed.setOnClickListener(v -> { drawingView.setPenColor(Color.RED); drawingView.setPenWidth(6f); });
        colorBlue.setOnClickListener(v -> { drawingView.setPenColor(Color.parseColor("#4488FF")); drawingView.setPenWidth(6f); });
        colorYellow.setOnClickListener(v -> { drawingView.setPenColor(Color.parseColor("#CCFFDD00")); drawingView.setPenWidth(22f); });
        colorWhite.setOnClickListener(v -> { drawingView.setPenColor(Color.WHITE); drawingView.setPenWidth(6f); });
        colorGreen.setOnClickListener(v -> { drawingView.setPenColor(Color.parseColor("#44DD88")); drawingView.setPenWidth(6f); });

        TooltipUtil.apply(triggerButton, "Draw on page");
        TooltipUtil.apply(btnUndo, "Undo last stroke");
        TooltipUtil.apply(btnClear, "Clear all drawings on this page");
        TooltipUtil.apply(penThin, "Thin pen");
        TooltipUtil.apply(penThick, "Thick pen");
    }

    public void toggle() {
        active = !active;
        drawingView.setDrawingEnabled(active);
        toolbar.setVisibility(active ? View.VISIBLE : View.GONE);
        triggerButton.setColorFilter(active ? Color.parseColor("#4488FF") : Color.parseColor("#555555"));

        // KEY FIX: drawing now stays available at any zoom level. We tell
        // the zoom container to let single-finger touches pass straight to
        // DrawingView (so a one-finger stroke isn't swallowed as a pan
        // gesture), while still allowing two-finger pinch to zoom even
        // while in draw mode — exactly like a real annotation app.
        zoomHost.setDrawPassThrough(active);
    }

    public boolean isActive() { return active; }
}