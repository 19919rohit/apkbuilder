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

        drawingView.attachZoomHost(zoomHost);

        triggerButton.setOnClickListener(v -> toggle());

        btnUndo.setOnClickListener(v -> drawingView.undoLastStroke());
        btnClear.setOnClickListener(v -> {
            drawingView.clearAll();
            onClearCurrentPage.run();
        });

        btnPen.setOnClickListener(v -> selectTool(DrawingView.Tool.PEN));
        btnHighlighter.setOnClickListener(v -> selectTool(DrawingView.Tool.HIGHLIGHTER));

        penThin.setOnClickListener(v -> drawingView.setThinWidth());
        penThick.setOnClickListener(v -> drawingView.setThickWidth());

        colorRed.setOnClickListener(v    -> drawingView.setPenColor(Color.parseColor("#FF4444")));
        colorBlue.setOnClickListener(v   -> drawingView.setPenColor(Color.parseColor("#4488FF")));
        colorYellow.setOnClickListener(v -> drawingView.setPenColor(Color.parseColor("#FFEE00")));
        colorWhite.setOnClickListener(v  -> drawingView.setPenColor(Color.WHITE));
        colorGreen.setOnClickListener(v  -> drawingView.setPenColor(Color.parseColor("#44DD88")));

        TooltipUtil.apply(triggerButton,  "Draw on page");
        TooltipUtil.apply(btnUndo,        "Undo last stroke");
        TooltipUtil.apply(btnClear,       "Clear all drawings on this page");
        TooltipUtil.apply(btnPen,         "Pen");
        TooltipUtil.apply(btnHighlighter, "Highlighter");
        TooltipUtil.apply(penThin,        "Thin");
        TooltipUtil.apply(penThick,       "Thick");

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

        if (active) {
            triggerButton.setColorFilter(Color.parseColor("#4488FF"));
        } else {
            // FIXED: previously re-applied a dim grey (#555555) tint
            // here, which — since the button's default XML tint was
            // ALSO #555555 — never visually differed from a permanently
            // "used up"/disabled look. Clearing the filter restores the
            // button's real default appearance (now white, see
            // activity_pdf.xml) instead of stacking another grey tint
            // on top of it.
            triggerButton.clearColorFilter();
        }

        zoomHost.setDrawPassThrough(active);
    }

    public boolean isActive() { return active; }
}