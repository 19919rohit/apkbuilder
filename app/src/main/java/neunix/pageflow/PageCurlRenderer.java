package neunix.pageflow;

import android.graphics.Bitmap;
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
        void onEnd(int direction);
    }

    private final Callback callback;

    // =========================================================
    // BITMAPS
    // =========================================================

    private Bitmap currentBitmap;
    private Bitmap nextBitmap;

    // =========================================================
    // TEXTURES
    // =========================================================

    private int currentTexture = 0;
    private int nextTexture = 0;

    // =========================================================
    // OPENGL
    // =========================================================

    private int program;

    private int aPosition;
    private int aTexCoord;

    private int uTexture;
    private int uDarkness;

    // =========================================================
    // MESH
    // =========================================================

    private static final int SEGMENTS = 72;

    private final float[] vertices =
            new float[(SEGMENTS + 1) * 4];

    private final float[] texCoords =
            new float[(SEGMENTS + 1) * 4];

    private FloatBuffer vertexBuffer;
    private FloatBuffer texBuffer;

    // =========================================================
    // ANIMATION
    // =========================================================

    private float progress = 0f;

    private boolean animating = false;

    private int direction = 1;

    // =========================================================
    // CURL
    // =========================================================

    private static final float CURL_RADIUS = 0.22f;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public PageCurlRenderer(Callback cb) {

        callback = cb;

        setupBuffers();
    }

    // =========================================================
    // PUBLIC
    // =========================================================

    public void setPages(Bitmap current, Bitmap next) {

        currentBitmap = current;
        nextBitmap = next;

        destroyTextures();

        currentTexture = 0;
        nextTexture = 0;
    }

    public void startFlip(int dir) {

        if (animating) return;

        direction = dir;

        progress = 0f;

        animating = true;
    }

    // =========================================================
    // OPENGL
    // =========================================================

    @Override
    public void onSurfaceCreated(
            GL10 gl,
            EGLConfig config
    ) {

        GLES20.glClearColor(0f, 0f, 0f, 1f);

        GLES20.glEnable(GLES20.GL_BLEND);

        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA
        );

        String vertexShader =

                "attribute vec2 aPosition;" +
                "attribute vec2 aTexCoord;" +

                "varying vec2 vTexCoord;" +

                "void main(){" +

                "gl_Position=vec4(aPosition,0.0,1.0);" +

                "vTexCoord=aTexCoord;" +

                "}";

        String fragmentShader =

                "precision mediump float;" +

                "uniform sampler2D uTexture;" +
                "uniform float uDarkness;" +

                "varying vec2 vTexCoord;" +

                "void main(){" +

                "vec4 c = texture2D(uTexture,vTexCoord);" +

                "c.rgb *= uDarkness;" +

                "gl_FragColor = c;" +

                "}";

        int vs = loadShader(
                GLES20.GL_VERTEX_SHADER,
                vertexShader
        );

        int fs = loadShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentShader
        );

        program = GLES20.glCreateProgram();

        GLES20.glAttachShader(program, vs);

        GLES20.glAttachShader(program, fs);

        GLES20.glLinkProgram(program);

        aPosition =
                GLES20.glGetAttribLocation(
                        program,
                        "aPosition"
                );

        aTexCoord =
                GLES20.glGetAttribLocation(
                        program,
                        "aTexCoord"
                );

        uTexture =
                GLES20.glGetUniformLocation(
                        program,
                        "uTexture"
                );

        uDarkness =
                GLES20.glGetUniformLocation(
                        program,
                        "uDarkness"
                );
    }

    @Override
    public void onSurfaceChanged(
            GL10 gl,
            int width,
            int height
    ) {

        GLES20.glViewport(
                0,
                0,
                width,
                height
        );
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT
        );

        if (currentBitmap == null) return;

        ensureTextures();

        // =====================================================
        // DRAW NEXT PAGE UNDER
        // =====================================================

        if (nextTexture != 0) {

            buildFlatMesh();

            drawMesh(
                    nextTexture,
                    1f
            );
        }

        // =====================================================
        // DRAW CURL PAGE
        // =====================================================

        buildCurlMesh();

        drawMesh(
                currentTexture,
                1f
        );

        // =====================================================
        // DRAW BACKSIDE
        // =====================================================

        if (animating) {

            buildBackMesh();

            drawMesh(
                    currentTexture,
                    0.72f
            );
        }

        // =====================================================
        // ANIMATION
        // =====================================================

        if (animating) {

            progress += 0.018f;

            if (progress >= 1f) {

                progress = 1f;

                animating = false;

                callback.onEnd(direction);
            }
        }
    }

    // =========================================================
    // REAL CURL MESH
    // =========================================================

    private void buildCurlMesh() {

        int vi = 0;
        int ti = 0;

        float curlPos;

        if (direction > 0) {

            curlPos = 1f - progress * 2f;

        } else {

            curlPos = -1f + progress * 2f;
        }

        for (int i = 0; i <= SEGMENTS; i++) {

            float x =
                    -1f + (2f * i / SEGMENTS);

            float u =
                    (float) i / SEGMENTS;

            boolean curled;

            float dist;

            if (direction > 0) {

                curled = x > curlPos;

                dist = x - curlPos;

            } else {

                curled = x < curlPos;

                dist = curlPos - x;
            }

            float vx = x;

            float z = 0f;

            if (curled) {

                float theta =
                        dist / 2f
                                * (float)Math.PI;

                theta =
                        Math.min(
                                theta,
                                (float)Math.PI
                        );

                float arcX =
                        (float)Math.sin(theta)
                                * CURL_RADIUS;

                z =
                        (1f -
                                (float)Math.cos(theta))
                                * 0.25f;

                if (direction > 0) {

                    vx = curlPos + arcX;

                } else {

                    vx = curlPos - arcX;
                }
            }

            // TOP
            vertices[vi++] = vx;
            vertices[vi++] = 1f - z;

            texCoords[ti++] = u;
            texCoords[ti++] = 0f;

            // BOTTOM
            vertices[vi++] = vx;
            vertices[vi++] = -1f + z;

            texCoords[ti++] = u;
            texCoords[ti++] = 1f;
        }

        uploadMesh();
    }

    // =========================================================
    // BACKSIDE
    // =========================================================

    private void buildBackMesh() {

        int vi = 0;
        int ti = 0;

        float curlPos;

        if (direction > 0) {

            curlPos = 1f - progress * 2f;

        } else {

            curlPos = -1f + progress * 2f;
        }

        for (int i = 0; i <= SEGMENTS; i++) {

            float x =
                    -1f + (2f * i / SEGMENTS);

            float u =
                    (float) i / SEGMENTS;

            boolean curled;

            float dist;

            if (direction > 0) {

                curled = x > curlPos;

                dist = x - curlPos;

            } else {

                curled = x < curlPos;

                dist = curlPos - x;
            }

            float vx = x;

            float z = 0f;

            if (curled) {

                float theta =
                        dist / 2f
                                * (float)Math.PI;

                theta =
                        Math.min(
                                theta,
                                (float)Math.PI
                        );

                float arcX =
                        (float)Math.sin(theta)
                                * CURL_RADIUS;

                z =
                        (1f -
                                (float)Math.cos(theta))
                                * 0.28f;

                if (direction > 0) {

                    vx = curlPos + arcX;

                } else {

                    vx = curlPos - arcX;
                }
            }

            // mirror UV backside
            float backU = 1f - u;

            // TOP
            vertices[vi++] = vx;
            vertices[vi++] = 1f - z;

            texCoords[ti++] = backU;
            texCoords[ti++] = 0f;

            // BOTTOM
            vertices[vi++] = vx;
            vertices[vi++] = -1f + z;

            texCoords[ti++] = backU;
            texCoords[ti++] = 1f;
        }

        uploadMesh();
    }

    // =========================================================
    // FLAT PAGE
    // =========================================================

    private void buildFlatMesh() {

        int vi = 0;
        int ti = 0;

        for (int i = 0; i <= SEGMENTS; i++) {

            float x =
                    -1f + (2f * i / SEGMENTS);

            float u =
                    (float) i / SEGMENTS;

            // TOP
            vertices[vi++] = x;
            vertices[vi++] = 1f;

            texCoords[ti++] = u;
            texCoords[ti++] = 0f;

            // BOTTOM
            vertices[vi++] = x;
            vertices[vi++] = -1f;

            texCoords[ti++] = u;
            texCoords[ti++] = 1f;
        }

        uploadMesh();
    }

    // =========================================================
    // DRAW
    // =========================================================

    private void drawMesh(
            int texture,
            float darkness
    ) {

        GLES20.glUseProgram(program);

        GLES20.glEnableVertexAttribArray(
                aPosition
        );

        GLES20.glVertexAttribPointer(
                aPosition,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
        );

        GLES20.glEnableVertexAttribArray(
                aTexCoord
        );

        GLES20.glVertexAttribPointer(
                aTexCoord,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                texBuffer
        );

        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                texture
        );

        GLES20.glUniform1i(
                uTexture,
                0
        );

        GLES20.glUniform1f(
                uDarkness,
                darkness
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                0,
                (SEGMENTS + 1) * 2
        );

        GLES20.glDisableVertexAttribArray(
                aPosition
        );

        GLES20.glDisableVertexAttribArray(
                aTexCoord
        );
    }

    // =========================================================
    // BUFFERS
    // =========================================================

    private void setupBuffers() {

        vertexBuffer =
                ByteBuffer.allocateDirect(
                        vertices.length * 4
                )
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();

        texBuffer =
                ByteBuffer.allocateDirect(
                        texCoords.length * 4
                )
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
    }

    private void uploadMesh() {

        vertexBuffer.clear();

        vertexBuffer.put(vertices);

        vertexBuffer.position(0);

        texBuffer.clear();

        texBuffer.put(texCoords);

        texBuffer.position(0);
    }

    // =========================================================
    // TEXTURES
    // =========================================================

    private void ensureTextures() {

        if (currentTexture == 0
                && currentBitmap != null) {

            currentTexture =
                    loadTexture(currentBitmap);
        }

        if (nextTexture == 0
                && nextBitmap != null) {

            nextTexture =
                    loadTexture(nextBitmap);
        }
    }

    private int loadTexture(Bitmap bmp) {

        int[] tex = new int[1];

        GLES20.glGenTextures(
                1,
                tex,
                0
        );

        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                tex[0]
        );

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLUtils.texImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                bmp,
                0
        );

        return tex[0];
    }

    private void destroyTextures() {

        if (currentTexture != 0) {

            GLES20.glDeleteTextures(
                    1,
                    new int[]{currentTexture},
                    0
            );

            currentTexture = 0;
        }

        if (nextTexture != 0) {

            GLES20.glDeleteTextures(
                    1,
                    new int[]{nextTexture},
                    0
            );

            nextTexture = 0;
        }
    }

    // =========================================================
    // SHADER
    // =========================================================

    private int loadShader(
            int type,
            String code
    ) {

        int shader =
                GLES20.glCreateShader(type);

        GLES20.glShaderSource(
                shader,
                code
        );

        GLES20.glCompileShader(shader);

        return shader;
    }
}