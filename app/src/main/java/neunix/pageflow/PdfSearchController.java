package neunix.pageflow;

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
import java.util.List;

public class PdfSearchController {

    public interface OnResultSelected { void onResult(int page); }

    private final android.content.Context context;
    private final PdfReaderController reader;
    private final OnResultSelected onResultSelected;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private final View      panel;
    private final EditText  input;
    private final TextView  resultText;
    private final ImageButton btnPrev, btnNext, btnClose, triggerButton;

    private final List<PdfTextExtractor.SearchResult> results = new ArrayList<>();
    private int resultIndex = -1;
    private boolean visible = false;

    public PdfSearchController(android.content.Context context, PdfReaderController reader,
                                View panel, EditText input, TextView resultText,
                                ImageButton btnPrev, ImageButton btnNext, ImageButton btnClose,
                                ImageButton triggerButton, OnResultSelected onResultSelected) {
        this.context = context;
        this.reader = reader;
        this.panel = panel;
        this.input = input;
        this.resultText = resultText;
        this.btnPrev = btnPrev;
        this.btnNext = btnNext;
        this.btnClose = btnClose;
        this.triggerButton = triggerButton;
        this.onResultSelected = onResultSelected;
        init();
    }

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
                    results.clear(); resultIndex = -1; resultText.setText("");
                }
            }
        });

        TooltipUtil.apply(triggerButton, "Search in PDF");
        TooltipUtil.apply(btnPrev, "Previous result");
        TooltipUtil.apply(btnNext, "Next result");
        TooltipUtil.apply(btnClose, "Close search");
    }

    public void toggle() { if (visible) hide(); else show(); }

    public void show() {
        visible = true;
        panel.setVisibility(View.VISIBLE);
        panel.animate().translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        input.requestFocus();
        showKeyboard();
    }

    public void hide() {
        visible = false;
        hideKeyboard();
        panel.animate().translationY(-400).setDuration(220)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
    }

    private void runSearch(String query) {
        PdfTextExtractor extractor = reader.getExtractor();
        if (query.isEmpty() || extractor == null || !extractor.isOpen()) return;
        resultText.setText("Searching…");
        results.clear();
        resultIndex = -1;

        reader.getBgExecutor().execute(() -> {
            List<PdfTextExtractor.SearchResult> found = extractor.searchAll(query, reader.getTotalPages());
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
        onResultSelected.onResult(results.get(resultIndex).page);
    }

    private void updateLabel() {
        if (results.isEmpty()) return;
        PdfTextExtractor.SearchResult r = results.get(resultIndex);
        resultText.setText("Result " + (resultIndex + 1) + " of " + results.size() + "  —  " + r.snippet);
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    public void reset() {
        results.clear();
        resultIndex = -1;
        resultText.setText("");
    }
}