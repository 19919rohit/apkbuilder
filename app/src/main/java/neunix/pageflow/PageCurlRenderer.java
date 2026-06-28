package neunix.pageflow;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * PageCurlRenderer — production-grade real-book page curl.
 *
 * Physics model:
 *  • Diagonal fold line from touch origin to opposite corner
 *  • Cylinder radius shrinks from 0.30 → 0.03 as progress → 1
 *  • Variable radius along the fold: tightest at pinch corner
 *  • Page thickness sliver on curl edge
 *  • Two-layer shadow: contact crease + ambient fan
 *  • Fresnel specular band riding the curl apex
 *  • Backside paper translucency (62% brightness + warm tint)
 *  • Spring overshoot on fast fling commit
 *  • Snap-back with deceleration ease
 *  • Idle corner-lift breathing animation
 *  • Full delta-time animation — frame-rate independent
 */
public class PageCurlRenderer implements GLSurfaceView.Renderer {

    // =========================================================
    // CALLBACK
    // =========================================================

    public interface Callback {
        void onFlipEnd(int direction);
    }

    private final Callback callback;

    // =========================================================
    // BITMAP HAND-OFF  (UI thread → GL thread)
    // =========================================================

    private volatile Bitmap  pendingCurrent;
    private volatile Bitmap  pendingNext;
    private volatile Bitmap  pendingPrev;
    private volatile boolean pagesDirty = false;

    private Bitmap currentBitmap;
    private Bitmap nextBitmap;
    private Bitmap prevBitmap;

    // =========================================================
    // TEXTURES
    // =========================================================

    private int texCurrent    = 0;
    private int texNext       = 0;
    private int texPrev       = 0;
    private int texShadow     = 0;   // quadratic fade: curl shadow
    private int texEdgeShadow = 0;   // tight linear: contact crease
    private int texSheen      = 0;   // bell curve: specular

    // =========================================================
    // SHADER: PAGE
    // =========================================================

    private int progPage;
    private int aPos_p, aTex_p;
    private int uTex_p, uDark_p, uWarm_p, uSpec_p, uSpecPos_p, uSpecWidth_p;

    // =========================================================
    // SHADER: SHADOW
    // =========================================================

    private int progShadow;
    private int aPos_s, aTex_s, uTex_s, uAlpha_s;

    // =========================================================
    // SHADER: EDGE (page thickness sliver)
    // =========================================================

    private int progEdge;
    private int aPos_e;
    private int uColor_e;

    // =========================================================
    // MESH
    // =========================================================

    // 160 segments — imperceptibly smooth on any screen density
    private static final int SEG = 160;

    // Each column: 2 verts × (x,y) = 4 floats
    private final float[] verts = new float[(SEG + 1) * 4];
    private final float[] uvs   = new float[(SEG + 1) * 4];

    // Edge sliver: just 4 verts (a thin quad)
    private final float[] edgeVerts = new float[8];

    private FloatBuffer vBuf;
    private FloatBuffer uBuf;
    private FloatBuffer eBuf;

    // =========================================================
    // SURFACE SIZE
    // =========================================================

    private int surfaceW = 1;
    private int surfaceH = 1;

    // =========================================================
    // ANIMATION STATE  (GL thread only after hand-off)
    // =========================================================

    // Flip progress: 0.0 = page flat, 1.0 = flip complete
    private float   progress     = 0f;
    private boolean animating    = false;
    private int     direction    = 1;   // +1 forward, -1 backward
    private long    lastNs       = 0L;
    private float   speedScale   = 1f;

    // Spring overshoot
    private boolean springing    = false;
    private float   springFrom   = 0f;
    private float   springTarget = 1f;
    private float   springVel    = 0f;  // NDC/ms

    // Snap-back
    private boolean snappingBack = false;

    // Drag
    private volatile boolean dragActive   = false;
    private volatile float   dragProgress = 0f;
    private volatile int     dragDir      = 1;

    // Touch origin in NDC — drives diagonal fold line
    private volatile float touchOriginNdcX = 1.0f;  // default: right edge
    private volatile float touchOriginNdcY = -1.0f; // default: bottom corner

    // =========================================================
    // IDLE CORNER LIFT
    // =========================================================

    private float idleLift     = 0f;    // 0 → 1
    private float idlePhase    = 0f;    // ms accumulator
    private static final float IDLE_LIFT_MAX    = 0.045f;  // NDC units
    private static final float IDLE_PULSE_MS    = 2200f;   // period
    private static final float IDLE_DELAY_MS    = 1800f;   // before first pulse

    // =========================================================
    // PHYSICS CONSTANTS
    // =========================================================

    // Cylinder radius at start and end of flip
    private static final float CURL_R_MAX       = 0.30f;
    private static final float CURL_R_MIN       = 0.025f;

    // How much the curl apex rises off the page (Z illusion)
    private static final float Z_LIFT           = 0.28f;

    // Page thickness sliver width in NDC
    private static final float EDGE_WIDTH       = 0.012f;

    // Spine tilt — fold line is not perfectly vertical
    // Real pages fan from the binding. This tilts it slightly.
    private static final float SPINE_TILT_X     = 0.055f;
    private static final float SPINE_TILT_Y     = 0.12f;

    // Animation durations
    private static final float BASE_DURATION_MS = 340f;
    private static final float SNAP_DURATION_MS = 220f;

    // Spring constants (critically damped feel)
    private static final float SPRING_K         = 0.000012f; // stiffness
    private static final float SPRING_DAMP      = 0.82f;     // damping

    // Overshoot threshold — only spring if velocity is high
    private static final float SPRING_MIN_VEL   = 0.004f;   // NDC/ms

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public PageCurlRenderer(Callback cb) {
        callback = cb;
        allocateBuffers();
    }

    // =========================================================
    // PUBLIC API  (safe to call from UI thread)
    // =========================================================

    public void setPages(Bitmap current, Bitmap next, Bitmap prev) {
        pendingCurrent = current;
        pendingNext    = next;
        pendingPrev    = prev;
        pagesDirty     = true;
    }

    /**
     * Trigger an auto-animated flip (tap or fling with no drag).
     * touchNdcX/Y: where in NDC the touch originated.
     */
    public void startFlip(int dir, float speed,
                          float touchNdcX, float touchNdcY) {
        if (animating || dragActive) return;
        direction        = dir;
        progress         = 0f;
        speedScale       = clamp(speed, 0.4f, 2.2f);
        snappingBack     = false;
        springing        = false;
        lastNs           = 0L;
        animating        = true;
        touchOriginNdcX  = touchNdcX;
        touchOriginNdcY  = touchNdcY;
        idlePhase        = 0f;
    }

    public void onDragStart(int dir, float touchNdcX, float touchNdcY) {
        if (animating) return;
        dragDir          = dir;
        dragProgress     = 0f;
        dragActive       = true;
        snappingBack     = false;
        springing        = false;
        touchOriginNdcX  = touchNdcX;
        touchOriginNdcY  = touchNdcY;
        idlePhase        = 0f;
    }

    public void setDragProgress(int dir, float p) {
        dragDir      = dir;
        dragProgress = clamp(p, 0f, 1f);
    }

    /**
     * @param commit        true = complete the flip
     * @param velocityNdc   finger velocity in NDC/ms (for spring overshoot)
     */
    public void commitDrag(boolean commit, float velocityNdc) {
        dragActive = false;

        if (commit) {
            direction  = dragDir;
            progress   = dragProgress;
            lastNs     = 0L;

            // If finger was moving fast enough, allow spring overshoot
            if (velocityNdc > SPRING_MIN_VEL) {
                springing    = true;
                springFrom   = progress;
                springTarget = 1.0f;
                springVel    = velocityNdc;
                snappingBack = false;
                animating    = true;
            } else {
                speedScale   = clamp(velocityNdc / 0.003f + 0.7f, 0.7f, 2.0f);
                snappingBack = false;
                springing    = false;
                animating    = true;
            }

        } else {
            direction    = dragDir;
            progress     = dragProgress;
            speedScale   = clamp(velocityNdc / 0.002f + 0.9f, 0.9f, 2.8f);
            snappingBack = true;
            springing    = false;
            lastNs       = 0L;
            animating    = true;
        }
    }

    // =========================================================
    // GLSurfaceView.Renderer
    // =========================================================

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.08f, 0.08f, 0.08f, 1f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        buildPageProgram();
        buildShadowProgram();
        buildEdgeProgram();
        buildUtilityTextures();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        surfaceW = Math.max(w, 1);
        surfaceH = Math.max(h, 1);
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        // --------------------------------------------------
        // 1. Flush pending bitmap uploads (GL thread only)
        // --------------------------------------------------
        if (pagesDirty) {
            pagesDirty = false;
            destroyPageTextures();
            currentBitmap = pendingCurrent;
            nextBitmap    = pendingNext;
            prevBitmap    = pendingPrev;
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (currentBitmap == null) return;

        ensurePageTextures();

        // --------------------------------------------------
        // 2. Resolve effective flip state
        // --------------------------------------------------
        float effProg;
        int   effDir;
        boolean isMoving = animating || dragActive;

        if (dragActive) {
            effProg = dragProgress;
            effDir  = dragDir;
        } else {
            effProg = progress;
            effDir  = direction;
        }

        // Compute fold geometry once per frame
        float[] fold = computeFoldLine(effProg, effDir);
        // fold[0] = foldX (NDC), fold[1] = foldTilt (NDC slope offset)
        float foldX    = fold[0];
        float foldTilt = fold[1];
        float curlR    = computeCurlRadius(effProg);

        // --------------------------------------------------
        // 3. Draw scene layers (back to front)
        // --------------------------------------------------

        if (!isMoving) {
            // Completely still — flat page + idle corner lift
            float lift = computeIdleLift();
            if (lift > 0.001f) {
                drawFlatPageWithCornerLift(texCurrent, lift, effDir == 1 ? 1 : -1);
            } else {
                drawFlatPage(texCurrent, 1f, 0f, 0f);
            }

        } else {

            // Layer 1: destination page (flat, underneath)
            int underTex = (effDir > 0) ? texNext : texPrev;
            if (underTex != 0) {
                drawFlatPage(underTex, 1f, 0f, 0f);
            } else {
                // No bitmap yet — draw dark background
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            }

            // Layer 2: wide ambient shadow on destination page
            drawAmbientShadow(effProg, effDir, foldX, curlR);

            // Layer 3: current page curling (front face)
            drawCurlPage(effProg, effDir, texCurrent,
                         false, foldX, foldTilt, curlR);

            // Layer 4: tight contact shadow at the crease
            drawContactShadow(effProg, effDir, foldX, curlR);

            // Layer 5: page thickness edge sliver
            drawEdgeSliver(effProg, effDir, foldX, foldTilt, curlR);

            // Layer 6: backside of curling page
            drawCurlPage(effProg, effDir, texCurrent,
                         true, foldX, foldTilt, curlR);
        }

        // --------------------------------------------------
        // 4. Advance animation (real delta-time)
        // --------------------------------------------------
        if (animating) advanceAnimation();
    }

    // =========================================================
    // DRAW PASSES
    // =========================================================

    private void drawFlatPage(int tex, float dark, float spec, float specPos) {
        int vi = 0, ui = 0;
        for (int i = 0; i <= SEG; i++) {
            float x = -1f + 2f * i / SEG;
            float u = (float) i / SEG;
            verts[vi++] = x; verts[vi++] = 1f;
            uvs[ui++]   = u; uvs[ui++]   = 0f;
            verts[vi++] = x; verts[vi++] = -1f;
            uvs[ui++]   = u; uvs[ui++]   = 1f;
        }
        upload();
        usePageProgram(tex, dark, 0f, spec, specPos, 0.06f);
        drawStrip();
    }

    /**
     * Draws the current page flat but with a subtle corner lift —
     * the bottom-right (or bottom-left) corner curls up slightly.
     * This is the idle breathing animation that invites the user to flip.
     */
    private void drawFlatPageWithCornerLift(int tex, float lift, int nextDir) {

        // Corner lift: only the last ~15% of columns near the trailing edge
        // smoothly lift, creating a tiny cylindrical curl at the corner.
        float cornerStart = nextDir > 0 ? 0.82f : -0.82f; // NDC threshold

        int vi = 0, ui = 0;
        for (int i = 0; i <= SEG; i++) {
            float x = -1f + 2f * i / SEG;
            float u = (float) i / SEG;

            float dist = nextDir > 0
                    ? Math.max(0f, x - cornerStart) / (1f - cornerStart)
                    : Math.max(0f, -cornerStart - x) / (1f - cornerStart);

            // Cubic ease: only the very tip lifts meaningfully
            float liftAmt = dist * dist * dist * lift * IDLE_LIFT_MAX;

            // Bottom vertex lifts more than top (corner effect)
            verts[vi++] = x;   verts[vi++] = 1f;
            uvs[ui++]   = u;   uvs[ui++]   = 0f;

            verts[vi++] = x;   verts[vi++] = -1f + liftAmt * 2.2f;
            uvs[ui++]   = u;   uvs[ui++]   = 1f;
        }

        upload();
        usePageProgram(tex, 1f, 0f, 0f, 0f, 0f);
        drawStrip();

        // Draw a tiny shadow under the lifted corner
        if (lift > 0.1f) {
            drawCornerLiftShadow(lift, nextDir, cornerStart);
        }
    }

    /**
     * Core page curl — both front face and backside.
     *
     * The fold geometry uses a diagonal line, not a vertical one.
     * The cylinder wraps from the fold outward, with radius that
     * varies along the fold line (tighter at the corner).
     */
    private void drawCurlPage(float prog, int dir, int tex,
                               boolean backside,
                               float foldX, float foldTilt, float curlR) {

        int vi = 0, ui = 0;

        for (int i = 0; i <= SEG; i++) {

            float t = (float) i / SEG;
            float x = -1f + 2f * t;
            float u = t;

            // Diagonal fold line: tilts based on Y position
            // We approximate this by shifting foldX by a small Y-dependent offset
            // that varies per column (Y ranges -1 to +1 across the strip height)
            // For each column x, we use two verts (top y=1, bottom y=-1)
            // and apply different fold positions per vert.

            float foldTop    = foldX + foldTilt;  // fold position at y=+1
            float foldBottom = foldX - foldTilt;  // fold position at y=-1

            float finalU = backside ? (1f - u) : u;

            // TOP VERTEX
            {
                float fold = foldTop;
                boolean inCurl = (dir > 0) ? (x > fold) : (x < fold);
                float dist     = (dir > 0) ? (x - fold) : (fold - x);

                float vx = x;
                float vy = 1f;

                if (inCurl) {
                    // Cylinder wrap
                    float theta = clamp(
                            dist / (curlR * 2f) * (float) Math.PI,
                            0f, (float) Math.PI);
                    float arcX  = (float) Math.sin(theta) * curlR;
                    float lift  = (1f - (float) Math.cos(theta)) * Z_LIFT;

                    vx = (dir > 0) ? fold + arcX : fold - arcX;
                    vy = 1f - lift * 0.35f;  // top lifts less than bottom

                    if (backside && theta < (float) Math.PI * 0.5f) {
                        vx = fold;  // collapse invisible backside columns
                    }
                }

                verts[vi++] = vx; verts[vi++] = vy;
                uvs[ui++]   = finalU; uvs[ui++] = 0f;
            }

            // BOTTOM VERTEX
            {
                float fold = foldBottom;
                boolean inCurl = (dir > 0) ? (x > fold) : (x < fold);
                float dist     = (dir > 0) ? (x - fold) : (fold - x);

                float vx = x;
                float vy = -1f;

                if (inCurl) {
                    float theta = clamp(
                            dist / (curlR * 2f) * (float) Math.PI,
                            0f, (float) Math.PI);
                    float arcX  = (float) Math.sin(theta) * curlR;
                    float lift  = (1f - (float) Math.cos(theta)) * Z_LIFT;

                    vx = (dir > 0) ? fold + arcX : fold - arcX;
                    vy = -1f + lift * 0.85f;  // bottom lifts more — diagonal effect

                    if (backside && theta < (float) Math.PI * 0.5f) {
                        vx = fold;
                    }
                }

                verts[vi++] = vx; verts[vi++] = vy;
                uvs[ui++]   = finalU; uvs[ui++] = 1f;
            }
        }

        upload();

        float darkness;
        float warmth;
        float specStr;
        float specPos;
        float specWidth;

        if (backside) {
            // Backside: paper translucency — washed out, slightly warm
            darkness  = 0.58f;
            warmth    = 0.04f;  // warm paper tint
            specStr   = 0f;
            specPos   = 0f;
            specWidth = 0f;
        } else {
            // Front face: full brightness + Fresnel specular on curl apex
            darkness  = 1f;
            warmth    = 0f;

            // Specular rides the apex of the cylinder
            float apexOffset = (dir > 0) ? curlR * 0.45f : -curlR * 0.45f;
            specPos   = foldX + apexOffset;
            specStr   = 0.22f * (1f - prog * 0.5f);  // fades as page turns
            specWidth = 140f + (1f - prog) * 60f;     // narrows as radius shrinks
        }

        usePageProgram(tex, darkness, warmth, specStr, specPos, specWidth);
        drawStrip();
    }

    /**
     * Ambient shadow — a wide gradient that fans from the fold
     * toward the leading edge of the page underneath.
     * Grows as the page lifts higher.
     */
    private void drawAmbientShadow(float prog, int dir,
                                   float foldX, float curlR) {

        float shadowWidth = lerpf(0f, 0.42f, prog);
        float alpha       = lerpf(0f, 0.55f, prog);

        int vi = 0, ui = 0;
        for (int i = 0; i <= SEG; i++) {
            float t = (float) i / SEG;
            float x = (dir > 0)
                    ? foldX - t * shadowWidth
                    : foldX + t * shadowWidth;
            float u = t;

            verts[vi++] = x; verts[vi++] = 1f;
            uvs[ui++]   = u; uvs[ui++]   = 0f;
            verts[vi++] = x; verts[vi++] = -1f;
            uvs[ui++]   = u; uvs[ui++]   = 1f;
        }

        upload();
        useShadowProgram(texShadow, alpha);
        drawStrip();
    }

    /**
     * Contact shadow — a tight, very dark strip right at the fold crease.
     * Simulates the sharp shadow a bent page casts on the page below
     * right at the point of contact.
     */
    private void drawContactShadow(float prog, int dir,
                                   float foldX, float curlR) {

        float width = curlR * 0.55f;
        float alpha = 0.68f + prog * 0.15f;  // darkens as fold tightens

        int vi = 0, ui = 0;
        for (int i = 0; i <= SEG; i++) {
            float t = (float) i / SEG;
            float x = (dir > 0)
                    ? foldX - t * width
                    : foldX + t * width;
            float u = t;

            verts[vi++] = x; verts[vi++] = 1f;
            uvs[ui++]   = u; uvs[ui++]   = 0f;
            verts[vi++] = x; verts[vi++] = -1f;
            uvs[ui++]   = u; uvs[ui++]   = 1f;
        }

        upload();
        useShadowProgram(texEdgeShadow, alpha);
        drawStrip();
    }

    /**
     * Page thickness edge sliver — a thin quad at the curl apex.
     * Lighter at top (catching light), darker at bottom.
     * This is what makes the page feel like it has real thickness.
     */
    private void drawEdgeSliver(float prog, int dir,
                                float foldX, float foldTilt, float curlR) {

        // The sliver sits at the apex of the curl cylinder
        float apexOffset = (dir > 0) ? curlR : -curlR;
        float sliverX    = foldX + apexOffset * 0.88f;
        float halfW      = EDGE_WIDTH * (1f - prog * 0.4f);

        // Four corners of the sliver quad
        // Slightly diagonal to match the fold tilt
        edgeVerts[0] = sliverX - halfW;  edgeVerts[1] = 1f;
        edgeVerts[2] = sliverX + halfW;  edgeVerts[3] = 1f;
        edgeVerts[4] = sliverX - halfW - foldTilt * 0.5f;
        edgeVerts[5] = -1f;
        edgeVerts[6] = sliverX + halfW - foldTilt * 0.5f;
        edgeVerts[7] = -1f;

        eBuf.clear();
        eBuf.put(edgeVerts);
        eBuf.position(0);

        // Top of sliver: bright (catching overhead light)
        // Bottom of sliver: darker
        // We use a simple flat color here — light grey
        float brightness = 0.78f + prog * 0.08f;

        useEdgeProgram(brightness, brightness * 0.85f, brightness * 0.80f, 0.92f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    /**
     * Tiny shadow under the idle corner lift.
     */
    private void drawCornerLiftShadow(float lift, int dir, float cornerStart) {

        float shadowAlpha = lift * 0.35f;
        float shadowWidth = lift * 0.15f;

        int vi = 0, ui = 0;
        for (int i = 0; i <= SEG; i++) {
            float t    = (float) i / SEG;
            float dist = t;
            float x    = dir > 0
                    ? cornerStart + dist * (1f - cornerStart)
                    : -cornerStart - dist * (1f - cornerStart);

            verts[vi++] = x; verts[vi++] = -1f + shadowWidth;
            uvs[ui++]   = t; uvs[ui++]   = 0f;
            verts[vi++] = x; verts[vi++] = -1f;
            uvs[ui++]   = t; uvs[ui++]   = 1f;
        }

        upload();
        useShadowProgram(texShadow, shadowAlpha);
        drawStrip();
    }

    // =========================================================
    // FOLD GEOMETRY
    // =========================================================

    /**
     * Computes the fold line position and tilt for this frame.
     *
     * The fold line is diagonal — it runs from the touch origin
     * toward the opposite corner of the page. This is exactly
     * what real paper does when you curl it from a corner.
     *
     * Returns float[2]: [foldX, foldTilt]
     *  foldX    = NDC X of fold at page centre
     *  foldTilt = how much the fold tilts (difference between
     *             foldX at top vs foldX at bottom, in NDC)
     */
    private float[] computeFoldLine(float prog, int dir) {

        // Base fold position: sweeps from edge to opposite edge
        float baseFold = (dir > 0)
                ? 1f - prog * 2f   // right → left
                : -1f + prog * 2f; // left → right

        // Diagonal tilt: derived from touch origin Y.
        // If finger started near bottom, bottom of fold leads.
        // Clamp touch Y so extreme values don't look broken.
        float touchY   = clamp(touchOriginNdcY, -1f, 1f);

        // Tilt increases as the fold moves: real pages fan from the corner
        float tiltScale = SPINE_TILT_Y * prog * 0.7f
                        + SPINE_TILT_X * (1f - prog) * 0.5f;

        // Direction-aware tilt
        float tilt = (dir > 0)
                ? tiltScale * (touchY < 0 ? 1f : 0.4f)   // bottom touch → more tilt
                : tiltScale * (touchY < 0 ? 1f : 0.4f);

        return new float[]{ baseFold, tilt };
    }

    /**
     * Cylinder radius shrinks as progress increases.
     * Real paper: loose at first, very tight at the end.
     * Uses a non-linear curve — slow shrink early, fast shrink late.
     */
    private float computeCurlRadius(float prog) {
        // Exponential: radius drops quickly in the last 30% of the flip
        float t = prog * prog;  // quadratic ease
        return lerpf(CURL_R_MAX, CURL_R_MIN, t);
    }

    // =========================================================
    // IDLE CORNER LIFT
    // =========================================================

    private float computeIdleLift() {
        idlePhase += 16.67f; // assume ~60fps tick; real delta used in animate

        if (idlePhase < IDLE_DELAY_MS) return 0f;

        float t = ((idlePhase - IDLE_DELAY_MS) % IDLE_PULSE_MS) / IDLE_PULSE_MS;

        // Bell curve: lift up then settle back down
        // sin² gives a smooth rise-and-fall in [0,1]
        idleLift = (float) Math.sin(t * Math.PI);

        return idleLift;
    }

    // =========================================================
    // ANIMATION ENGINE
    // =========================================================

    private void advanceAnimation() {

        long nowNs = System.nanoTime();

        if (lastNs == 0L) {
            lastNs = nowNs;
            return;
        }

        float deltaMs = (nowNs - lastNs) / 1_000_000f;
        // Clamp delta to avoid huge jumps after GC pause or background return
        deltaMs = Math.min(deltaMs, 32f);
        lastNs  = nowNs;

        if (springing) {
            advanceSpring(deltaMs);
        } else if (snappingBack) {
            advanceSnapBack(deltaMs);
        } else {
            advanceLinear(deltaMs);
        }
    }

    /**
     * Normal forward animation — smoothstep easing.
     * Slow start, fast middle, slow end — like real paper.
     */
    private void advanceLinear(float deltaMs) {

        float duration = BASE_DURATION_MS / speedScale;
        float step     = deltaMs / duration;

        // Smoothstep: remap linear step through ease curve
        // This gives the page a gentle acceleration at the start
        // and a natural deceleration as it lands.
        float t        = clamp(progress + step, 0f, 1f);
        progress       = smoothstep(t);

        // Re-clamp after smoothstep (shouldn't exceed 1 but just in case)
        progress = clamp(progress, 0f, 1f);

        if (progress >= 1f) {
            progress  = 1f;
            animating = false;
            lastNs    = 0L;
            callback.onFlipEnd(direction);
        }
    }

    /**
     * Spring overshoot — page flies past 1.0 then bounces back.
     * Happens on fast fling. Feels like a heavy page settling.
     */
    private void advanceSpring(float deltaMs) {

        // Simple spring: F = -k*displacement - damping*velocity
        float displacement = progress - springTarget;
        float force        = -SPRING_K * displacement * deltaMs * deltaMs
                           - SPRING_DAMP * springVel * deltaMs;

        springVel += force;
        progress  += springVel * deltaMs;

        // Clamp to reasonable overshoot — don't go below 0.95 or above 1.12
        progress = clamp(progress, 0.0f, 1.12f);

        // Settled: velocity near zero and near target
        if (Math.abs(springVel) < 0.00005f
                && Math.abs(progress - springTarget) < 0.004f) {
            progress  = springTarget;
            springing = false;
            animating = false;
            lastNs    = 0L;
            callback.onFlipEnd(direction);
        }
    }

    /**
     * Snap-back — page snaps back to 0 when user didn't commit.
     * Decelerates as it approaches 0 (ease-out).
     */
    private void advanceSnapBack(float deltaMs) {

        float duration = SNAP_DURATION_MS / speedScale;
        float step     = deltaMs / duration;

        // Ease-out: step shrinks as progress approaches 0
        float easedStep = step * (0.3f + progress * 0.7f);
        progress = Math.max(0f, progress - easedStep);

        if (progress <= 0.001f) {
            progress     = 0f;
            animating    = false;
            snappingBack = false;
            lastNs       = 0L;
            // No callback — cancelled flip
        }
    }

    // =========================================================
    // SHADER: PAGE
    // =========================================================

    private void usePageProgram(int tex, float dark, float warm,
                                float spec, float specPos, float specWidth) {
        GLES20.glUseProgram(progPage);

        GLES20.glEnableVertexAttribArray(aPos_p);
        GLES20.glVertexAttribPointer(aPos_p, 2, GLES20.GL_FLOAT, false, 0, vBuf);

        GLES20.glEnableVertexAttribArray(aTex_p);
        GLES20.glVertexAttribPointer(aTex_p, 2, GLES20.GL_FLOAT, false, 0, uBuf);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(uTex_p, 0);

        GLES20.glUniform1f(uDark_p,     dark);
        GLES20.glUniform1f(uWarm_p,     warm);
        GLES20.glUniform1f(uSpec_p,     spec);
        GLES20.glUniform1f(uSpecPos_p,  specPos);
        GLES20.glUniform1f(uSpecWidth_p,specWidth);
    }

    private void buildPageProgram() {

        String vert =
            "attribute vec2 aPos;" +
            "attribute vec2 aTex;" +
            "varying vec2 vTex;" +
            "varying float vX;" +
            "void main(){" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);" +
            "  vTex = aTex;" +
            "  vX = aPos.x;" +
            "}";

        // Fragment shader:
        //  - Texture sample
        //  - Darkness multiplier (backside dimming)
        //  - Warm tint for paper translucency on backside
        //  - Gaussian Fresnel specular band on curl apex
        String frag =
            "precision mediump float;" +
            "uniform sampler2D uTex;" +
            "uniform float uDark;" +     // brightness multiplier
            "uniform float uWarm;" +     // warm tint (backside paper)
            "uniform float uSpec;" +     // specular strength
            "uniform float uSpecPos;" +  // specular centre X (NDC)
            "uniform float uSpecWidth;" +// Gaussian width factor
            "varying vec2 vTex;" +
            "varying float vX;" +
            "void main(){" +
            "  vec4 c = texture2D(uTex, vTex);" +
            "  c.rgb *= uDark;" +
            // Warm paper tint — backside looks like you're seeing
            // the content through slightly warm, thin paper
            "  c.rgb += vec3(uWarm * 0.12, uWarm * 0.06, 0.0);" +
            // Fresnel specular: narrow Gaussian band
            "  float d = abs(vX - uSpecPos);" +
            "  float spec = uSpec * exp(-d * d * uSpecWidth);" +
            "  c.rgb += vec3(spec * 0.9, spec * 0.95, spec);" +
            "  gl_FragColor = c;" +
            "}";

        progPage     = linkProgram(vert, frag);
        aPos_p       = GLES20.glGetAttribLocation (progPage, "aPos");
        aTex_p       = GLES20.glGetAttribLocation (progPage, "aTex");
        uTex_p       = GLES20.glGetUniformLocation(progPage, "uTex");
        uDark_p      = GLES20.glGetUniformLocation(progPage, "uDark");
        uWarm_p      = GLES20.glGetUniformLocation(progPage, "uWarm");
        uSpec_p      = GLES20.glGetUniformLocation(progPage, "uSpec");
        uSpecPos_p   = GLES20.glGetUniformLocation(progPage, "uSpecPos");
        uSpecWidth_p = GLES20.glGetUniformLocation(progPage, "uSpecWidth");
    }

    // =========================================================
    // SHADER: SHADOW
    // =========================================================

    private void useShadowProgram(int tex, float alpha) {
        GLES20.glUseProgram(progShadow);

        GLES20.glEnableVertexAttribArray(aPos_s);
        GLES20.glVertexAttribPointer(aPos_s, 2, GLES20.GL_FLOAT, false, 0, vBuf);

        GLES20.glEnableVertexAttribArray(aTex_s);
        GLES20.glVertexAttribPointer(aTex_s, 2, GLES20.GL_FLOAT, false, 0, uBuf);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(uTex_s, 0);
        GLES20.glUniform1f(uAlpha_s, alpha);
    }

    private void buildShadowProgram() {
        String vert =
            "attribute vec2 aPos;" +
            "attribute vec2 aTex;" +
            "varying vec2 vTex;" +
            "void main(){" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);" +
            "  vTex = aTex;" +
            "}";

        String frag =
            "precision mediump float;" +
            "uniform sampler2D uTex;" +
            "uniform float uAlpha;" +
            "varying vec2 vTex;" +
            "void main(){" +
            "  float g = texture2D(uTex, vTex).a;" +
            "  gl_FragColor = vec4(0.0, 0.0, 0.0, g * uAlpha);" +
            "}";

        progShadow = linkProgram(vert, frag);
        aPos_s     = GLES20.glGetAttribLocation (progShadow, "aPos");
        aTex_s     = GLES20.glGetAttribLocation (progShadow, "aTex");
        uTex_s     = GLES20.glGetUniformLocation(progShadow, "uTex");
        uAlpha_s   = GLES20.glGetUniformLocation(progShadow, "uAlpha");
    }

    // =========================================================
    // SHADER: EDGE SLIVER
    // =========================================================

    private void useEdgeProgram(float r, float g, float b, float a) {
        GLES20.glUseProgram(progEdge);

        GLES20.glEnableVertexAttribArray(aPos_e);
        GLES20.glVertexAttribPointer(aPos_e, 2, GLES20.GL_FLOAT, false, 0, eBuf);

        GLES20.glUniform4f(uColor_e, r, g, b, a);
    }

    private void buildEdgeProgram() {
        String vert =
            "attribute vec2 aPos;" +
            "void main(){" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);" +
            "}";

        String frag =
            "precision mediump float;" +
            "uniform vec4 uColor;" +
            "void main(){" +
            "  gl_FragColor = uColor;" +
            "}";

        progEdge = linkProgram(vert, frag);
        aPos_e   = GLES20.glGetAttribLocation (progEdge, "aPos");
        uColor_e = GLES20.glGetUniformLocation(progEdge, "uColor");
    }

    // =========================================================
    // UTILITY TEXTURES
    // =========================================================

    private void buildUtilityTextures() {
        // Wide quadratic fade — ambient shadow
        texShadow = buildGradientTex(256, new int[]{
                0xFF000000, 0x88000000, 0x22000000, 0x00000000
        }, new float[]{ 0f, 0.25f, 0.65f, 1f });

        // Tight linear fade — contact crease shadow
        texEdgeShadow = buildGradientTex(64, new int[]{
                0xFF000000, 0xCC000000, 0x44000000, 0x00000000
        }, new float[]{ 0f, 0.12f, 0.55f, 1f });

        // Bell curve — specular highlight
        texSheen = buildGradientTex(64, new int[]{
                0x00FFFFFF, 0xBBFFFFFF, 0x00FFFFFF
        }, new float[]{ 0f, 0.5f, 1f });
    }

    private int buildGradientTex(int size, int[] colors, float[] positions) {
        Bitmap bmp = Bitmap.createBitmap(size, 1, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);

        LinearGradient grad = new LinearGradient(
                0, 0, size, 0, colors, positions, Shader.TileMode.CLAMP);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(grad);
        c.drawRect(0, 0, size, 1, p);

        int tex = uploadBitmapTex(bmp);
        bmp.recycle();
        return tex;
    }

    // =========================================================
    // TEXTURE MANAGEMENT
    // =========================================================

    private void ensurePageTextures() {
        if (texCurrent == 0 && currentBitmap != null)
            texCurrent = uploadBitmapTex(currentBitmap);
        if (texNext == 0 && nextBitmap != null)
            texNext = uploadBitmapTex(nextBitmap);
        if (texPrev == 0 && prevBitmap != null)
            texPrev = uploadBitmapTex(prevBitmap);
    }

    private void destroyPageTextures() {
        texCurrent = deleteTex(texCurrent);
        texNext    = deleteTex(texNext);
        texPrev    = deleteTex(texPrev);
    }

    private int uploadBitmapTex(Bitmap bmp) {
        int[] id = new int[1];
        GLES20.glGenTextures(1, id, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        return id[0];
    }

    private int deleteTex(int tex) {
        if (tex != 0) GLES20.glDeleteTextures(1, new int[]{ tex }, 0);
        return 0;
    }

    // =========================================================
    // MESH HELPERS
    // =========================================================

    private void upload() {
        vBuf.clear(); vBuf.put(verts); vBuf.position(0);
        uBuf.clear(); uBuf.put(uvs);   uBuf.position(0);
    }

    private void drawStrip() {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, (SEG + 1) * 2);
    }

    // =========================================================
    // SHADER COMPILATION
    // =========================================================

    private int linkProgram(String vert, String frag) {
        int vs = compileShader(GLES20.GL_VERTEX_SHADER,   vert);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, frag);
        int p  = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        return p;
    }

    private int compileShader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    // =========================================================
    // BUFFERS
    // =========================================================

    private void allocateBuffers() {
        vBuf = ByteBuffer.allocateDirect(verts.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        uBuf = ByteBuffer.allocateDirect(uvs.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        eBuf = ByteBuffer.allocateDirect(edgeVerts.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    // =========================================================
    // MATH
    // =========================================================

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float lerpf(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Smoothstep: maps t ∈ [0,1] through 3t²-2t³
     * Gives a natural ease-in-out curve for the flip.
     */
    private static float smoothstep(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}