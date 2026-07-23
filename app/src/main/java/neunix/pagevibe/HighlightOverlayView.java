package neunix.pagevibe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Transparent overlay that draws two kinds of highlight, both rendered as
 * a rounded, translucent "pill" that fully SURROUNDS the word — like a
 * real highlighter marker laid over the already-rendered PDF text. The
 * word's own glyphs (already part of the rendered page bitmap underneath)
 * remain visible through the tint; nothing is redrawn on top of it.
 *
 *  1. Search highlights — every occurrence of the current query on the
 *     visible page, in a vivid amber pill, with the currently-selected
 *     occurrence in a more vivid orange pill.
 *  2. TTS word highlight — a pulsing vivid blue pill following speech.
 *
 * Highlight boxes come in as normalised [0,1] coordinates relative to the
 * PDF page. The rendered page bitmap is letterboxed to fit the view
 * (PdfCore scales the page to fit width/height while preserving aspect
 * ratio, centering it) — so to line up correctly, this view reproduces
 * that same scale + offset, plus GalleryZoomView's live pinch-zoom
 * scale/pan, every draw.
 *
 * This view is ALWAYS click-through — touches pass to whatever is below.
 */
public class HighlightOverlayView extends View {

    // ── Vibrant, translucent marker colours (ARGB) — chosen so the real
    // rendered text underneath stays legible through the tint, since we
    // deliberately do NOT redraw the word on top of the pill. ───────────
    private static final int SEARCH_OTHER_FILL   = 0x99FFC400; // vivid amber marker
    private static final int SEARCH_OTHER_STROKE = 0xCCCC9900;

    private static final int SEARCH_ACTIVE_FILL   = 0xA6FF6D00; // vivid orange marker
    private static final int SEARCH_ACTIVE_STROKE = 0xD9CC5200;

    private static final int TTS_FILL   = 0x992F80FF; // vivid azure marker
    private static final int TTS_STROKE = 0xCC1B5FCC;

    // ── Paints — allocated once, reused every draw ──────────────────────
    private final Paint mFillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Highlight data — stored as RAW normalised boxes, converted to
    // pixel rects at draw time, so positions stay correct across page
    // size updates, view resizes, and zoom/pan changes. ─────────────────
    private final List<PdfTextExtractor.WordBox> mSearchWordBoxes = new ArrayList<>();
    private final Set<Integer> mActiveSearchIds = new HashSet<>();

    private PdfTextExtractor.WordBox mTtsWordBox = null;
    private float                    mTtsPulse   = 1f;

    private ValueAnimator mPulseAnimator;

    private float mPageContentW = 0f;
    private float mPageContentH = 0f;

    private GalleryZoomView mZoomHost;

    // =========================================================
    // CONSTRUCTORS
    // =========================================================

    public HighlightOverlayView(Context context) {
        super(context);
        init();
    }

    public HighlightOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HighlightOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);

        mFillPaint.setStyle(Paint.Style.FILL);

        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(2.5f);

        mPulseAnimator = ValueAnimator.ofFloat(0.55f, 1.0f);
        mPulseAnimator.setDuration(650);
        mPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mPulseAnimator.setInterpolator(new LinearInterpolator());
        mPulseAnimator.addUpdateListener(a -> {
            mTtsPulse = (float) a.getAnimatedValue();
            if (mTtsWordBox != null) invalidate();
        });
    }

    // =========================================================
    // ZOOM HOST
    // =========================================================

    public void attachZoomHost(GalleryZoomView host) {
        mZoomHost = host;
    }

    // =========================================================
    // PAGE SIZE
    // =========================================================

    public void setPageSize(float pageWidthPts, float pageHeightPts) {
        if (pageWidthPts <= 0f || pageHeightPts <= 0f) return;
        mPageContentW = pageWidthPts;
        mPageContentH = pageHeightPts;
        invalidate();
    }

    // =========================================================
    // SEARCH HIGHLIGHTS
    //
    // activeIds identifies which of the supplied boxes belong to the
    // currently-selected search result, matched by WordBox.id (a stable
    // per-word identity from the page's canonical text) rather than by
    // position in the list — a list-position index breaks the moment a
    // match spans more than one word, since "the Nth result" and "the Nth
    // box" stop being the same index. Matching by id has no such failure
    // mode.
    // =========================================================

    public void setSearchHighlights(List<PdfTextExtractor.WordBox> boxes, Set<Integer> activeIds) {
        mSearchWordBoxes.clear();
        mActiveSearchIds.clear();
        if (boxes != null) mSearchWordBoxes.addAll(boxes);
        if (activeIds != null) mActiveSearchIds.addAll(activeIds);
        invalidate();
    }

    public void clearSearchHighlights() {
        mSearchWordBoxes.clear();
        mActiveSearchIds.clear();
        invalidate();
    }

    // =========================================================
    // TTS WORD HIGHLIGHT
    // =========================================================

    public void setTtsHighlight(PdfTextExtractor.WordBox box) {
        mTtsWordBox = box;
        if (mTtsWordBox != null) {
            if (!mPulseAnimator.isRunning()) mPulseAnimator.start();
        } else {
            mPulseAnimator.cancel();
            mTtsPulse = 1f;
        }
        invalidate();
    }

    public void clearTtsHighlight() {
        setTtsHighlight(null);
    }

    // =========================================================
    // DRAW
    // =========================================================

    @Override
    protected void onDraw(Canvas canvas) {
        for (PdfTextExtractor.WordBox wb : mSearchWordBoxes) {
            boolean active = mActiveSearchIds.contains(wb.id);
            drawWordPill(canvas, wb,
                    active ? SEARCH_ACTIVE_FILL   : SEARCH_OTHER_FILL,
                    active ? SEARCH_ACTIVE_STROKE : SEARCH_OTHER_STROKE,
                    1f);
        }

        if (mTtsWordBox != null) {
            drawWordPill(canvas, mTtsWordBox, TTS_FILL, TTS_STROKE, mTtsPulse);
        }
    }

    /**
     * Draws one word as a rounded, translucent pill that surrounds it —
     * a highlighter-marker effect over the real rendered text underneath.
     * Deliberately does NOT redraw the word's text: the actual glyph is
     * already part of the page bitmap beneath this overlay, and drawing
     * it a second time on top duplicated the word and always looked
     * wrong regardless of colour choice.
     */
    private void drawWordPill(Canvas canvas, PdfTextExtractor.WordBox wb,
                               int fillColor, int strokeColor,
                               float alphaMultiplier) {
        RectF tight = normToPixel(wb);
        if (tight.width() <= 0f || tight.height() <= 0f) return;

        // Outset the tight glyph bounds so the pill visibly SURROUNDS the
        // word with breathing room, instead of hugging it exactly.
        float padH = Math.max(tight.height() * 0.30f, 6f);
        float padW = Math.max(tight.width()  * 0.16f, 8f);
        RectF pill = new RectF(
                tight.left   - padW,
                tight.top    - padH,
                tight.right  + padW,
                tight.bottom + padH);

        float cornerRadius = pill.height() / 2f;

        int fillAlpha = Math.round(Color.alpha(fillColor) * alphaMultiplier);
        mFillPaint.setColor(fillColor);
        mFillPaint.setAlpha(fillAlpha);
        canvas.drawRoundRect(pill, cornerRadius, cornerRadius, mFillPaint);

        mStrokePaint.setColor(strokeColor);
        mStrokePaint.setAlpha(Math.round(Color.alpha(strokeColor) * alphaMultiplier));
        canvas.drawRoundRect(pill, cornerRadius, cornerRadius, mStrokePaint);
    }

    // =========================================================
    // COORDINATE CONVERSION
    // =========================================================

    private RectF normToPixel(PdfTextExtractor.WordBox wb) {
        float vw = getWidth();
        float vh = getHeight();
        if (vw <= 0 || vh <= 0) return new RectF();

        float renderW = vw, renderH = vh, offsetX = 0f, offsetY = 0f;
        if (mPageContentW > 0f && mPageContentH > 0f) {
            float scale = Math.min(vw / mPageContentW, vh / mPageContentH);
            renderW = mPageContentW * scale;
            renderH = mPageContentH * scale;
            offsetX = (vw - renderW) / 2f;
            offsetY = (vh - renderH) / 2f;
        }

        float left   = offsetX + wb.left   * renderW;
        float top    = offsetY + wb.top    * renderH;
        float right  = offsetX + wb.right  * renderW;
        float bottom = offsetY + wb.bottom * renderH;

        if (mZoomHost != null) {
            float scale = mZoomHost.getScaleFactor();
            float cx = vw / 2f;
            float cy = vh / 2f;

            Matrix m = new Matrix();
            m.postScale(scale, scale, cx, cy);
            m.postTranslate(mZoomHost.getTransX(), mZoomHost.getTransY());

            float[] pts = { left, top, right, bottom };
            m.mapPoints(pts);
            return new RectF(pts[0], pts[1], pts[2], pts[3]);
        }

        return new RectF(left, top, right, bottom);
    }

    // =========================================================
    // LIFECYCLE
    // =========================================================

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mPulseAnimator != null) mPulseAnimator.cancel();
    }
}