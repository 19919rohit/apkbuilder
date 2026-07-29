package neunix.pagevibe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Transparent overlay drawing FOUR kinds of highlight, all as rounded,
 * translucent pills over the already-rendered PDF text:
 *  1. Persistent user highlights (saved, colour chosen by the user)
 *  2. Search results (amber / active-orange)
 *  3. Text-selection in progress (cyan)
 *  4. TTS word-in-progress (pulsing azure)
 *
 * Draw order is bottom-to-top in that list, so a live TTS highlight is
 * always visible even if it happens to overlap a saved highlight.
 *
 * This view is ALWAYS click-through when there's nothing to select — the
 * actual tap handling for text selection lives in TextSelectionView, a
 * separate sibling layer, so this view's own "always click-through"
 * contract (needed for search/TTS) is never compromised.
 */
public class HighlightOverlayView extends View {

    public static class PersistentHighlight {
        public final PdfTextExtractor.WordBox box;
        public final int color;
        public PersistentHighlight(PdfTextExtractor.WordBox box, int color) { this.box = box; this.color = color; }
    }

    private static final int SEARCH_OTHER_FILL   = 0x99FFC400;
    private static final int SEARCH_OTHER_STROKE = 0xCCCC9900;

    private static final int SEARCH_ACTIVE_FILL   = 0xA6FF6D00;
    private static final int SEARCH_ACTIVE_STROKE = 0xD9CC5200;

    private static final int TTS_FILL   = 0x992F80FF;
    private static final int TTS_STROKE = 0xCC1B5FCC;

    private static final int SELECTION_FILL   = 0x8800BFFF;
    private static final int SELECTION_STROKE = 0xCC0090C0;

    private final Paint mFillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<PdfTextExtractor.WordBox> mSearchWordBoxes = new ArrayList<>();
    private final Set<Integer> mActiveSearchIds = new HashSet<>();

    private final List<PdfTextExtractor.WordBox> mSelectionWordBoxes = new ArrayList<>();
    private final List<PersistentHighlight> mPersistentHighlights = new ArrayList<>();

    private PdfTextExtractor.WordBox mTtsWordBox = null;
    private float                    mTtsPulse   = 1f;

    private ValueAnimator mPulseAnimator;

    private float mPageContentW = 0f;
    private float mPageContentH = 0f;

    private GalleryZoomView mZoomHost;

    public HighlightOverlayView(Context context) { super(context); init(); }
    public HighlightOverlayView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public HighlightOverlayView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

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

    public void attachZoomHost(GalleryZoomView host) { mZoomHost = host; }

    public void setPageSize(float pageWidthPts, float pageHeightPts) {
        if (pageWidthPts <= 0f || pageHeightPts <= 0f) return;
        mPageContentW = pageWidthPts;
        mPageContentH = pageHeightPts;
        invalidate();
    }

    // ── Search ──────────────────────────────────────────────
    public void setSearchHighlights(List<PdfTextExtractor.WordBox> boxes, Set<Integer> activeIds) {
        mSearchWordBoxes.clear();
        mActiveSearchIds.clear();
        if (boxes != null) mSearchWordBoxes.addAll(boxes);
        if (activeIds != null) mActiveSearchIds.addAll(activeIds);
        invalidate();
    }
    public void clearSearchHighlights() { mSearchWordBoxes.clear(); mActiveSearchIds.clear(); invalidate(); }

    // ── TTS ─────────────────────────────────────────────────
    public void setTtsHighlight(PdfTextExtractor.WordBox box) {
        mTtsWordBox = box;
        if (mTtsWordBox != null) { if (!mPulseAnimator.isRunning()) mPulseAnimator.start(); }
        else { mPulseAnimator.cancel(); mTtsPulse = 1f; }
        invalidate();
    }
    public void clearTtsHighlight() { setTtsHighlight(null); }

    // ── Selection (in-progress, before commit) ─────────────
    public void setSelectionHighlights(List<PdfTextExtractor.WordBox> boxes) {
        mSelectionWordBoxes.clear();
        if (boxes != null) mSelectionWordBoxes.addAll(boxes);
        invalidate();
    }
    public void clearSelectionHighlights() { mSelectionWordBoxes.clear(); invalidate(); }

    // ── Persistent (saved) highlights ──────────────────────
    public void setPersistentHighlights(List<PersistentHighlight> highlights) {
        mPersistentHighlights.clear();
        if (highlights != null) mPersistentHighlights.addAll(highlights);
        invalidate();
    }
    public void clearPersistentHighlights() { mPersistentHighlights.clear(); invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        for (PersistentHighlight ph : mPersistentHighlights) {
            drawWordPill(canvas, ph.box, withAlpha(ph.color, 0x99), withAlpha(darken(ph.color, 0.75f), 0xCC), 1f);
        }

        for (PdfTextExtractor.WordBox wb : mSearchWordBoxes) {
            boolean active = mActiveSearchIds.contains(wb.id);
            drawWordPill(canvas, wb,
                    active ? SEARCH_ACTIVE_FILL   : SEARCH_OTHER_FILL,
                    active ? SEARCH_ACTIVE_STROKE : SEARCH_OTHER_STROKE, 1f);
        }

        for (PdfTextExtractor.WordBox wb : mSelectionWordBoxes) {
            drawWordPill(canvas, wb, SELECTION_FILL, SELECTION_STROKE, 1f);
        }

        if (mTtsWordBox != null) {
            drawWordPill(canvas, mTtsWordBox, TTS_FILL, TTS_STROKE, mTtsPulse);
        }
    }

    private void drawWordPill(Canvas canvas, PdfTextExtractor.WordBox wb, int fillColor, int strokeColor, float alphaMultiplier) {
        RectF tight = normToPixel(wb);
        if (tight.width() <= 0f || tight.height() <= 0f) return;

        float padH = Math.max(tight.height() * 0.30f, 6f);
        float padW = Math.max(tight.width()  * 0.16f, 8f);
        RectF pill = new RectF(tight.left - padW, tight.top - padH, tight.right + padW, tight.bottom + padH);
        float cornerRadius = pill.height() / 2f;

        mFillPaint.setColor(fillColor);
        mFillPaint.setAlpha(Math.round(Color.alpha(fillColor) * alphaMultiplier));
        canvas.drawRoundRect(pill, cornerRadius, cornerRadius, mFillPaint);

        mStrokePaint.setColor(strokeColor);
        mStrokePaint.setAlpha(Math.round(Color.alpha(strokeColor) * alphaMultiplier));
        canvas.drawRoundRect(pill, cornerRadius, cornerRadius, mStrokePaint);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int darken(int color, float factor) {
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.rgb(r, g, b);
    }

    // ── Coordinate conversion (norm ↔ pixel) ───────────────

    private RectF normToPixel(PdfTextExtractor.WordBox wb) {
        float vw = getWidth(), vh = getHeight();
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
            float cx = vw / 2f, cy = vh / 2f;
            Matrix m = new Matrix();
            m.postScale(scale, scale, cx, cy);
            m.postTranslate(mZoomHost.getTransX(), mZoomHost.getTransY());
            float[] pts = { left, top, right, bottom };
            m.mapPoints(pts);
            return new RectF(pts[0], pts[1], pts[2], pts[3]);
        }
        return new RectF(left, top, right, bottom);
    }

    /**
     * Inverse of normToPixel's coordinate pipeline — converts an
     * on-screen pixel (as reported by a tap) into normalised [0,1] page
     * coordinates, accounting for BOTH the letterbox scale/offset AND
     * the live zoom/pan transform. Used by PdfSelectionController for
     * hit-testing which word was tapped.
     */
    public PointF pixelToNormPoint(float screenX, float screenY) {
        float vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) return null;

        float x = screenX, y = screenY;
        if (mZoomHost != null) {
            float scale = mZoomHost.getScaleFactor();
            float cx = vw / 2f, cy = vh / 2f;
            Matrix m = new Matrix();
            m.postScale(scale, scale, cx, cy);
            m.postTranslate(mZoomHost.getTransX(), mZoomHost.getTransY());
            Matrix inv = new Matrix();
            if (!m.invert(inv)) return null;
            float[] pts = { screenX, screenY };
            inv.mapPoints(pts);
            x = pts[0]; y = pts[1];
        }

        float renderW = vw, renderH = vh, offsetX = 0f, offsetY = 0f;
        if (mPageContentW > 0f && mPageContentH > 0f) {
            float scale = Math.min(vw / mPageContentW, vh / mPageContentH);
            renderW = mPageContentW * scale;
            renderH = mPageContentH * scale;
            offsetX = (vw - renderW) / 2f;
            offsetY = (vh - renderH) / 2f;
        }
        if (renderW <= 0 || renderH <= 0) return null;

        return new PointF((x - offsetX) / renderW, (y - offsetY) / renderH);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mPulseAnimator != null) mPulseAnimator.cancel();
    }
}