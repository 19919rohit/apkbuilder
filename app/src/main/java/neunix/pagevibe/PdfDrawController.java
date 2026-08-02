package neunix.pagevibe;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageButton;

public class PdfDrawController {

    private final DrawingView drawingView;
    private final GalleryZoomView zoomHost;
    private final View toolbar;
    private final ImageButton triggerButton;
    private final ImageButton btnPen;
    private final ImageButton btnHighlighter;
    private final ThemeManager themeManager;

    private boolean active = false;
    private Runnable onActivateCallback;

    public PdfDrawController(Context context,
                             DrawingView drawingView,
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

        this.themeManager = new ThemeManager(context);
        this.drawingView = drawingView;
        this.zoomHost = zoomHost;
        this.toolbar = toolbar;
        this.triggerButton = triggerButton;
        this.btnPen = btnPen;
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

        colorRed.setOnClickListener(v ->
                drawingView.setPenColor(Color.parseColor("#FF4444")));
        colorBlue.setOnClickListener(v ->
                drawingView.setPenColor(Color.parseColor("#4488FF")));
        colorYellow.setOnClickListener(v ->
                drawingView.setPenColor(Color.parseColor("#FFEE00")));
        colorWhite.setOnClickListener(v ->
                drawingView.setPenColor(Color.WHITE));
        colorGreen.setOnClickListener(v ->
                drawingView.setPenColor(Color.parseColor("#44DD88")));

        TooltipUtil.apply(triggerButton, "Draw on page");
        TooltipUtil.apply(btnUndo, "Undo last stroke");
        TooltipUtil.apply(btnClear, "Clear all drawings on this page");
        TooltipUtil.apply(btnPen, "Pen");
        TooltipUtil.apply(btnHighlighter, "Highlighter");
        TooltipUtil.apply(penThin, "Thin");
        TooltipUtil.apply(penThick, "Thick");

        selectTool(DrawingView.Tool.PEN);

        triggerButton.setColorFilter(themeManager.getActiveTheme().textSecondaryColor);
    }

    /** Called just before this mode activates — used to deactivate any
     * mutually-exclusive mode (e.g. text selection). */
    public void setOnActivateCallback(Runnable r) {
        onActivateCallback = r;
    }

    private void selectTool(DrawingView.Tool tool) {
        drawingView.setTool(tool);

        int penColor = (tool == DrawingView.Tool.PEN)
                ? Color.WHITE
                : Color.parseColor("#666666");

        int hlColor = (tool == DrawingView.Tool.HIGHLIGHTER)
                ? Color.parseColor("#FFEE00")
                : Color.parseColor("#666666");

        btnPen.setColorFilter(penColor);
        btnHighlighter.setColorFilter(hlColor);
    }

    public void toggle() {
        setActive(!active);
    }

    public void setActive(boolean value) {
        if (value && onActivateCallback != null)
            onActivateCallback.run();

        active = value;
        drawingView.setDrawingEnabled(active);
        toolbar.setVisibility(active ? View.VISIBLE : View.GONE);

        if (active) {
            triggerButton.setColorFilter(themeManager.getActiveTheme().accentColor);
        } else {
            triggerButton.setColorFilter(themeManager.getActiveTheme().textSecondaryColor);
        }

        zoomHost.setDrawPassThrough(active);
    }

    public void deactivate() {
        if (active)
            setActive(false);
    }

    public boolean isActive() {
        return active;
    }
}