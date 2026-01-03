package com.draw.perfect;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Random;

public class DrawView extends View {

    private Paint paint, textPaint, particlePaint;
    private Path path;
    private int drawColor = Color.BLACK;
    private String targetShape = "Circle";
    private ArrayList<PointF> points = new ArrayList<>();
    private OnDrawCompleteListener listener;
    private Random random = new Random();

    public DrawView(Context context, AttributeSet attrs) {
        super(context, attrs);

        paint = new Paint();
        paint.setColor(drawColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(60f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        particlePaint = new Paint();
        particlePaint.setColor(Color.MAGENTA);
        particlePaint.setStyle(Paint.Style.FILL);

        path = new Path();
    }

    public void setDrawColor(int color) {
        drawColor = color;
        paint.setColor(drawColor);
        invalidate();
    }

    public void setTargetShape(String shape) {
        targetShape = shape;
        points.clear();
        path.reset();
        invalidate();
    }

    public void setOnDrawCompleteListener(OnDrawCompleteListener l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(path, paint);

        // live accuracy
        if (!points.isEmpty()) {
            int accuracy = calculateAccuracy();
            canvas.drawText("Accuracy: " + accuracy + "%", 50, 100, textPaint);
        }

        // simple particle effect
        for (PointF p : points) {
            canvas.drawCircle(p.x, p.y, random.nextInt(5) + 3, particlePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(x, y);
                points.clear();
                points.add(new PointF(x, y));
                break;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(x, y);
                points.add(new PointF(x, y));
                break;
            case MotionEvent.ACTION_UP:
                int accuracy = calculateAccuracy();
                if (listener != null) listener.onDrawComplete(accuracy);
                break;
        }
        invalidate();
        return true;
    }

    private int calculateAccuracy() {
        if (points.size() < 10) return 0;

        float centerX = 0, centerY = 0;
        for (PointF p : points) {
            centerX += p.x;
            centerY += p.y;
        }
        centerX /= points.size();
        centerY /= points.size();

        switch (targetShape) {
            case "Circle":
                float sumDist = 0;
                for (PointF p : points)
                    sumDist += distance(centerX, centerY, p.x, p.y);
                float avgDist = sumDist / points.size();
                float variance = 0;
                for (PointF p : points)
                    variance += Math.pow(distance(centerX, centerY, p.x, p.y) - avgDist, 2);
                variance /= points.size();
                return Math.max(0, 100 - (int) (variance / 10));

            case "Square":
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
                for (PointF p : points) {
                    if (p.x < minX) minX = p.x;
                    if (p.y < minY) minY = p.y;
                    if (p.x > maxX) maxX = p.x;
                    if (p.y > maxY) maxY = p.y;
                }
                float w = maxX - minX, h = maxY - minY;
                return (int) (100 - Math.abs(w - h) / Math.max(w, h) * 50);

            case "Triangle":
                minX = Float.MAX_VALUE; minY = Float.MAX_VALUE; maxX = Float.MIN_VALUE; maxY = Float.MIN_VALUE;
                for (PointF p : points) {
                    if (p.x < minX) minX = p.x;
                    if (p.y < minY) minY = p.y;
                    if (p.x > maxX) maxX = p.x;
                    if (p.y > maxY) maxY = p.y;
                }
                float dx = maxX - minX, dy = maxY - minY;
                return (int) (100 - Math.abs(dx - dy) / Math.max(dx, dy) * 40);

            case "Star":
                float pathLength = 0;
                for (int i = 1; i < points.size(); i++)
                    pathLength += distance(points.get(i - 1).x, points.get(i - 1).y, points.get(i).x, points.get(i).y);
                float bbox = distance(centerX, centerY, points.get(0).x, points.get(0).y) * 2;
                return (int) Math.max(0, 100 - Math.abs(pathLength - bbox * 5));
        }
        return 0;
    }

    private float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    public interface OnDrawCompleteListener {
        void onDrawComplete(int accuracy);
    }
}