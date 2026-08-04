package neunix.pagevibe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * "Flappy Page" — a small, fully self-contained offline mini-game shown
 * on the Discover error screen when there's no connection. Single input
 * (tap = upward impulse against constant gravity), obstacles are paired
 * "book stack" rectangles with a gap, moving left at constant speed.
 *
 * Frame-rate independent: uses Choreographer.FrameCallback and computes
 * real delta-time between frames rather than assuming a fixed 16ms tick,
 * so physics stay consistent across devices with different refresh
 * rates instead of running too fast/slow.
 *
 * Theme-aware: applyTheme() recolors every drawn element (background,
 * obstacle fill, player, text) from the currently active AppTheme —
 * call it once when the view becomes visible and again any time the
 * active theme changes.
 */
public class GameView extends View implements Choreographer.FrameCallback {

    private static final String PREFS_NAME = "pagevibe_prefs";
    private static final String KEY_HIGH_SCORE = "discover_game_high_score";

    // Physics constants (in dp/sec units, converted to px at draw time
    // via density so the game feels consistent across screen sizes).
    private static final float GRAVITY_DP        = 900f;   // px/s^2 equivalent in dp
    private static final float FLAP_IMPULSE_DP   = -320f;  // instantaneous velocity change on tap
    private static final float SCROLL_SPEED_DP   = 140f;   // px/s obstacles move left
    private static final float OBSTACLE_GAP_DP   = 150f;
    private static final float OBSTACLE_WIDTH_DP = 46f;
    private static final float SPAWN_INTERVAL_S  = 1.6f;
    private static final float PLAYER_SIZE_DP    = 22f;
    private static final float PLAYER_X_DP       = 70f;

    private final Paint bgPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint obstaclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playerPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groundPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static class Obstacle {
        float x;
        float gapCenterY;
        boolean scored = false;
    }

    private final List<Obstacle> obstacles = new ArrayList<>();
    private final Random random = new Random();

    private boolean running = false;
    private boolean gameOver = false;
    private boolean started = false; // waiting for first tap

    private float density;
    private float playerY;
    private float playerVelocity;
    private float timeSinceLastSpawn;
    private int score;
    private int highScore;
    private long lastFrameTimeNanos = 0L;

    public GameView(Context context) { super(context); init(); }
    public GameView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public GameView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        highScore = loadHighScore();
        resetGameState();
    }

    /** Call once on becoming visible, and again whenever the active
     *  theme changes — recolors every drawn element live. */
    public void applyTheme(ThemeManager.AppTheme theme) {
        bgPaint.setColor(theme.backgroundColor);
        obstaclePaint.setColor(theme.cardColor);
        playerPaint.setColor(theme.accentColor);
        textPaint.setColor(theme.textPrimaryColor);
        groundPaint.setColor(theme.dividerColor);
        invalidate();
    }

    private void resetGameState() {
        obstacles.clear();
        playerY = 0f; // set properly on first layout pass in onSizeChanged
        playerVelocity = 0f;
        timeSinceLastSpawn = 0f;
        score = 0;
        gameOver = false;
        started = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        playerY = h / 2f;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopLoop();
    }

    public void startLoop() {
        if (running) return;
        running = true;
        lastFrameTimeNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void stopLoop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;

        if (lastFrameTimeNanos == 0L) lastFrameTimeNanos = frameTimeNanos;
        float deltaSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f;
        deltaSeconds = Math.min(deltaSeconds, 0.05f); // clamp against a huge gap (e.g. resumed after backgrounding)
        lastFrameTimeNanos = frameTimeNanos;

        if (started && !gameOver) {
            updatePhysics(deltaSeconds);
        }

        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void updatePhysics(float dt) {
        int h = getHeight();
        if (h <= 0) return;

        playerVelocity += GRAVITY_DP * density * dt;
        playerY += playerVelocity * dt;

        float playerSizePx = PLAYER_SIZE_DP * density;
        if (playerY - playerSizePx / 2f < 0) {
            playerY = playerSizePx / 2f;
            playerVelocity = 0f;
        }
        if (playerY + playerSizePx / 2f > h) {
            triggerGameOver();
            return;
        }

        float scrollPx = SCROLL_SPEED_DP * density * dt;
        float obstacleWidthPx = OBSTACLE_WIDTH_DP * density;
        float gapPx = OBSTACLE_GAP_DP * density;
        float playerXPx = PLAYER_X_DP * density;

        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle o = it.next();
            o.x -= scrollPx;

            if (!o.scored && o.x + obstacleWidthPx < playerXPx) {
                o.scored = true;
                score++;
            }

            if (o.x + obstacleWidthPx < -10) {
                it.remove();
                continue;
            }

            boolean overlapsX = playerXPx + playerSizePx / 2f > o.x
                    && playerXPx - playerSizePx / 2f < o.x + obstacleWidthPx;
            if (overlapsX) {
                boolean insideGap = playerY - playerSizePx / 2f > o.gapCenterY - gapPx / 2f
                        && playerY + playerSizePx / 2f < o.gapCenterY + gapPx / 2f;
                if (!insideGap) {
                    triggerGameOver();
                    return;
                }
            }
        }

        timeSinceLastSpawn += dt;
        if (timeSinceLastSpawn >= SPAWN_INTERVAL_S) {
            timeSinceLastSpawn = 0f;
            spawnObstacle();
        }
    }

    private void spawnObstacle() {
        int h = getHeight();
        if (h <= 0) return;
        float gapPx = OBSTACLE_GAP_DP * density;
        float margin = gapPx; // keep the gap fully on-screen with room to spare
        float minCenter = margin;
        float maxCenter = h - margin;
        if (maxCenter <= minCenter) return;

        Obstacle o = new Obstacle();
        o.x = getWidth();
        o.gapCenterY = minCenter + random.nextFloat() * (maxCenter - minCenter);
        obstacles.add(o);
    }

    private void triggerGameOver() {
        gameOver = true;
        if (score > highScore) {
            highScore = score;
            saveHighScore(highScore);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;

        if (gameOver) {
            resetGameState();
            playerY = getHeight() / 2f;
            return true;
        }

        started = true;
        playerVelocity = FLAP_IMPULSE_DP * density;
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawRect(0, 0, w, h, bgPaint);

        float obstacleWidthPx = OBSTACLE_WIDTH_DP * density;
        float gapPx = OBSTACLE_GAP_DP * density;
        for (Obstacle o : obstacles) {
            float topBottom = o.gapCenterY - gapPx / 2f;
            float bottomTop = o.gapCenterY + gapPx / 2f;
            canvas.drawRoundRect(new RectF(o.x, 0, o.x + obstacleWidthPx, topBottom), 8f, 8f, obstaclePaint);
            canvas.drawRoundRect(new RectF(o.x, bottomTop, o.x + obstacleWidthPx, h), 8f, 8f, obstaclePaint);
        }

        // Ground line
        canvas.drawRect(0, h - 3f, w, h, groundPaint);

        // Player — a small paper-airplane triangle, rotated slightly by
        // current velocity for a bit of visual life without any real
        // added complexity.
        float playerXPx = PLAYER_X_DP * density;
        float playerSizePx = PLAYER_SIZE_DP * density;
        canvas.save();
        canvas.translate(playerXPx, playerY);
        float rotationDeg = Math.max(-25f, Math.min(45f, playerVelocity / (density * 12f)));
        canvas.rotate(rotationDeg);
        Path plane = new Path();
        plane.moveTo(playerSizePx / 2f, 0);
        plane.lineTo(-playerSizePx / 2f, -playerSizePx / 2.6f);
        plane.lineTo(-playerSizePx / 4f, 0);
        plane.lineTo(-playerSizePx / 2f, playerSizePx / 2.6f);
        plane.close();
        canvas.drawPath(plane, playerPaint);
        canvas.restore();

        textPaint.setTextSize(28f * density);
        if (!started) {
            canvas.drawText("Tap to fly", w / 2f, h / 2f - 40 * density, textPaint);
            textPaint.setTextSize(13f * density);
            canvas.drawText("while you're offline", w / 2f, h / 2f, textPaint);
        } else {
            canvas.drawText(String.valueOf(score), w / 2f, 50 * density, textPaint);
        }

        if (gameOver) {
            textPaint.setTextSize(22f * density);
            canvas.drawText("Game Over", w / 2f, h / 2f - 20 * density, textPaint);
            textPaint.setTextSize(14f * density);
            canvas.drawText("Score " + score + "  ·  Best " + highScore, w / 2f, h / 2f + 12 * density, textPaint);
            textPaint.setTextSize(12f * density);
            canvas.drawText("Tap to try again", w / 2f, h / 2f + 40 * density, textPaint);
        }
    }

    private int loadHighScore() {
        return getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_HIGH_SCORE, 0);
    }

    private void saveHighScore(int value) {
        getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_HIGH_SCORE, value).apply();
    }
}