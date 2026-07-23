package neunix.pagevibe;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PdfSearchController {

    public interface OnResultSelected { void onResult(int page); }

    private final android.content.Context context;
    private final PdfReaderController     reader;
    private final OnResultSelected        onResultSelected;
    private final android.os.Handler      uiHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final View        panel;
    private final EditText    input;
    private final TextView    resultText;
    private final ImageButton btnPrev, btnNext, btnClose, triggerButton;

    private final List<PdfTextExtractor.SearchResult> results = new ArrayList<>();
    private int     resultIndex = -1;
    private boolean visible     = false;
    private String  lastQuery   = "";

    // Injected from PdfActivity — may be null on older layouts
    private HighlightOverlayView highlightOverlay;

    public PdfSearchController(android.content.Context context,
                                PdfReaderController reader,
                                View panel, EditText input, TextView resultText,
                                ImageButton btnPrev, ImageButton btnNext,
                                ImageButton btnClose, ImageButton triggerButton,
                                OnResultSelected onResultSelected) {
        this.context          = context;
        this.reader           = reader;
        this.panel            = panel;
        this.input            = input;
        this.resultText       = resultText;
        this.btnPrev          = btnPrev;
        this.btnNext          = btnNext;
        this.btnClose         = btnClose;
        this.triggerButton    = triggerButton;
        this.onResultSelected = onResultSelected;
        init();
    }

    /** Call from PdfActivity after the overlay view is inflated. */
    public void attachHighlightOverlay(HighlightOverlayView overlay) {
        this.highlightOverlay = overlay;
    }

    // =========================================================
    // INIT
    // =========================================================
    private void init() {
        triggerButton.setOnClickListener(v -> toggle());
        btnClose.setOnClickListener(v -> hide());

        btnNext.setOnClickListener(v -> {
            if (results.isEmpty()) return;
            resultIndex = (resultIndex + 1) % results.size();
            jumpToResult();
        });
        btnPrev.setOnClickListener(v -> {
            if (results.isEmpty()) return;
            resultIndex = (resultIndex - 1 + results.size()) % results.size();
            jumpToResult();
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(input.getText().toString().trim());
                hideKeyboard();
                return true;
            }
            return false;
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    results.clear();
                    resultIndex = -1;
                    lastQuery   = "";
                    resultText.setText("");
                    if (highlightOverlay != null) highlightOverlay.clearSearchHighlights();
                }
            }
        });

        TooltipUtil.apply(triggerButton, "Search in PDF");
        TooltipUtil.apply(btnPrev,       "Previous result");
        TooltipUtil.apply(btnNext,       "Next result");
        TooltipUtil.apply(btnClose,      "Close search");
    }

    // =========================================================
    // SHOW / HIDE
    // =========================================================
    public void toggle() { if (visible) hide(); else show(); }

    public void show() {
        visible = true;
        panel.setVisibility(View.VISIBLE);
        panel.animate().translationY(0).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
        input.requestFocus();
        showKeyboard();
    }

    public void hide() {
        visible = false;
        hideKeyboard();
        panel.animate().translationY(-400).setDuration(220)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
        if (highlightOverlay != null) highlightOverlay.clearSearchHighlights();
    }

    // =========================================================
    // SEARCH
    // =========================================================
    private void runSearch(String query) {
        PdfTextExtractor extractor = reader.getExtractor();
        if (query.isEmpty() || extractor == null || !extractor.isOpen()) return;
        lastQuery = query;
        resultText.setText("Searching…");
        results.clear();
        resultIndex = -1;
        if (highlightOverlay != null) highlightOverlay.clearSearchHighlights();

        reader.getBgExecutor().execute(() -> {
            List<PdfTextExtractor.SearchResult> found =
                    extractor.searchAll(query, reader.getTotalPages());
            uiHandler.post(() -> {
                results.addAll(found);
                if (results.isEmpty()) {
                    resultText.setText("No results for \"" + query + "\"");
                } else {
                    resultIndex = 0;
                    updateLabel();
                    jumpToResult();
                }
            });
        });
    }

    private void jumpToResult() {
        if (resultIndex < 0 || resultIndex >= results.size()) return;
        updateLabel();
        PdfTextExtractor.SearchResult r = results.get(resultIndex);
        // Navigate first, then update highlights for the destination page
        onResultSelected.onResult(r.page);
        pushHighlightsForPage(r.page);
    }

    /**
     * Called whenever the visible page changes (including after navigation
     * from TOC, slider, or swipe) so that if the new page has search results
     * they are highlighted automatically.
     */
    public void onPageChanged(int page) {
        if (results.isEmpty() || lastQuery.isEmpty()) return;
        pushHighlightsForPage(page);
    }

    /**
     * Builds the word-level highlight set for a page using
     * findMatchGroups() — the SAME canonical-text scan searchAll() used to
     * produce the SearchResult list — so "result index N on this page"
     * and "match group N on this page" are guaranteed to be the same
     * occurrence. The active occurrence is marked by WordBox.id rather
     * than list position, so it stays correct even for multi-word phrase
     * matches.
     */
    private void pushHighlightsForPage(int page) {
        if (highlightOverlay == null || lastQuery.isEmpty()) return;

        PdfTextExtractor extractor = reader.getExtractor();
        if (extractor == null || !extractor.isOpen()) return;

        List<Integer> pageResultIndices = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).page == page) pageResultIndices.add(i);
        }
        if (pageResultIndices.isEmpty()) {
            highlightOverlay.clearSearchHighlights();
            return;
        }

        int activeLocal = -1;
        for (int li = 0; li < pageResultIndices.size(); li++) {
            if (pageResultIndices.get(li) == resultIndex) { activeLocal = li; break; }
        }
        final int activeGroupIndex = activeLocal;

        reader.getBgExecutor().execute(() -> {
            List<PdfTextExtractor.MatchGroup> groups = extractor.findMatchGroups(page, lastQuery);

            List<PdfTextExtractor.WordBox> allWords = new ArrayList<>();
            Set<Integer> activeIds = new HashSet<>();

            for (int gi = 0; gi < groups.size(); gi++) {
                PdfTextExtractor.MatchGroup group = groups.get(gi);
                allWords.addAll(group.words);
                if (gi == activeGroupIndex) {
                    for (PdfTextExtractor.WordBox wb : group.words) activeIds.add(wb.id);
                }
            }

            uiHandler.post(() -> {
                if (highlightOverlay != null) {
                    highlightOverlay.setSearchHighlights(allWords, activeIds);
                }
            });
        });
    }

    private void updateLabel() {
        if (results.isEmpty()) return;
        PdfTextExtractor.SearchResult r = results.get(resultIndex);
        resultText.setText("Result " + (resultIndex + 1) + " of "
                + results.size() + "  —  " + r.snippet);
    }

    // =========================================================
    // KEYBOARD
    // =========================================================
    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    public void reset() {
        results.clear();
        resultIndex = -1;
        lastQuery   = "";
        resultText.setText("");
        if (highlightOverlay != null) highlightOverlay.clearSearchHighlights();
    }
}