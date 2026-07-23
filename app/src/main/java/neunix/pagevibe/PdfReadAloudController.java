package neunix.pagevibe;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PdfReadAloudController {

    private final Context  context;
    private final PdfReaderController reader;
    private final Handler  uiHandler = new Handler(Looper.getMainLooper());

    private final View        bar;
    private final TextView    statusText;
    private final ImageButton btnPlayPause;
    private final ImageButton btnStop;
    private final ImageButton triggerButton;

    private HighlightOverlayView highlightOverlay;

    private TextToSpeech tts;
    private boolean      ttsReady   = false;
    private boolean      ttsPlaying = false;
    private boolean      ttsPaused  = false;

    private final AtomicInteger ttsLockedPage = new AtomicInteger(-1);

    private String                          ttsPageText  = "";
    private List<PdfTextExtractor.WordBox>  ttsWordBoxes = null;

    private int mWordCursor = 0;
    private int mLastSpokenCharEnd = 0;

    public PdfReadAloudController(Context context, PdfReaderController reader,
                                   View bar, TextView statusText,
                                   ImageButton btnPlayPause, ImageButton btnStop,
                                   ImageButton triggerButton) {
        this.context      = context;
        this.reader       = reader;
        this.bar          = bar;
        this.statusText   = statusText;
        this.btnPlayPause = btnPlayPause;
        this.btnStop      = btnStop;
        this.triggerButton = triggerButton;
        init();
    }

    public void attachHighlightOverlay(HighlightOverlayView overlay) {
        this.highlightOverlay = overlay;
    }

    private void init() {
        tts = new TextToSpeech(context, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int langResult = tts.setLanguage(Locale.getDefault());
            ttsReady = langResult != TextToSpeech.LANG_MISSING_DATA
                    && langResult != TextToSpeech.LANG_NOT_SUPPORTED;
            if (!ttsReady) return;

            try {
                Set<Voice> voices = tts.getVoices();
                if (voices != null) {
                    Voice best = null;
                    for (Voice v : voices) {
                        if (v.isNetworkConnectionRequired()) continue;
                        if (!v.getLocale().getLanguage()
                                .equals(Locale.getDefault().getLanguage())) continue;
                        if (best == null || v.getQuality() > best.getQuality()) best = v;
                    }
                    if (best != null) tts.setVoice(best);
                }
            } catch (Exception ignored) {}

            tts.setSpeechRate(0.92f);
            tts.setPitch(1.0f);
        });

        btnPlayPause.setOnClickListener(v -> { if (ttsPlaying) pauseTts(); else resumeTts(); });
        btnStop.setOnClickListener(v -> stop());

        TooltipUtil.apply(triggerButton, "Read this page aloud");
        TooltipUtil.apply(btnPlayPause, "Play / Pause");
        TooltipUtil.apply(btnStop, "Stop reading");
    }

    public void toggle() {
        if (ttsPlaying) { stop(); return; }
        if (!ttsReady)  { toast("Text-to-speech engine not ready"); return; }
        ttsLockedPage.set(reader.getSettledPage());
        ttsPaused = false;
        readLockedPage();
    }

    private void readLockedPage() {
        int page = ttsLockedPage.get();
        if (page < 0) return;

        PdfTextExtractor extractor = reader.getExtractor();
        if (extractor == null || !extractor.isOpen()) { toast("PDF not ready"); return; }

        statusText.setText("Extracting text…");
        showBar(true);

        reader.getBgExecutor().execute(() -> {
            PdfTextExtractor.PageWordData data = extractor.extractPageWordData(page);

            uiHandler.post(() -> {
                if (ttsLockedPage.get() != page) return;
                if (data.text == null || data.text.trim().isEmpty()) {
                    statusText.setText("Page " + (page + 1) + " — no text (image PDF)");
                    ttsPlaying = false;
                    setTriggerColor(false);
                    return;
                }
                ttsPageText        = data.text;
                ttsWordBoxes       = data.words;
                mWordCursor        = 0;
                mLastSpokenCharEnd = 0;
                speakChunked(ttsPageText, page, 0);
            });
        });
    }

    private void speakChunked(String text, int pageIndex, int baseOffset) {
        ttsPlaying = true;
        ttsPaused  = false;
        btnPlayPause.setImageResource(R.drawable.ic_pause);
        setTriggerColor(true);
        statusText.setText("Reading page " + (pageIndex + 1) + "…");

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

                int absoluteStart = baseOffset + chunkOffsetFromId(utteranceId) + start;
                int absoluteEnd   = baseOffset + chunkOffsetFromId(utteranceId) + end;

                mLastSpokenCharEnd = Math.max(mLastSpokenCharEnd, absoluteEnd);

                uiHandler.post(() -> {
                    if (highlightOverlay == null || ttsWordBoxes == null) return;
                    PdfTextExtractor.WordBox match = findWordBoxByOffset(absoluteStart, absoluteEnd);
                    if (match != null) highlightOverlay.setTtsHighlight(match);
                });
            }

            @Override
            public void onDone(String id) {
                if (!id.endsWith("_last")) return;
                uiHandler.post(() -> {
                    if (!ttsPlaying && !ttsPaused) return;
                    ttsPlaying = false;
                    ttsPaused  = false;
                    btnPlayPause.setImageResource(R.drawable.ic_play);
                    setTriggerColor(false);
                    statusText.setText("Finished — page " + (pageIndex + 1));
                    if (highlightOverlay != null) highlightOverlay.clearTtsHighlight();
                });
            }

            @Override
            public void onError(String id) {
                uiHandler.post(() -> stop());
            }
        });

        tts.stop();

        mChunkOffsets.clear();
        int chunkSize = 3800;
        int offset = 0, chunkIndex = 0;
        while (offset < text.length()) {
            int end = Math.min(offset + chunkSize, text.length());
            if (end < text.length()) {
                int brk = findSentenceBreak(text, offset, end);
                if (brk > offset) end = brk;
            }

            String  chunk  = text.substring(offset, end);
            boolean isLast = (end >= text.length());
            String  uid    = "pg" + pageIndex + "_c" + chunkIndex + (isLast ? "_last" : "");

            mChunkOffsets.add(offset);

            Bundle params = new Bundle();
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, params, uid);
            offset = end;
            chunkIndex++;
        }
    }

    private final java.util.ArrayList<Integer> mChunkOffsets = new java.util.ArrayList<>();

    private int chunkOffsetFromId(String uid) {
        try {
            int cIdx = uid.indexOf("_c");
            if (cIdx < 0) return 0;
            String after = uid.substring(cIdx + 2);
            int end = after.indexOf('_');
            String numStr = end >= 0 ? after.substring(0, end) : after;
            int chunkNum = Integer.parseInt(numStr);
            if (chunkNum < mChunkOffsets.size()) return mChunkOffsets.get(chunkNum);
        } catch (Exception ignored) {}
        return 0;
    }

    private PdfTextExtractor.WordBox findWordBoxByOffset(int absStart, int absEnd) {
        if (ttsWordBoxes == null || ttsWordBoxes.isEmpty()) return null;

        int n = ttsWordBoxes.size();
        if (mWordCursor >= n) mWordCursor = n - 1;
        if (mWordCursor < 0)  mWordCursor = 0;

        while (mWordCursor < n - 1 && ttsWordBoxes.get(mWordCursor).charEnd <= absStart) {
            mWordCursor++;
        }

        PdfTextExtractor.WordBox candidate = ttsWordBoxes.get(mWordCursor);
        if (absStart < candidate.charEnd && absEnd > candidate.charStart) {
            return candidate;
        }

        for (int i = Math.max(0, mWordCursor - 3); i < mWordCursor; i++) {
            PdfTextExtractor.WordBox wb = ttsWordBoxes.get(i);
            if (absStart < wb.charEnd && absEnd > wb.charStart) return wb;
        }

        return candidate;
    }

    private int findSentenceBreak(String text, int start, int end) {
        int mid = start + (end - start) / 2;
        for (int i = end - 1; i > mid; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') return i + 1;
        }
        return end;
    }

    private void pauseTts() {
        if (!ttsPlaying) return;
        if (tts != null) tts.stop();
        ttsPlaying = false;
        ttsPaused  = true;
        if (highlightOverlay != null) highlightOverlay.clearTtsHighlight();
        btnPlayPause.setImageResource(R.drawable.ic_play);
        statusText.setText("Paused — page " + (ttsLockedPage.get() + 1));
    }

    private void resumeTts() {
        if (ttsPlaying) return;
        if (!ttsReady) { toast("Text-to-speech engine not ready"); return; }

        if (ttsPaused && ttsLockedPage.get() >= 0
                && ttsPageText != null && !ttsPageText.isEmpty()) {

            int resumeFrom = Math.min(mLastSpokenCharEnd, ttsPageText.length());
            String remaining = ttsPageText.substring(resumeFrom);

            if (remaining.trim().isEmpty()) {
                ttsPlaying = false;
                ttsPaused  = false;
                btnPlayPause.setImageResource(R.drawable.ic_play);
                setTriggerColor(false);
                statusText.setText("Finished — page " + (ttsLockedPage.get() + 1));
                if (highlightOverlay != null) highlightOverlay.clearTtsHighlight();
                return;
            }

            statusText.setText("Resuming…");
            speakChunked(remaining, ttsLockedPage.get(), resumeFrom);
        } else {
            ttsLockedPage.set(reader.getSettledPage());
            ttsPaused = false;
            readLockedPage();
        }
    }

    public void stop() {
        if (tts != null) tts.stop();
        ttsPlaying         = false;
        ttsPaused          = false;
        ttsLockedPage.set(-1);
        ttsPageText        = "";
        ttsWordBoxes       = null;
        mWordCursor        = 0;
        mLastSpokenCharEnd = 0;
        setTriggerColor(false);
        showBar(false);
        if (highlightOverlay != null) highlightOverlay.clearTtsHighlight();
    }

    private void showBar(boolean show) {
        if (show) {
            bar.setVisibility(View.VISIBLE);
            bar.setAlpha(0f);
            bar.animate().alpha(1f).setDuration(200).start();
        } else {
            bar.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> bar.setVisibility(View.GONE)).start();
        }
    }

    private void setTriggerColor(boolean active) {
        triggerButton.setColorFilter(Color.parseColor(active ? "#4488FF" : "#555555"));
    }

    public boolean isPlaying() { return ttsPlaying; }

    public void shutdown() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}