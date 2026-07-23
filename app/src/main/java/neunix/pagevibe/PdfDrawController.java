package neunix.pagevibe;

import android.graphics.Color;
import android.view.View;
import android.widget.ImageButton;

public class PdfDrawController {

    private final DrawingView    drawingView;
    private final GalleryZoomView zoomHost;
    private final View           toolbar;
    private final ImageButton    triggerButton;
    private final ImageButton    btnPen;
    private final ImageButton    btnHighlighter;
    private boolean              active = false;

    public PdfDrawController(DrawingView drawingView,
                              GalleryZoomView zoomHost,
                              View toolbar,
                              ImageButton triggerButton,
                              ImageButton btnUndo,
                              ImageButton btnClear,
                              ImageButton btnPen,
                              ImageButton btnHighlighter,
                              ImageButton penThin,
                              ImageButton penThick,
                              View colorRed,
                              View colorBlue,
                              View colorYellow,
                              View colorWhite,
                              View colorGreen,
                              Runnable onClearCurrentPage) {

        this.drawingView    = drawingView;
        this.zoomHost       = zoomHost;
        this.toolbar        = toolbar;
        this.triggerButton  = triggerButton;
        this.btnPen         = btnPen;
        this.btnHighlighter = btnHighlighter;

        // Link drawing view to zoom container for coordinate conversion
        drawingView.attachZoomHost(zoomHost);

        // ── Main toggle ──────────────────────────────────────
        triggerButton.setOnClickListener(v -> toggle());

        // ── Undo / Clear ─────────────────────────────────────
        btnUndo.setOnClickListener(v -> drawingView.undoLastStroke());
        btnClear.setOnClickListener(v -> {
            drawingView.clearAll();
            onClearCurrentPage.run();
        });

        // ── Tool selection — ONLY changes tool + default width.
        // Color is left untouched so switching tools never surprises
        // the user by resetting a color they already picked. ────
        btnPen.setOnClickListener(v -> selectTool(DrawingView.Tool.PEN));
        btnHighlighter.setOnClickListener(v -> selectTool(DrawingView.Tool.HIGHLIGHTER));

        // ── Pen thickness — only meaningful for the Pen tool ──
        penThin.setOnClickListener(v -> drawingView.setThinWidth());
        penThick.setOnClickListener(v -> drawingView.setThickWidth());

        // ── Colors — ONLY set color. They never touch width or
        // tool, so they work identically for Pen and Highlighter
        // and never clobber a chosen pen thickness. ─────────────
        colorRed.setOnClickListener(v    -> drawingView.setPenColor(Color.parseColor("#FF4444")));
        colorBlue.setOnClickListener(v   -> drawingView.setPenColor(Color.parseColor("#4488FF")));
        colorYellow.setOnClickListener(v -> drawingView.setPenColor(Color.parseColor("#FFEE00")));
        colorWhite.setOnClickListener(v  -> drawingView.setPenColor(Color.WHITE));
        colorGreen.setOnClickListener(v  -> drawingView.setPenColor(Color.parseColor("#44DD88")));

        // ── Tooltips ─────────────────────────────────────────
        TooltipUtil.apply(triggerButton,  "Draw on page");
        TooltipUtil.apply(btnUndo,        "Undo last stroke");
        TooltipUtil.apply(btnClear,       "Clear all drawings on this page");
        TooltipUtil.apply(btnPen,         "Pen");
        TooltipUtil.apply(btnHighlighter, "Highlighter");
        TooltipUtil.apply(penThin,        "Thin");
        TooltipUtil.apply(penThick,       "Thick");

        // Reflect the initial tool (Pen) in the toolbar icon tint.
        selectTool(DrawingView.Tool.PEN);
    }

    private void selectTool(DrawingView.Tool tool) {
        drawingView.setTool(tool);

        int penColor = (tool == DrawingView.Tool.PEN)        ? Color.WHITE : Color.parseColor("#666666");
        int hlColor  = (tool == DrawingView.Tool.HIGHLIGHTER) ? Color.parseColor("#FFEE00") : Color.parseColor("#666666");
        btnPen.setColorFilter(penColor);
        btnHighlighter.setColorFilter(hlColor);
    }

    public void toggle() {
        active = !active;
        drawingView.setDrawingEnabled(active);
        toolbar.setVisibility(active ? View.VISIBLE : View.GONE);
        triggerButton.setColorFilter(
                active ? Color.parseColor("#4488FF") : Color.parseColor("#555555"));
        // Pass single-finger touches to DrawingView while drawing is active,
        // but still allow two-finger pinch to zoom.
        zoomHost.setDrawPassThrough(active);
    }

    public boolean isActive() { return active; }
}