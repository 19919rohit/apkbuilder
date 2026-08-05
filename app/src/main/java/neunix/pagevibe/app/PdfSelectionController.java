package neunix.pagevibe.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Tap-to-select, tap-again-to-extend text selection. See class-level
 * discussion in the chat response for why this is the robust choice over
 * continuous drag: a selection is always the inclusive range between two
 * WordBox IDs in the page's canonical reading order, so it is immune to
 * line-wrap breakage by construction.
 */
public class PdfSelectionController {

    private final Context context;
    private final PdfReaderController reader;
    private final PdfReadAloudController readAloud;
    private final GalleryZoomView zoomContainer;
    private final TextSelectionView selectionView;
    private final HighlightOverlayView highlightOverlay;
    private final PdfHighlightManager highlightManager;
    private final ImageButton triggerButton;

    private boolean active = false;
    private int anchorWordId = -1;
    private int focusWordId  = -1;
    private List<PdfTextExtractor.WordBox> currentPageWords = new ArrayList<>();
    private PopupWindow toolbarPopup;

    private Runnable onActivateCallback;

    public PdfSelectionController(Context context, PdfReaderController reader,
                                   PdfReadAloudController readAloud,
                                   GalleryZoomView zoomContainer,
                                   TextSelectionView selectionView,
                                   HighlightOverlayView highlightOverlay,
                                   ImageButton triggerButton) {
        this.context          = context;
        this.reader           = reader;
        this.readAloud        = readAloud;
        this.zoomContainer    = zoomContainer;
        this.selectionView    = selectionView;
        this.highlightOverlay = highlightOverlay;
        this.highlightManager = new PdfHighlightManager(context);
        this.triggerButton    = triggerButton;

        triggerButton.setOnClickListener(v -> toggle());
        selectionView.setOnWordTapListener(this::handleTap);
        TooltipUtil.apply(triggerButton, "Select text");
    }

    public void setOnActivateCallback(Runnable r) { onActivateCallback = r; }

    public void toggle() { setActive(!active); }

    public void setActive(boolean value) {
        if (value && onActivateCallback != null) onActivateCallback.run();
        active = value;
        selectionView.setSelectionEnabled(active);
        zoomContainer.setDrawPassThrough(active);
        if (active) triggerButton.setColorFilter(Color.parseColor("#4488FF"));
        else triggerButton.clearColorFilter();
        if (!active) clearSelection();
    }

    public void deactivate() { if (active) setActive(false); }
    public boolean isActive() { return active; }

    /** Selection never survives a page turn — call on every settled-page change. */
    public void onPageChanged() {
        currentPageWords = new ArrayList<>();
        clearSelection();
    }

    private void handleTap(float screenX, float screenY) {
        PointF norm = highlightOverlay.pixelToNormPoint(screenX, screenY);
        if (norm == null) return;

        int page = reader.getSettledPage();
        if (currentPageWords.isEmpty()) {
            PdfTextExtractor extractor = reader.getExtractor();
            if (extractor == null) return;
            currentPageWords = extractor.extractPageWordData(page).words;
        }

        int tappedId = hitTest(norm.x, norm.y);
        if (tappedId < 0) { clearSelection(); return; }

        if (anchorWordId < 0) { anchorWordId = tappedId; focusWordId = tappedId; }
        else { focusWordId = tappedId; }

        renderSelection();
        showToolbar();
    }

    private int hitTest(float normX, float normY) {
        for (PdfTextExtractor.WordBox wb : currentPageWords) {
            if (normX >= wb.left && normX <= wb.right && normY >= wb.top && normY <= wb.bottom) return wb.id;
        }
        // Nearest-word fallback within a small tolerance — real finger
        // taps rarely land exactly inside a tight glyph bounding box.
        float toleranceSq = 0.02f * 0.02f * 4f;
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (PdfTextExtractor.WordBox wb : currentPageWords) {
            float cx = (wb.left + wb.right) / 2f, cy = (wb.top + wb.bottom) / 2f;
            float dist = (cx - normX) * (cx - normX) + (cy - normY) * (cy - normY);
            if (dist < bestDist) { bestDist = dist; best = wb.id; }
        }
        return (best >= 0 && bestDist < toleranceSq) ? best : -1;
    }

    private List<PdfTextExtractor.WordBox> currentSelectionWords() {
        List<PdfTextExtractor.WordBox> result = new ArrayList<>();
        if (anchorWordId < 0 || focusWordId < 0) return result;
        int lo = Math.min(anchorWordId, focusWordId), hi = Math.max(anchorWordId, focusWordId);
        for (PdfTextExtractor.WordBox wb : currentPageWords) {
            if (wb.id >= lo && wb.id <= hi) result.add(wb);
        }
        return result;
    }

    private void renderSelection() { highlightOverlay.setSelectionHighlights(currentSelectionWords()); }

    private void clearSelection() {
        anchorWordId = -1; focusWordId = -1;
        highlightOverlay.clearSelectionHighlights();
        hideToolbar();
    }

    private void showToolbar() {
        hideToolbar();
        List<PdfTextExtractor.WordBox> words = currentSelectionWords();
        if (words.isEmpty()) return;

        View content = LayoutInflater.from(context).inflate(R.layout.popup_selection_toolbar, null);
        content.findViewById(R.id.btnSelHighlight).setOnClickListener(v -> showColorPicker(words));
        content.findViewById(R.id.btnSelReadAloud).setOnClickListener(v -> {
            readAloud.readCustomSelection(words);
            clearSelection();
        });
        content.findViewById(R.id.btnSelTranslate).setOnClickListener(v -> {
            translate(words);
            clearSelection();
        });
        content.findViewById(R.id.btnSelClose).setOnClickListener(v -> clearSelection());

        toolbarPopup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        toolbarPopup.setElevation(16f);
        toolbarPopup.showAtLocation(selectionView, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dpToPx(140));
    }

    private void hideToolbar() {
        if (toolbarPopup != null && toolbarPopup.isShowing()) toolbarPopup.dismiss();
        toolbarPopup = null;
    }

    private void showColorPicker(List<PdfTextExtractor.WordBox> words) {
        hideToolbar();
        View content = LayoutInflater.from(context).inflate(R.layout.popup_highlight_colors, null);
        int[] ids = { R.id.colorYellowDot, R.id.colorGreenDot, R.id.colorBlueDot, R.id.colorPinkDot, R.id.colorOrangeDot };
        String[] colors = { "#FFEE00", "#44DD88", "#4488FF", "#FF6EC7", "#FF9944" };

        PopupWindow picker = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        picker.setElevation(16f);

        for (int i = 0; i < ids.length; i++) {
            final String colorHex = colors[i];
            content.findViewById(ids[i]).setOnClickListener(v -> {
                commitHighlight(words, colorHex);
                picker.dismiss();
                clearSelection();
            });
        }
        picker.showAtLocation(selectionView, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dpToPx(140));
    }

    private void commitHighlight(List<PdfTextExtractor.WordBox> words, String colorHex) {
        if (words.isEmpty()) return;
        Uri uri = reader.getCurrentUri();
        if (uri == null) return;
        int page = reader.getSettledPage();
        int charStart = words.get(0).charStart;
        int charEnd   = words.get(words.size() - 1).charEnd;
        highlightManager.addHighlight(uri, page, charStart, charEnd, colorHex);
        refreshPersistentHighlightsForPage(page);
        Toast.makeText(context, "Highlighted", Toast.LENGTH_SHORT).show();
    }

    /** Call after opening a page and after committing a new highlight —
     *  redraws every saved highlight for that page. */
    public void refreshPersistentHighlightsForPage(int page) {
        Uri uri = reader.getCurrentUri();
        PdfTextExtractor extractor = reader.getExtractor();
        if (uri == null || extractor == null) return;

        List<PdfHighlightManager.HighlightEntry> saved = highlightManager.getHighlightsForPage(uri, page);
        List<HighlightOverlayView.PersistentHighlight> renderList = new ArrayList<>();
        if (!saved.isEmpty()) {
            List<PdfTextExtractor.WordBox> pageWords = extractor.extractPageWordData(page).words;
            for (PdfHighlightManager.HighlightEntry entry : saved) {
                int color;
                try { color = Color.parseColor(entry.colorHex); } catch (Throwable t) { continue; }
                for (PdfTextExtractor.WordBox wb : pageWords) {
                    if (wb.charStart >= entry.charStart && wb.charEnd <= entry.charEnd) {
                        renderList.add(new HighlightOverlayView.PersistentHighlight(wb, color));
                    }
                }
            }
        }
        highlightOverlay.setPersistentHighlights(renderList);
    }

    private void translate(List<PdfTextExtractor.WordBox> words) {
        StringBuilder sb = new StringBuilder();
        for (PdfTextExtractor.WordBox wb : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(wb.word);
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) return;

        try {
            Intent translateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://translate.google.com/?sl=auto&tl=" + java.util.Locale.getDefault().getLanguage()
                            + "&text=" + Uri.encode(text) + "&op=translate"));
            context.startActivity(translateIntent);
        } catch (Throwable t) {
            Toast.makeText(context, "Could not open translator", Toast.LENGTH_SHORT).show();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}