package neunix.pagevibe.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Transparent input layer for tap-based text selection, following the
 * exact same enable/disable + click-through pattern as DrawingView: when
 * inactive it returns false from onTouchEvent and lets everything pass
 * through to CurlView/GalleryZoomView underneath; when active it
 * consumes taps and reports the raw screen coordinate up to the
 * controller, which does all the actual hit-testing.
 */
public class TextSelectionView extends View {

    public interface OnWordTapListener { void onWordTap(float screenX, float screenY); }

    private boolean mEnabled = false;
    private OnWordTapListener mListener;

    public TextSelectionView(Context c) { super(c); }
    public TextSelectionView(Context c, AttributeSet a) { super(c, a); }

    public void setOnWordTapListener(OnWordTapListener l) { mListener = l; }

    public void setSelectionEnabled(boolean enabled) {
        mEnabled = enabled;
        setClickable(enabled);
        setFocusable(enabled);
    }

    public boolean isSelectionEnabled() { return mEnabled; }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mEnabled) return false;
        if (event.getAction() != MotionEvent.ACTION_UP) return true; // consume down/move; act on tap release
        if (mListener != null) mListener.onWordTap(event.getX(), event.getY());
        return true;
    }
}