package neunix.pageflow;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PageCurlRenderer implements GLSurfaceView.Renderer {

    // =========================================================
    // CALLBACK
    // =========================================================

    public interface Callback {
        void onFlipEnd(int direction);
    }

    private final Callback callback;

    // =========================================================
    // PAGES  (volatile hand-off from UI → GL thread)
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

    private int texCurrent = 0;
    private int texNext    = 0;
    private int texPrev    = 0;
    private int texShadow  = 0;
    private int texSheen   = 0;

    // =========================================================
    // SHADER PROGRAMS
    // =========================================================

    private int progPage;
    private int aPos_page, aTex_page;
    private int uTex_page, uDark_page, uSpec_page, uSpecPos_page;

    private int progShadow;
    private int aPos_shad, aTex_shad;
    private int uTex_shad;

    // =========================================================
    // MESH
    // =========================================================

    private static final int SEG = 120;

    private final float[] verts = new float[(SEG + 1) * 4];
    private final float[] uvs   = new float[(SEG + 1) * 4];

    private FloatBuffer vBuf;
    private FloatBuffer uBuf;

    // =========================================================
    // ANIMATION STATE  (GL thread only)
    // =========================================================

    private boolean animating    = false;
    private float   progress     = 0f;
    private int     direction    = 1;
    private long    lastNs       = 0L;
    private float   speedScale   = 1f;

    private boolean snappingBack = false;

    private boolean dragActive   = false;
    private float   dragProgress = 0f;
    private int     dragDir      = 1;

    // =========================================================
    // CURL PHYSICS CONSTANTS
    // =========================================================

    private static final float CURL_RADIUS_MAX  = 0.28f;
    private static final float CURL_RADIUS_MIN  = 0.04f;
    private static final float SPINE_TILT       = 0.06f;
    private static final float Z_LIFT_SCALE     = 0.22f;
    private static final float BASE_DURATION_MS = 370f;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public PageCurlRenderer(Callback cb) {
        callback = cb;
        allocateBuffers();
    }

    // =========================================================
    // PUBLIC API  (UI thread)
    // =========================================================

    public void setPages(Bitmap current, Bitmap next, Bitmap prev) {
        pendingCurrent = current;
        pendingNext    = next;
        pendingPrev    = prev;
        pagesDirty     = true;
    }

    public void startFlip(int dir, float speed) {
        if (animating || dragActive) return;
        direction    = dir;
        progress     = 0f;
        speedScale   = Math.max(0.4f, Math.min(speed, 2.0f));
        snappingBack = false;
        lastNs       = 0L;
        animating    = true;
    }

    public void onDragStart(int dir) {
        if (animating) return;
        dragDir      = dir;
        dragProgress = 0f;
        dragActive   = true;
        snappingBack = false;
    }

    public void setDragProgress(int dir, float p) {
        dragDir      = dir;
        dragProgress = clamp(p, 0f, 1f);
    }

    public void commitDrag(boolean commit, float velocityDp) {
        dragActive = false;

        if (commit) {
            direction    = dragDir;
            progress     = dragProgress;
            speedScale   = Math.max(0.6f, Math.min(velocityDp / 800f + 0.6f, 2.0f));
            snappingBack = false;
            lastNs       = 0L;
            animating    = true;
        } else {
            direction    = dragDir;
            progress     = dragProgress;
            speedScale   = Math.max(0.8f, Math.min(velocityDp / 600f + 0.8f, 2.5f));
            snappingBack = true;
            lastNs       = 0L;
            animating    = true;
        }
    }

    // =========================================================
    // RENDERER
    // =========================================================

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.09f, 0.09f, 0.09f, 1f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        buildPageProgram();
        buildShadowProgram();
        buildUtilityTextures();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        // Flush pending bitmap uploads — always on GL thread
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

        // Resolve effective flip state for this frame
        float   effProgress;
        int     effDir;
        boolean isMoving = animating || dragActive;

        if (dragActive) {
            effProgress = dragProgress;
            effDir      = dragDir;
        } else {
            effProgress = progress;
            effDir      = direction;
        }

        // Draw scene
        if (!isMoving) {
            // Still — flat current page only
            drawFlatPage(texCurrent, 1f);

        } else {
            // 1. Destination page underneath
            int underTex = (effDir > 0) ? texNext : texPrev;
            if (underTex != 0) drawFlatPage(underTex, 1f);

            // 2. Soft ambient shadow
            drawAmbientShadow(effProgress, effDir);

            // 3. Curling page front face
            drawCurlPage(effProgress, effDir, texCurrent, false);

            // 4. Contact shadow at curl crease
            drawContactShadow(effProgress, effDir);

            // 5. Page backside
            drawCurlPage(effProgress, effDir, texCurrent, true);
        }

        // Advance animation on GL thread with real delta-time
        if (animating) {
            advanceAnimation();
        }
    }

    // =========================================================
    // DRAW PASSES
    // =========================================================

    private void drawFlatPage(int tex, float brightness) {
        meshFlat();
        usePageProgram(tex, brightness, 0f, 0f);
        drawStrip();
    }

    private void drawCurlPage(float prog, int dir, int tex, boolean backside) {

        float curlRadius = lerpf(CURL_RADIUS_MAX, CURL_RADIUS_MIN, prog);
        float curlPos    = curlPosition(prog, dir);

        int vi = 0, ui = 0;

        for (int i = 0; i <= SEG; i++) {

            float t = (float) i / SEG;
            float x = -1f + 2f * t;
            float u = t;

            float tiltedCurlPos = curlPos
                    + (backside ? -1f : 1f) * SPINE_TILT * (1f - prog);

            boolean inCurl = (dir > 0) ? (x > tiltedCurlPos)
                                       : (x < tiltedCurlPos);

            float dist = (dir > 0) ? (x - tiltedCurlPos)
                                   : (tiltedCurlPos - x);

            float vx      = x;
            float ySquish = 0f;

            if (inCurl) {
                float theta = clamp(
                        dist / (curlRadius * 2f) * (float) Math.PI,
                        0f, (float) Math.PI);

                float arcX = (float) Math.sin(theta) * curlRadius;
                ySquish    = (1f - (float) Math.cos(theta)) * Z_LIFT_SCALE;

                vx = (dir > 0) ? tiltedCurlPos + arcX
                               : tiltedCurlPos - arcX;

                if (backside && theta < Math.PI / 2f) {
                    vx = tiltedCurlPos;
                }
            }

            float finalU = backside ? (1f - u) : u;

            verts[vi++] = vx; verts[vi++] = 1f - ySquish;
            uvs[ui++]   = finalU; uvs[ui++] = 0f;

            verts[vi++] = vx; verts[vi++] = -1f + ySquish;
            uvs[ui++]   = finalU; uvs[ui++] = 1f;
        }

        upload();

        float darkness = backside ? 0.62f : 1f;
        float specPos  = curlPos + (dir > 0
                ? curlRadius * 0.5f : -curlRadius * 0.5f);

        usePageProgram(tex, darkness,
                backside ? 0f : 0.18f,
                specPos);
        drawStrip();
    }

    private void drawAmbientShadow(float prog, int dir) {

        float shadowWidth = lerpf(0f, 0.35f, prog);
        float curlPos     = curlPosition(prog, dir);

        int vi = 0, ui = 0;

        for (int i = 0; i <= SEG; i++) {
            float t = (float) i / SEG;
            float x = (dir > 0)
                    ? curlPos - t * shadowWidth
                    : curlPos + t * shadowWidth;

            verts[vi++] = x; verts[vi++] = 1f;
            uvs[ui++]   = t; uvs[ui++]   = 0f;

            verts[vi++] = x; verts[vi++] = -1f;
            uvs[ui++]   = t; uvs[ui++]   = 1f;
        }

        upload();
        useShadowProgram(texShadow, 0.50f);
        drawStrip();
    }

    private void drawContactShadow(float prog, int dir) {

        float curlPos    = curlPosition(prog, dir);
        float curlRadius = lerpf(CURL_RADIUS_MAX, CURL_RADIUS_MIN, prog);
        float width      = curlRadius * 0.6f;

        int vi = 0, ui = 0;

        for (int i = 0; i <= SEG; i++) {
            float t = (float) i / SEG;
            float x = (dir > 0)
                    ? curlPos - t * width
                    : curlPos + t * width;

            verts[vi++] = x; verts[vi++] = 1f;
            uvs[ui++]   = t; uvs[ui++]   = 0f;

            verts[vi++] = x; verts[vi++] = -1f;
            uvs[ui++]   = t; uvs[ui++]   = 1f;
        }

        upload();
        useShadowProgram(texShadow, 0.72f);
        drawStrip();
    }

    // =========================================================
    // ANIMATION ENGINE
    // =========================================================

    private void advanceAnimation() {

        long nowNs = System.nanoTime();

        if (lastNs != 0L) {
            float deltaMs  = (nowNs - lastNs) / 1_000_000f;
            float duration = BASE_DURATION_MS / speedScale;

            if (snappingBack) {
                float step = deltaMs / (duration * 0.6f);
                progress = Math.max(0f, progress - step);

                if (progress <= 0f) {
                    progress     = 0f;
                    animating    = false;
                    snappingBack = false;
                    lastNs       = 0L;
                }
            } else {
                float linearStep = deltaMs / duration;
                progress = clamp(progress + linearStep, 0f, 1f);

                if (progress >= 1f) {
                    progress  = 1f;
                    animating = false;
                    lastNs    = 0L;
                    callback.onFlipEnd(direction);
                }
            }
        }

        lastNs = nowNs;
    }

    // =========================================================
    // MESH HELPERS
    // =========================================================

    private void meshFlat() {
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
    }

    private void upload() {
        vBuf.clear(); vBuf.put(verts); vBuf.position(0);
        uBuf.clear(); uBuf.put(uvs);   uBuf.position(0);
    }

    private void drawStrip() {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, (SEG + 1) * 2);
    }

    private float curlPosition(float prog, int dir) {
        return (dir > 0) ? 1f - prog * 2f : -1f + prog * 2f;
    }

    // =========================================================
    // SHADER: PAGE
    // =========================================================

    private void usePageProgram(int tex, float darkness,
                                float specStrength, float specPos) {

        GLES20.glUseProgram(progPage);

        GLES20.glEnableVertexAttribArray(aPos_page);
        GLES20.glVertexAttribPointer(aPos_page, 2,
                GLES20.GL_FLOAT, false, 0, vBuf);

        GLES20.glEnableVertexAttribArray(aTex_page);
        GLES20.glVertexAttribPointer(aTex_page, 2,
                GLES20.GL_FLOAT, false, 0, uBuf);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(uTex_page, 0);

        GLES20.glUniform1f(uDark_page,    darkness);
        GLES20.glUniform1f(uSpec_page,    specStrength);
        GLES20.glUniform1f(uSpecPos_page, specPos);
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

        String frag =
            "precision mediump float;" +
            "uniform sampler2D uTex;" +
            "uniform float uDark;" +
            "uniform float uSpec;" +
            "uniform float uSpecPos;" +
            "varying vec2 vTex;" +
            "varying float vX;" +
            "void main(){" +
            "  vec4 c = texture2D(uTex, vTex);" +
            "  c.rgb *= uDark;" +
            "  float d = abs(vX - uSpecPos);" +
            "  float spec = uSpec * exp(-d * d * 180.0);" +
            "  c.rgb += vec3(spec);" +
            "  gl_FragColor = c;" +
            "}";

        progPage      = linkProgram(vert, frag);
        aPos_page     = GLES20.glGetAttribLocation (progPage, "aPos");
        aTex_page     = GLES20.glGetAttribLocation (progPage, "aTex");
        uTex_page     = GLES20.glGetUniformLocation(progPage, "uTex");
        uDark_page    = GLES20.glGetUniformLocation(progPage, "uDark");
        uSpec_page    = GLES20.glGetUniformLocation(progPage, "uSpec");
        uSpecPos_page = GLES20.glGetUniformLocation(progPage, "uSpecPos");
    }

    // =========================================================
    // SHADER: SHADOW
    // =========================================================

    private void useShadowProgram(int tex, float alpha) {

        GLES20.glUseProgram(progShadow);

        GLES20.glEnableVertexAttribArray(aPos_shad);
        GLES20.glVertexAttribPointer(aPos_shad, 2,
                GLES20.GL_FLOAT, false, 0, vBuf);

        GLES20.glEnableVertexAttribArray(aTex_shad);
        GLES20.glVertexAttribPointer(aTex_shad, 2,
                GLES20.GL_FLOAT, false, 0, uBuf);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(uTex_shad, 0);

        int uAlpha = GLES20.glGetUniformLocation(progShadow, "uAlpha");
        GLES20.glUniform1f(uAlpha, alpha);
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
            "  float grad = texture2D(uTex, vTex).a;" +
            "  gl_FragColor = vec4(0.0, 0.0, 0.0, grad * uAlpha);" +
            "}";

        progShadow = linkProgram(vert, frag);
        aPos_shad  = GLES20.glGetAttribLocation (progShadow, "aPos");
        aTex_shad  = GLES20.glGetAttribLocation (progShadow, "aTex");
        uTex_shad  = GLES20.glGetUniformLocation(progShadow, "uTex");
    }

    // =========================================================
    // UTILITY TEXTURES
    // =========================================================

    private void buildUtilityTextures() {
        texShadow = buildGradientTexture(128, true);
        texSheen  = buildGradientTexture(64,  false);
    }

    private int buildGradientTexture(int size, boolean quadratic) {

        Bitmap bmp = Bitmap.createBitmap(size, 1, Bitmap.Config.ARGB_8888);
        Canvas c   = new Canvas(bmp);

        int[]   colors;
        float[] positions;

        if (quadratic) {
            colors    = new int[]  { 0xFF000000, 0x44000000, 0x00000000 };
            positions = new float[]{ 0f, 0.4f, 1f };
        } else {
            colors    = new int[]  { 0x00FFFFFF, 0xAAFFFFFF, 0x00FFFFFF };
            positions = new float[]{ 0f, 0.5f, 1f };
        }

        LinearGradient grad = new LinearGradient(
                0, 0, size, 0, colors, positions, Shader.TileMode.CLAMP);

        Paint p = new Paint();
        p.setShader(grad);
        c.drawRect(0, 0, size, 1, p);

        int tex = uploadBitmapAsTexture(bmp);
        bmp.recycle();
        return tex;
    }

    // =========================================================
    // TEXTURE MANAGEMENT
    // =========================================================

    private void ensurePageTextures() {
        if (texCurrent == 0 && currentBitmap != null)
            texCurrent = uploadBitmapAsTexture(currentBitmap);
        if (texNext == 0 && nextBitmap != null)
            texNext = uploadBitmapAsTexture(nextBitmap);
        if (texPrev == 0 && prevBitmap != null)
            texPrev = uploadBitmapAsTexture(prevBitmap);
    }

    private void destroyPageTextures() {
        texCurrent = deleteTexture(texCurrent);
        texNext    = deleteTexture(texNext);
        texPrev    = deleteTexture(texPrev);
    }

    private int uploadBitmapAsTexture(Bitmap bmp) {
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

    private int deleteTexture(int tex) {
        if (tex != 0)
            GLES20.glDeleteTextures(1, new int[]{ tex }, 0);
        return 0;
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
    }

    // =========================================================
    // MATH UTILS
    // =========================================================

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float lerpf(float a, float b, float t) {
        return a + (b - a) * t;
    }
}