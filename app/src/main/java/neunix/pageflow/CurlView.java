/*
   Copyright 2012 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package neunix.pageflow;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * OpenGL ES View.
 *
 * @author harism
 */
public class CurlView extends GLSurfaceView implements View.OnTouchListener,
        CurlRenderer.Observer {

    // Curl state. We are flipping none, left or right page.
    private static final int CURL_LEFT  = 1;
    private static final int CURL_NONE  = 0;
    private static final int CURL_RIGHT = 2;

    // Constants for mAnimationTargetEvent.
    private static final int SET_CURL_TO_LEFT  = 1;
    private static final int SET_CURL_TO_RIGHT = 2;

    // Shows one page at the center of view.
    public static final int SHOW_ONE_PAGE  = 1;
    // Shows two pages side by side.
    public static final int SHOW_TWO_PAGES = 2;

    private boolean mAllowLastPageCurl = true;

    private boolean mAnimate = false;
    private long    mAnimationDurationTime = 300;
    private PointF  mAnimationSource = new PointF();
    private long    mAnimationStartTime;
    private PointF  mAnimationTarget = new PointF();
    private int     mAnimationTargetEvent;

    private PointF mCurlDir = new PointF();
    private PointF mCurlPos = new PointF();
    private int    mCurlState = CURL_NONE;

    // Current bitmap index. This is always showed as front of right page.
    private int mCurrentIndex = 0;

    // Start position for dragging.
    private PointF mDragStartPos = new PointF();

    private boolean mEnableTouchPressure = false;

    // Bitmap size. Updated from renderer once it's initialized.
    private int mPageBitmapHeight = -1;
    private int mPageBitmapWidth  = -1;

    // Page meshes. Left and right are 'static'; curl is used for flipping.
    private CurlMesh mPageCurl;
    private CurlMesh mPageLeft;
    private CurlMesh mPageRight;

    private PageProvider     mPageProvider;
    private PointerPosition  mPointerPos = new PointerPosition();
    private CurlRenderer     mRenderer;
    private boolean          mRenderLeftPage = true;
    private SizeChangedObserver mSizeChangedObserver;

    // ---- Page-settle callback -------------------------------------------
    // Notified exactly once, on the UI thread, whenever a swipe-driven curl
    // animation finishes and mCurrentIndex actually changes value.
    // Without this, callers (PdfReaderController) have no way to know the
    // user has swiped to a new page, causing read-aloud, bookmarks, the page
    // counter, drawings, and the saved position to all reference the WRONG page.
    private OnPageSettleListener mPageSettleListener;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    // -----------------------------------------------------------------------

    // One page is the default.
    private int mViewMode = SHOW_ONE_PAGE;

    // =========================================================
    // CONSTRUCTORS
    // =========================================================

    public CurlView(Context ctx) {
        super(ctx);
        init(ctx);
    }

    public CurlView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init(ctx);
    }

    public CurlView(Context ctx, AttributeSet attrs, int defStyle) {
        this(ctx, attrs);
    }

    // =========================================================
    // INIT
    // =========================================================

    private void init(Context ctx) {
        mRenderer = new CurlRenderer(this);
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setOnTouchListener(this);

        mPageLeft  = new CurlMesh(10);
        mPageRight = new CurlMesh(10);
        mPageCurl  = new CurlMesh(10);
        mPageLeft.setFlipTexture(true);
        mPageRight.setFlipTexture(false);
    }

    // =========================================================
    // CurlRenderer.Observer — DRAW FRAME
    // =========================================================

    @Override
    public void onDrawFrame() {
        if (!mAnimate) return;

        long currentTime = System.currentTimeMillis();

        if (currentTime >= mAnimationStartTime + mAnimationDurationTime) {
            // Animation complete — settle the page.
            // Capture the index BEFORE mutating so we can compare after.
            final int indexBefore = mCurrentIndex;

            if (mAnimationTargetEvent == SET_CURL_TO_RIGHT) {
                CurlMesh right = mPageCurl;
                CurlMesh curl  = mPageRight;
                right.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT));
                right.setFlipTexture(false);
                right.reset();
                mRenderer.removeCurlMesh(curl);
                mPageCurl  = curl;
                mPageRight = right;
                // Curling left page backwards → index decrements.
                if (mCurlState == CURL_LEFT) {
                    --mCurrentIndex;
                }
            } else if (mAnimationTargetEvent == SET_CURL_TO_LEFT) {
                CurlMesh left = mPageCurl;
                CurlMesh curl = mPageLeft;
                left.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_LEFT));
                left.setFlipTexture(true);
                left.reset();
                mRenderer.removeCurlMesh(curl);
                if (!mRenderLeftPage) {
                    mRenderer.removeCurlMesh(left);
                }
                mPageCurl  = curl;
                mPageLeft  = left;
                // Curling right page forwards → index increments.
                if (mCurlState == CURL_RIGHT) {
                    ++mCurrentIndex;
                }
            }

            mCurlState = CURL_NONE;
            mAnimate   = false;
            requestRender();

            // Fire the settle callback on the UI thread ONLY when the index
            // actually changed (i.e. the user completed a real page flip, not
            // an aborted drag that snapped back to the same page).
            final int indexAfter = mCurrentIndex;
            if (indexAfter != indexBefore && mPageSettleListener != null) {
                final OnPageSettleListener listener = mPageSettleListener;
                mMainHandler.post(() -> listener.onPageSettled(indexAfter));
            }

        } else {
            // Animation still in progress — continue interpolating.
            mPointerPos.mPos.set(mAnimationSource);
            float t = 1f - ((float)(currentTime - mAnimationStartTime) / mAnimationDurationTime);
            t = 1f - (t * t * t * (3 - 2 * t));
            mPointerPos.mPos.x += (mAnimationTarget.x - mAnimationSource.x) * t;
            mPointerPos.mPos.y += (mAnimationTarget.y - mAnimationSource.y) * t;
            updateCurlPos(mPointerPos);
        }
    }

    // =========================================================
    // CurlRenderer.Observer — PAGE SIZE / SURFACE
    // =========================================================

    @Override
    public void onPageSizeChanged(int width, int height) {
        mPageBitmapWidth  = width;
        mPageBitmapHeight = height;
        updatePages();
        requestRender();
    }

    @Override
    public void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        requestRender();
        if (mSizeChangedObserver != null) {
            mSizeChangedObserver.onSizeChanged(w, h);
        }
    }

    @Override
    public void onSurfaceCreated() {
        mPageLeft.resetTexture();
        mPageRight.resetTexture();
        mPageCurl.resetTexture();
    }

    // =========================================================
    // TOUCH
    // =========================================================

    @Override
    public boolean onTouch(View view, MotionEvent me) {
        if (mAnimate || mPageProvider == null) {
            return false;
        }

        RectF rightRect = mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT);
        RectF leftRect  = mRenderer.getPageRect(CurlRenderer.PAGE_LEFT);

        mPointerPos.mPos.set(me.getX(), me.getY());
        mRenderer.translate(mPointerPos.mPos);
        if (mEnableTouchPressure) {
            mPointerPos.mPressure = me.getPressure();
        } else {
            mPointerPos.mPressure = 0.8f;
        }

        switch (me.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                mDragStartPos.set(mPointerPos.mPos);

                if (mDragStartPos.y > rightRect.top) {
                    mDragStartPos.y = rightRect.top;
                } else if (mDragStartPos.y < rightRect.bottom) {
                    mDragStartPos.y = rightRect.bottom;
                }

                if (mViewMode == SHOW_TWO_PAGES) {
                    if (mDragStartPos.x < rightRect.left && mCurrentIndex > 0) {
                        mDragStartPos.x = leftRect.left;
                        startCurl(CURL_LEFT);
                    } else if (mDragStartPos.x >= rightRect.left
                            && mCurrentIndex < mPageProvider.getPageCount()) {
                        mDragStartPos.x = rightRect.right;
                        if (!mAllowLastPageCurl
                                && mCurrentIndex >= mPageProvider.getPageCount() - 1) {
                            return false;
                        }
                        startCurl(CURL_RIGHT);
                    }
                } else if (mViewMode == SHOW_ONE_PAGE) {
                    float halfX = (rightRect.right + rightRect.left) / 2;
                    if (mDragStartPos.x < halfX && mCurrentIndex > 0) {
                        mDragStartPos.x = rightRect.left;
                        startCurl(CURL_LEFT);
                    } else if (mDragStartPos.x >= halfX
                            && mCurrentIndex < mPageProvider.getPageCount()) {
                        mDragStartPos.x = rightRect.right;
                        if (!mAllowLastPageCurl
                                && mCurrentIndex >= mPageProvider.getPageCount() - 1) {
                            return false;
                        }
                        startCurl(CURL_RIGHT);
                    }
                }

                if (mCurlState == CURL_NONE) {
                    return false;
                }
                // Fall through to MOVE to trigger first render.
            }
            case MotionEvent.ACTION_MOVE: {
                updateCurlPos(mPointerPos);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (mCurlState == CURL_LEFT || mCurlState == CURL_RIGHT) {
                    mAnimationSource.set(mPointerPos.mPos);
                    mAnimationStartTime = System.currentTimeMillis();

                    if ((mViewMode == SHOW_ONE_PAGE
                            && mPointerPos.mPos.x > (rightRect.left + rightRect.right) / 2)
                            || (mViewMode == SHOW_TWO_PAGES
                            && mPointerPos.mPos.x > rightRect.left)) {
                        mAnimationTarget.set(mDragStartPos);
                        mAnimationTarget.x = mRenderer
                                .getPageRect(CurlRenderer.PAGE_RIGHT).right;
                        mAnimationTargetEvent = SET_CURL_TO_RIGHT;
                    } else {
                        mAnimationTarget.set(mDragStartPos);
                        if (mCurlState == CURL_RIGHT || mViewMode == SHOW_TWO_PAGES) {
                            mAnimationTarget.x = leftRect.left;
                        } else {
                            mAnimationTarget.x = rightRect.left;
                        }
                        mAnimationTargetEvent = SET_CURL_TO_LEFT;
                    }
                    mAnimate = true;
                    requestRender();
                }
                break;
            }
        }

        return true;
    }

    // =========================================================
    // PUBLIC SETTERS
    // =========================================================

    public void setAllowLastPageCurl(boolean allowLastPageCurl) {
        mAllowLastPageCurl = allowLastPageCurl;
    }

    @Override
    public void setBackgroundColor(int color) {
        mRenderer.setBackgroundColor(color);
        requestRender();
    }

    public void setCurrentIndex(int index) {
        if (mPageProvider == null || index < 0) {
            mCurrentIndex = 0;
        } else {
            if (mAllowLastPageCurl) {
                mCurrentIndex = Math.min(index, mPageProvider.getPageCount());
            } else {
                mCurrentIndex = Math.min(index, mPageProvider.getPageCount() - 1);
            }
        }
        updatePages();
        requestRender();
    }

    public void setEnableTouchPressure(boolean enableTouchPressure) {
        mEnableTouchPressure = enableTouchPressure;
    }

    public void setMargins(float left, float top, float right, float bottom) {
        mRenderer.setMargins(left, top, right, bottom);
    }

    /**
     * Register a listener that is called on the UI thread whenever a
     * swipe-driven page flip animation completes and the visible page changes.
     * This is the correct hook for updating page counters, bookmarks, TTS,
     * drawings, and saved position after gesture navigation.
     */
    public void setOnPageSettleListener(OnPageSettleListener listener) {
        mPageSettleListener = listener;
    }

    public void setPageProvider(PageProvider pageProvider) {
        mPageProvider = pageProvider;
        mCurrentIndex = 0;
        updatePages();
        requestRender();
    }

    public void setRenderLeftPage(boolean renderLeftPage) {
        mRenderLeftPage = renderLeftPage;
    }

    public void setSizeChangedObserver(SizeChangedObserver observer) {
        mSizeChangedObserver = observer;
    }

    public void setViewMode(int viewMode) {
        switch (viewMode) {
            case SHOW_ONE_PAGE:
                mViewMode = viewMode;
                mPageLeft.setFlipTexture(true);
                mRenderer.setViewMode(CurlRenderer.SHOW_ONE_PAGE);
                break;
            case SHOW_TWO_PAGES:
                mViewMode = viewMode;
                mPageLeft.setFlipTexture(false);
                mRenderer.setViewMode(CurlRenderer.SHOW_TWO_PAGES);
                break;
        }
    }

    // =========================================================
    // PUBLIC GETTERS
    // =========================================================

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    // =========================================================
    // INTERNAL — CURL POSITION
    // =========================================================

    private void setCurlPos(PointF curlPos, PointF curlDir, double radius) {
        if (mCurlState == CURL_RIGHT
                || (mCurlState == CURL_LEFT && mViewMode == SHOW_ONE_PAGE)) {
            RectF pageRect = mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT);
            if (curlPos.x >= pageRect.right) {
                mPageCurl.reset();
                requestRender();
                return;
            }
            if (curlPos.x < pageRect.left) {
                curlPos.x = pageRect.left;
            }
            if (curlDir.y != 0) {
                float diffX  = curlPos.x - pageRect.left;
                float leftY  = curlPos.y + (diffX * curlDir.x / curlDir.y);
                if (curlDir.y < 0 && leftY < pageRect.top) {
                    curlDir.x = curlPos.y - pageRect.top;
                    curlDir.y = pageRect.left - curlPos.x;
                } else if (curlDir.y > 0 && leftY > pageRect.bottom) {
                    curlDir.x = pageRect.bottom - curlPos.y;
                    curlDir.y = curlPos.x - pageRect.left;
                }
            }
        } else if (mCurlState == CURL_LEFT) {
            RectF pageRect = mRenderer.getPageRect(CurlRenderer.PAGE_LEFT);
            if (curlPos.x <= pageRect.left) {
                mPageCurl.reset();
                requestRender();
                return;
            }
            if (curlPos.x > pageRect.right) {
                curlPos.x = pageRect.right;
            }
            if (curlDir.y != 0) {
                float diffX  = curlPos.x - pageRect.right;
                float rightY = curlPos.y + (diffX * curlDir.x / curlDir.y);
                if (curlDir.y < 0 && rightY < pageRect.top) {
                    curlDir.x = pageRect.top - curlPos.y;
                    curlDir.y = curlPos.x - pageRect.right;
                } else if (curlDir.y > 0 && rightY > pageRect.bottom) {
                    curlDir.x = curlPos.y - pageRect.bottom;
                    curlDir.y = pageRect.right - curlPos.x;
                }
            }
        }

        double dist = Math.sqrt(curlDir.x * curlDir.x + curlDir.y * curlDir.y);
        if (dist != 0) {
            curlDir.x /= dist;
            curlDir.y /= dist;
            mPageCurl.curl(curlPos, curlDir, radius);
        } else {
            mPageCurl.reset();
        }

        requestRender();
    }

    private void startCurl(int page) {
        switch (page) {
            case CURL_RIGHT: {
                mRenderer.removeCurlMesh(mPageLeft);
                mRenderer.removeCurlMesh(mPageRight);
                mRenderer.removeCurlMesh(mPageCurl);

                CurlMesh curl = mPageRight;
                mPageRight = mPageCurl;
                mPageCurl  = curl;

                if (mCurrentIndex > 0) {
                    mPageLeft.setFlipTexture(true);
                    mPageLeft.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_LEFT));
                    mPageLeft.reset();
                    if (mRenderLeftPage) {
                        mRenderer.addCurlMesh(mPageLeft);
                    }
                }
                if (mCurrentIndex < mPageProvider.getPageCount() - 1) {
                    updatePage(mPageRight.getTexturePage(), mCurrentIndex + 1);
                    mPageRight.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT));
                    mPageRight.setFlipTexture(false);
                    mPageRight.reset();
                    mRenderer.addCurlMesh(mPageRight);
                }

                mPageCurl.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT));
                mPageCurl.setFlipTexture(false);
                mPageCurl.reset();
                mRenderer.addCurlMesh(mPageCurl);

                mCurlState = CURL_RIGHT;
                break;
            }
            case CURL_LEFT: {
                mRenderer.removeCurlMesh(mPageLeft);
                mRenderer.removeCurlMesh(mPageRight);
                mRenderer.removeCurlMesh(mPageCurl);

                CurlMesh curl = mPageLeft;
                mPageLeft = mPageCurl;
                mPageCurl = curl;

                if (mCurrentIndex > 1) {
                    updatePage(mPageLeft.getTexturePage(), mCurrentIndex - 2);
                    mPageLeft.setFlipTexture(true);
                    mPageLeft.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_LEFT));
                    mPageLeft.reset();
                    if (mRenderLeftPage) {
                        mRenderer.addCurlMesh(mPageLeft);
                    }
                }

                if (mCurrentIndex < mPageProvider.getPageCount()) {
                    mPageRight.setFlipTexture(false);
                    mPageRight.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT));
                    mPageRight.reset();
                    mRenderer.addCurlMesh(mPageRight);
                }

                if (mViewMode == SHOW_ONE_PAGE
                        || (mCurlState == CURL_LEFT && mViewMode == SHOW_TWO_PAGES)) {
                    mPageCurl.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT));
                    mPageCurl.setFlipTexture(false);
                } else {
                    mPageCurl.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_LEFT));
                    mPageCurl.setFlipTexture(true);
                }
                mPageCurl.reset();
                mRenderer.addCurlMesh(mPageCurl);

                mCurlState = CURL_LEFT;
                break;
            }
        }
    }

    private void updateCurlPos(PointerPosition pointerPos) {
        double radius = mRenderer.getPageRect(CURL_RIGHT).width() / 3;
        radius *= Math.max(1f - pointerPos.mPressure, 0f);
        mCurlPos.set(pointerPos.mPos);

        if (mCurlState == CURL_RIGHT
                || (mCurlState == CURL_LEFT && mViewMode == SHOW_TWO_PAGES)) {
            mCurlDir.x = mCurlPos.x - mDragStartPos.x;
            mCurlDir.y = mCurlPos.y - mDragStartPos.y;
            float dist = (float) Math.sqrt(
                    mCurlDir.x * mCurlDir.x + mCurlDir.y * mCurlDir.y);

            float pageWidth = mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT).width();
            double curlLen = radius * Math.PI;
            if (dist > (pageWidth * 2) - curlLen) {
                curlLen = Math.max((pageWidth * 2) - dist, 0f);
                radius  = curlLen / Math.PI;
            }

            if (dist >= curlLen) {
                double translate = (dist - curlLen) / 2;
                if (mViewMode == SHOW_TWO_PAGES) {
                    mCurlPos.x -= mCurlDir.x * translate / dist;
                } else {
                    float pageLeftX = mRenderer
                            .getPageRect(CurlRenderer.PAGE_RIGHT).left;
                    radius = Math.max(
                            Math.min(mCurlPos.x - pageLeftX, radius), 0f);
                }
                mCurlPos.y -= mCurlDir.y * translate / dist;
            } else {
                double angle     = Math.PI * Math.sqrt(dist / curlLen);
                double translate = radius * Math.sin(angle);
                mCurlPos.x += mCurlDir.x * translate / dist;
                mCurlPos.y += mCurlDir.y * translate / dist;
            }
        } else if (mCurlState == CURL_LEFT) {
            float pageLeftX = mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT).left;
            radius = Math.max(Math.min(mCurlPos.x - pageLeftX, radius), 0f);

            float pageRightX = mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT).right;
            mCurlPos.x  -= Math.min(pageRightX - mCurlPos.x, radius);
            mCurlDir.x   = mCurlPos.x + mDragStartPos.x;
            mCurlDir.y   = mCurlPos.y - mDragStartPos.y;
        }

        setCurlPos(mCurlPos, mCurlDir, radius);
    }

    private void updatePage(CurlPage page, int index) {
        page.reset();
        mPageProvider.updatePage(page, mPageBitmapWidth, mPageBitmapHeight, index);
    }

    private void updatePages() {
        if (mPageProvider == null || mPageBitmapWidth <= 0 || mPageBitmapHeight <= 0) {
            return;
        }

        mRenderer.removeCurlMesh(mPageLeft);
        mRenderer.removeCurlMesh(mPageRight);
        mRenderer.removeCurlMesh(mPageCurl);

        int leftIdx  = mCurrentIndex - 1;
        int rightIdx = mCurrentIndex;
        int curlIdx  = -1;
        if (mCurlState == CURL_LEFT) {
            curlIdx = leftIdx;
            --leftIdx;
        } else if (mCurlState == CURL_RIGHT) {
            curlIdx = rightIdx;
            ++rightIdx;
        }

        if (rightIdx >= 0 && rightIdx < mPageProvider.getPageCount()) {
            updatePage(mPageRight.getTexturePage(), rightIdx);
            mPageRight.setFlipTexture(false);
            mPageRight.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT));
            mPageRight.reset();
            mRenderer.addCurlMesh(mPageRight);
        }
        if (leftIdx >= 0 && leftIdx < mPageProvider.getPageCount()) {
            updatePage(mPageLeft.getTexturePage(), leftIdx);
            mPageLeft.setFlipTexture(true);
            mPageLeft.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_LEFT));
            mPageLeft.reset();
            if (mRenderLeftPage) {
                mRenderer.addCurlMesh(mPageLeft);
            }
        }
        if (curlIdx >= 0 && curlIdx < mPageProvider.getPageCount()) {
            updatePage(mPageCurl.getTexturePage(), curlIdx);
            if (mCurlState == CURL_RIGHT) {
                mPageCurl.setFlipTexture(true);
                mPageCurl.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_RIGHT));
            } else {
                mPageCurl.setFlipTexture(false);
                mPageCurl.setRect(mRenderer.getPageRect(CurlRenderer.PAGE_LEFT));
            }
            mPageCurl.reset();
            mRenderer.addCurlMesh(mPageCurl);
        }
    }

    // =========================================================
    // INTERFACES
    // =========================================================

    /**
     * Provider for feeding 'book' with bitmaps used for rendering pages.
     */
    public interface PageProvider {
        int getPageCount();
        void updatePage(CurlPage page, int width, int height, int index);
    }

    /**
     * Called on the UI thread when a swipe-driven page flip settles on a
     * new page. {@code newIndex} is the authoritative page index after the
     * flip — identical to what {@link #getCurrentIndex()} will return.
     *
     * Wire this into {@link PdfReaderController#reportSettledFromGesture}
     * so that read-aloud, bookmarks, the page counter, drawing strokes, and
     * the saved position all stay in sync after gesture navigation.
     */
    public interface OnPageSettleListener {
        void onPageSettled(int newIndex);
    }

    /**
     * Simple holder for pointer position.
     */
    private class PointerPosition {
        PointF mPos = new PointF();
        float  mPressure;
    }

    /**
     * Observer interface for handling CurlView size changes.
     */
    public interface SizeChangedObserver {
        void onSizeChanged(int width, int height);
    }
}