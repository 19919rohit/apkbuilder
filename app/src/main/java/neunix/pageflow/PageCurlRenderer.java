package neunix.pageflow;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLSurfaceView;

public class PageCurlRenderer implements GLSurfaceView.Renderer {

    public interface Callback {
        void onEnd(int direction);
    }

    private final Callback callback;

    // -----------------------------------
    // PAGE BITMAPS
    // -----------------------------------

    private Bitmap currentBitmap;
    private Bitmap nextBitmap;

    // -----------------------------------
    // OPENGL
    // -----------------------------------

    private int program;

    private int aPosition;
    private int aTexCoord;

    private int uTexture;
    private int uOffset;
    private int uDarkness;

    private int currentTexture = 0;
    private int nextTexture = 0;

    // -----------------------------------
    // ANIMATION
    // -----------------------------------

    private boolean animating = false;

    private int direction = 1;

    private float progress = 0f;

    // 60 FPS speed tuned
    private static final float SPEED = 0.040f;

    // -----------------------------------
    // GEOMETRY
    // -----------------------------------

    private final float[] vertices = {

            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
    };

    private final float[] texCoords = {

            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
    };

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer texBuffer;

    public PageCurlRenderer(Callback cb) {

        callback = cb;

        ByteBuffer vb =
                ByteBuffer.allocateDirect(
                        vertices.length * 4
                );

        vb.order(ByteOrder.nativeOrder());

        vertexBuffer = vb.asFloatBuffer();

        vertexBuffer.put(vertices);

        vertexBuffer.position(0);

        ByteBuffer tb =
                ByteBuffer.allocateDirect(
                        texCoords.length * 4
                );

        tb.order(ByteOrder.nativeOrder());

        texBuffer = tb.asFloatBuffer();

        texBuffer.put(texCoords);

        texBuffer.position(0);
    }

    // -----------------------------------
    // PAGE INPUT
    // -----------------------------------

    public void setPages(
            Bitmap current,
            Bitmap next
    ) {

        currentBitmap = current;

        nextBitmap = next;

        deleteTextures();

        currentTexture = 0;
        nextTexture = 0;
    }

    // -----------------------------------
    // START FLIP
    // -----------------------------------

    public void startFlip(int dir) {

        if (animating) return;

        direction = dir;

        progress = 0f;

        animating = true;
    }

    // -----------------------------------
    // OPENGL INIT
    // -----------------------------------

    @Override
    public void onSurfaceCreated(
            GL10 gl,
            EGLConfig config
    ) {

        GLES20.glClearColor(
                0f,
                0f,
                0f,
                1f
        );

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        GLES20.glEnable(GLES20.GL_BLEND);

        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA
        );

        String vertexShader =

                "attribute vec4 aPosition;" +
                "attribute vec2 aTexCoord;" +

                "uniform float uOffset;" +

                "varying vec2 vTexCoord;" +
                "varying float vShade;" +

                "void main(){" +

                "float x = aPosition.x;" +

                // curl distortion
                "float curve = sin(abs(x) * 1.57) * uOffset * 0.35;" +

                "vec4 pos = aPosition;" +

                "pos.x += uOffset;" +
                "pos.z = curve;" +

                // fake perspective
                "pos.xy *= (1.0 - curve * 0.12);" +

                "gl_Position = pos;" +

                "vTexCoord = aTexCoord;" +

                "vShade = curve;" +

                "}";

        String fragmentShader =

                "precision mediump float;" +

                "uniform sampler2D uTexture;" +
                "uniform float uDarkness;" +

                "varying vec2 vTexCoord;" +
                "varying float vShade;" +

                "void main(){" +

                "vec4 color =" +
                "texture2D(uTexture, vTexCoord);" +

                // premium shadow
                "float shadow =" +
                "(vShade * 0.55) + uDarkness;" +

                "color.rgb -= shadow;" +

                "gl_FragColor = color;" +

                "}";

        int vs = compileShader(
                GLES20.GL_VERTEX_SHADER,
                vertexShader
        );

        int fs = compileShader(
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

        uOffset =
                GLES20.glGetUniformLocation(
                        program,
                        "uOffset"
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

    // -----------------------------------
    // DRAW LOOP
    // -----------------------------------

    @Override
    public void onDrawFrame(GL10 gl) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT
        );

        if (currentBitmap == null) {
            return;
        }

        // lazy texture upload
        ensureTextures();

        // -----------------------------------
        // STATIC PAGE
        // -----------------------------------

        if (!animating) {

            drawPage(
                    currentTexture,
                    0f,
                    0f
            );

            return;
        }

        // -----------------------------------
        // ANIMATION VALUES
        // -----------------------------------

        float t = progress;

        // smooth easing
        float ease =
                (float)(
                        1f -
                        Math.pow(1f - t, 3)
                );

        // -----------------------------------
        // DRAW BACK PAGE
        // -----------------------------------

        if (nextTexture != 0) {

            drawPage(
                    nextTexture,
                    0f,
                    0f
            );
        }

        // -----------------------------------
        // FRONT CURL PAGE
        // -----------------------------------

        float move;

        if (direction > 0) {

            move = -ease * 2.0f;

        } else {

            move = ease * 2.0f;
        }

        float darkness =
                ease * 0.22f;

        drawPage(
                currentTexture,
                move,
                darkness
        );

        // -----------------------------------
        // UPDATE
        // -----------------------------------

        progress += SPEED;

        if (progress >= 1f) {

            progress = 1f;

            animating = false;

            if (callback != null) {
                callback.onEnd(direction);
            }
        }
    }

    // -----------------------------------
    // DRAW SINGLE PAGE
    // -----------------------------------

    private void drawPage(
            int texture,
            float offset,
            float darkness
    ) {

        GLES20.glUseProgram(program);

        GLES20.glEnableVertexAttribArray(aPosition);

        GLES20.glVertexAttribPointer(
                aPosition,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
        );

        GLES20.glEnableVertexAttribArray(aTexCoord);

        GLES20.glVertexAttribPointer(
                aTexCoord,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                texBuffer
        );

        GLES20.glActiveTexture(
                GLES20.GL_TEXTURE0
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
                uOffset,
                offset
        );

        GLES20.glUniform1f(
                uDarkness,
                darkness
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                0,
                4
        );

        GLES20.glDisableVertexAttribArray(
                aPosition
        );

        GLES20.glDisableVertexAttribArray(
                aTexCoord
        );
    }

    // -----------------------------------
    // TEXTURES
    // -----------------------------------

    private void ensureTextures() {

        if (currentTexture == 0
                && currentBitmap != null) {

            currentTexture =
                    createTexture(currentBitmap);
        }

        if (nextTexture == 0
                && nextBitmap != null) {

            nextTexture =
                    createTexture(nextBitmap);
        }
    }

    private int createTexture(Bitmap bmp) {

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

    private void deleteTextures() {

        if (currentTexture != 0) {

            GLES20.glDeleteTextures(
                    1,
                    new int[]{currentTexture},
                    0
            );
        }

        if (nextTexture != 0) {

            GLES20.glDeleteTextures(
                    1,
                    new int[]{nextTexture},
                    0
            );
        }
    }

    // -----------------------------------
    // SHADERS
    // -----------------------------------

    private int compileShader(
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