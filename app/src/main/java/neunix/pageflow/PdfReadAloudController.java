package neunix.pageflow;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real text-to-speech reading using pdfbox-extracted text.
 *
 * Bug fix: previously this read whatever `currentPage` happened to be at
 * button-press time, which could be stale/in-flight due to prefetch churn
 * in PdfActivity. Now it always reads PdfReaderController.getSettledPage()
 * — the single authoritative "what page is the user looking at" value —
 * and locks in that exact page number (ttsLockedPage) before extraction
 * even starts, so a navigation that happens mid-extraction can't cause the
 * spoken text to silently switch to a different page than what started.
 */
public class PdfReadAloudController {

    private final Context  context;
    private final PdfReaderController reader;
    private final Handler  uiHandler = new Handler(Looper.getMainLooper());

    private final View       bar;
    private final TextView   statusText;
    private final ImageButton btnPlayPause;
    private final ImageButton btnStop;
    private final ImageButton triggerButton; // the toolbar "read aloud" icon

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean ttsPlaying = false;

    // The page number locked in at the moment Play was pressed — read-aloud
    // always speaks THIS page, even if the user swipes elsewhere mid-read.
    private final AtomicInteger ttsLockedPage = new AtomicInteger(-1);

    public PdfReadAloudController(Context context, PdfReaderController reader,
                                   View bar, TextView statusText,
                                   ImageButton btnPlayPause, ImageButton btnStop,
                                   ImageButton triggerButton) {
        this.context = context;
        this.reader  = reader;
        this.bar = bar;
        this.statusText = statusText;
        this.btnPlayPause = btnPlayPause;
        this.btnStop = btnStop;
        this.triggerButton = triggerButton;
        init();
    }

    private void init() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.getDefault());
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(0.92f);
                tts.setPitch(1.0f);
            }
        });
        btnPlayPause.setOnClickListener(v -> { if (ttsPlaying) pause(); else resume(); });
        btnStop.setOnClickListener(v -> stop());
        TooltipUtil.apply(triggerButton, "Read this page aloud");
        TooltipUtil.apply(btnPlayPause, "Play / Pause");
        TooltipUtil.apply(btnStop, "Stop reading");
    }

    public void toggle() {
        if (ttsPlaying) stop();
        else {
            if (!ttsReady) { toast("Text-to-speech engine not ready"); return; }
            // Lock the page NOW, synchronously, on the UI thread — before
            // any background work starts. This is the critical fix: the
            // spoken page can never drift even if the user navigates while
            // extraction is still running on the bg thread.
            int page = reader.getSettledPage();
            ttsLockedPage.set(page);
            readLockedPage();
        }
    }

    private void readLockedPage() {
        int page = ttsLockedPage.get();
        if (page < 0) return;

        PdfTextExtractor extractor = reader.getExtractor();
        if (extractor == null || !extractor.isOpen()) { toast("PDF not ready"); return; }

        statusText.setText("Extracting text from page " + (page + 1) + "…");
        showBar(true);

        reader.getBgExecutor().execute(() -> {
            String text = extractor.extractPageText(page);
            uiHandler.post(() -> {
                // Re-check: if the locked page somehow changed (shouldn't,
                // but defensive), only proceed if it's still the same lock.
                if (ttsLockedPage.get() != page) return;

                if (text == null || text.isEmpty()) {
                    statusText.setText("Page " + (page + 1) + " has no extractable text (image PDF)");
                    ttsPlaying = false;
                    return;
                }
                speakChunked(text, page);
            });
        });
    }

    private void speakChunked(String text, int pageIndex) {
        ttsPlaying = true;
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        triggerButton.setColorFilter(Color.parseColor("#4488FF"));
        statusText.setText("Reading page " + (pageIndex + 1) + "…");

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) {
                if (id.endsWith("_last")) {
                    uiHandler.post(() -> {
                        ttsPlaying = false;
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                        triggerButton.setColorFilter(Color.parseColor("#555555"));
                        statusText.setText("Finished page " + (pageIndex + 1));
                    });
                }
            }
            @Override public void onError(String id) { uiHandler.post(() -> stop()); }
        });

        tts.stop();
        int chunkSize = 3800;
        int offset = 0, chunkIndex = 0;
        while (offset < text.length()) {
            int end = Math.min(offset + chunkSize, text.length());
            if (end < text.length()) {
                int breakAt = findSentenceBreak(text, offset, end);
                if (breakAt > offset) end = breakAt;
            }
            String chunk = text.substring(offset, end).trim();
            boolean isLast = end >= text.length();
            String utteranceId = "pg" + pageIndex + "_" + chunkIndex + (isLast ? "_last" : "");
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, utteranceId);
            offset = end;
            chunkIndex++;
        }
    }

    private int findSentenceBreak(String text, int start, int end) {
        for (int i = end - 1; i > start + (end - start) / 2; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') return i + 1;
        }
        return end;
    }

    private void pause() {
        if (tts != null) tts.stop();
        ttsPlaying = false;
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        statusText.setText("Paused");
    }

    private void resume() {
        // Re-locks to the CURRENT settled page on resume, matching user
        // expectation: pressing play again after navigating away should
        // read the page they're now looking at, not the old one.
        ttsLockedPage.set(reader.getSettledPage());
        readLockedPage();
    }

    public void stop() {
        if (tts != null) tts.stop();
        ttsPlaying = false;
        ttsLockedPage.set(-1);
        showBar(false);
        triggerButton.setColorFilter(Color.parseColor("#555555"));
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

    public boolean isPlaying() { return ttsPlaying; }

    public void shutdown() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}