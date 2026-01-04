package com.draw.perfect;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

public class DrawView extends View {

    private Paint drawPaint;
    private Paint textPaint;
    private Paint particlePaint;

    private Path drawPath;
    private ArrayList<PointF> points;

    private String targetShape = "Circle";
    private int drawColor = Color.BLACK;

    private OnDrawCompleteListener listener;
    private final Random random = new Random();

    public DrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        points = new ArrayList<>();

        drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeWidth(8f);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setColor(drawColor);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(42f);
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);
        particlePaint.setColor(Color.LTGRAY);

        drawPath = new Path();
    }

    /* =======================
       PUBLIC API
       ======================= */

    public void setTargetShape(String shape) {
        this.targetShape = shape;
        reset();
    }

    public void setDrawColor(int color) {
        this.drawColor = color;
        drawPaint.setColor(color);
        invalidate();
    }

    public void setOnDrawCompleteListener(OnDrawCompleteListener l) {
        this.listener = l;
    }

    public void reset() {
        drawPath.reset();
        points.clear();
        invalidate();
    }

    /* =======================
       DRAW
       ======================= */

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw user path
        canvas.drawPath(drawPath, drawPaint);

        // Live accuracy preview
        if (points.size() > 10) {
            int accuracy = calculateAccuracy();
            canvas.drawText("Accuracy: " + accuracy + "%", 30, 60, textPaint);
        }

        // Subtle particle effect
        for (PointF p : points) {
            canvas.drawCircle(
                    p.x,
                    p.y,
                    random.nextInt(4) + 2,
                    particlePaint
            );
        }
    }

    /* =======================
       TOUCH HANDLING
       ======================= */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                reset();
                drawPath.moveTo(x, y);
                points.add(new PointF(x, y));
                return true;

            case MotionEvent.ACTION_MOVE:
                drawPath.lineTo(x, y);
                points.add(new PointF(x, y));
                break;

            case MotionEvent.ACTION_UP:
                if (listener != null) {
                    listener.onDrawComplete(calculateAccuracy());
                }
                break;
        }

        invalidate();
        return true;
    }

    /* =======================
       ACCURACY LOGIC
       ======================= */

    private int calculateAccuracy() {
        if (points.size() < 15) return 0;

        float centerX = 0f, centerY = 0f;
        for (PointF p : points) {
            centerX += p.x;
            centerY += p.y;
        }
        centerX /= points.size();
        centerY /= points.size();

        switch (targetShape) {

            case "Circle":
                return circleAccuracy(centerX, centerY);

            case "Square":
                return squareAccuracy();

            case "Triangle":
                return triangleAccuracy();

            case "Star":
                return starAccuracy();

            default:
                return 0;
        }
    }

    private int circleAccuracy(float cx, float cy) {
        float sum = 0f;
        for (PointF p : points)
            sum += distance(cx, cy, p.x, p.y);

        float avg = sum / points.size();

        float variance = 0f;
        for (PointF p : points)
            variance += Math.pow(distance(cx, cy, p.x, p.y) - avg, 2);

        variance /= points.size();

        return clamp(100 - (int) (variance / 12));
    }

    private int squareAccuracy() {
        RectF box = getBoundingBox();
        float diff = Math.abs(box.width() - box.height());
        return clamp(100 - (int) (diff / Math.max(box.width(), box.height()) * 60));
    }

    private int triangleAccuracy() {
        RectF box = getBoundingBox();
        float ratio = box.width() / box.height();
        return clamp(100 - (int) (Math.abs(ratio - 1f) * 80));
    }

    private int starAccuracy() {
        float length = 0f;
        for (int i = 1; i < points.size(); i++)
            length += distance(
                    points.get(i - 1).x,
                    points.get(i - 1).y,
                    points.get(i).x,
                    points.get(i).y
            );

        return clamp(100 - (int) Math.abs(length / 20));
    }

    /* =======================
       HELPERS
       ======================= */

    private RectF getBoundingBox() {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

        for (PointF p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        return new RectF(minX, minY, maxX, maxY);
    }

    private float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    /* =======================
       CALLBACK
       ======================= */

    public interface OnDrawCompleteListener {
        void onDrawComplete(int accuracy);
    }
}