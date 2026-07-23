package neunix.pagevibe;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StatsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "pagevibe_prefs";
    private static final String KEY_RECENT = "recent_files";

    private ReadingStatsController stats;

    private final List<ValueAnimator> activeAnimators = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        stats = new ReadingStatsController(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        bindTopNumbers();
        bindStreak();
        bindWeekChart();
        bindMostRead();
    }

    @Override
    protected void onDestroy() {
        for (ValueAnimator a : activeAnimators) {
            try { a.cancel(); } catch (Throwable ignored) {}
        }
        activeAnimators.clear();
        super.onDestroy();
    }

    private void bindTopNumbers() {
        long totalSeconds = stats.getTotalSeconds();
        long totalPages   = stats.getTotalPagesTurned();

        ((TextView) findViewById(R.id.statTotalTime))
                .setText(ReadingStatsController.formatDuration(totalSeconds));
        ((TextView) findViewById(R.id.statTotalPages))
                .setText(String.valueOf(totalPages));

        boolean hasAnyData = totalSeconds > 0 || totalPages > 0;
        findViewById(R.id.emptyStatsMessage).setVisibility(hasAnyData ? View.GONE : View.VISIBLE);
    }

    private void bindStreak() {
        int streak = stats.getCurrentStreakDays();
        TextView streakText = findViewById(R.id.statStreak);
        if (streak <= 0) {
            streakText.setText("No active streak");
        } else if (streak == 1) {
            streakText.setText("1 day streak");
        } else {
            streakText.setText(streak + " day streak");
        }
    }

    private void bindWeekChart() {
        LinearLayout chart = findViewById(R.id.weekChart);
        chart.removeAllViews();

        List<ReadingStatsController.DayEntry> entries = stats.getRecentDayEntries();

        long maxSeconds = 1L;
        long bestSeconds = 0L;
        for (ReadingStatsController.DayEntry e : entries) {
            maxSeconds = Math.max(maxSeconds, e.seconds);
            bestSeconds = Math.max(bestSeconds, e.seconds);
        }

        int bestIndex = -1;
        if (bestSeconds > 0) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).seconds == bestSeconds) { bestIndex = i; break; }
            }
        }

        int maxBarHeightPx = dpToPx(80);
        int barWidthPx     = dpToPx(18);
        int glowExtraPx    = dpToPx(12);

        for (int i = 0; i < entries.size(); i++) {
            ReadingStatsController.DayEntry entry = entries.get(i);
            boolean isBest = (i == bestIndex);

            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);

            FrameLayout barSlot = new FrameLayout(this);
            FrameLayout.LayoutParams slotLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, maxBarHeightPx);
            barSlot.setLayoutParams(slotLp);

            float ratio = maxSeconds > 0 ? entry.seconds / (float) maxSeconds : 0f;
            int barHeight = entry.seconds > 0
                    ? Math.max(dpToPx(4), (int) (maxBarHeightPx * ratio))
                    : dpToPx(4);

            if (isBest) {
                addBreathingGlow(barSlot, barWidthPx, barHeight, glowExtraPx);
            }

            View bar = new View(this);
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(barWidthPx, barHeight);
            barLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            bar.setLayoutParams(barLp);

            GradientDrawable barBg = new GradientDrawable();
            barBg.setShape(GradientDrawable.RECTANGLE);
            barBg.setCornerRadius(dpToPx(5));
            if (isBest) {
                barBg.setColor(Color.parseColor("#FFC400"));
            } else if (entry.seconds > 0) {
                barBg.setColor(Color.parseColor("#4488FF"));
            } else {
                barBg.setColor(Color.parseColor("#222222"));
            }
            bar.setBackground(barBg);
            barSlot.addView(bar);

            if (isBest) {
                attachShimmer(barSlot, barWidthPx, barHeight);
            }

            TextView label = new TextView(this);
            label.setText(entry.label);
            label.setTextSize(10f);
            label.setTextColor(entry.isToday ? Color.parseColor("#DDDDDD") : Color.parseColor("#666666"));
            label.setTypeface(null, entry.isToday ? Typeface.BOLD : Typeface.NORMAL);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dpToPx(6);

            column.addView(barSlot);
            column.addView(label, labelLp);
            chart.addView(column, colLp);
        }
    }

    /**
     * A soft rounded halo behind the best-day bar that breathes — alpha
     * and scale animated together on one ValueAnimator so they stay
     * perfectly in sync, using an ease-in/ease-out curve rather than
     * linear so the pulse feels organic instead of mechanical.
     */
    private void addBreathingGlow(FrameLayout barSlot, int barWidthPx, int barHeightPx, int glowExtraPx) {
        View glow = new View(this);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(
                barWidthPx + glowExtraPx * 2, barHeightPx + glowExtraPx);
        glowLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        glow.setLayoutParams(glowLp);

        GradientDrawable glowBg = new GradientDrawable();
        glowBg.setShape(GradientDrawable.RECTANGLE);
        glowBg.setCornerRadius(dpToPx(14));
        glowBg.setColor(Color.parseColor("#77FFC400"));
        glow.setBackground(glowBg);
        barSlot.addView(glow, 0); // behind the bar, which is added next

        ValueAnimator breathe = ValueAnimator.ofFloat(0f, 1f);
        breathe.setDuration(1000);
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.setInterpolator(new AccelerateDecelerateInterpolator());
        breathe.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            glow.setAlpha(0.4f + f * 0.6f);
            float scale = 0.9f + f * 0.22f;
            glow.setScaleX(scale);
            glow.setScaleY(scale);
        });
        breathe.start();
        activeAnimators.add(breathe);
    }

    /**
     * A soft streak of light that travels up through the best-day bar and
     * fades in/out at both ends via a sine curve — layered on top of the
     * breathing glow above for a genuinely lively highlight, not just a
     * static pulse. One small View + one ValueAnimator, cheap enough to
     * run indefinitely for a single bar.
     */
    private void attachShimmer(FrameLayout barSlot, int barWidthPx, int barHeightPx) {
        View shimmer = new View(this);
        int shimmerHeight = dpToPx(10);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(barWidthPx, shimmerHeight);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        shimmer.setLayoutParams(lp);

        GradientDrawable shimmerBg = new GradientDrawable();
        shimmerBg.setShape(GradientDrawable.RECTANGLE);
        shimmerBg.setCornerRadius(dpToPx(5));
        shimmerBg.setColor(Color.WHITE);
        shimmer.setBackground(shimmerBg);
        barSlot.addView(shimmer);

        float travel = Math.max(0, barHeightPx - shimmerHeight);

        ValueAnimator travelAnim = ValueAnimator.ofFloat(0f, 1f);
        travelAnim.setDuration(1600);
        travelAnim.setRepeatMode(ValueAnimator.RESTART);
        travelAnim.setRepeatCount(ValueAnimator.INFINITE);
        travelAnim.setInterpolator(new LinearInterpolator());
        travelAnim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            shimmer.setTranslationY(-travel * f);
            double fade = Math.sin(Math.PI * f);
            shimmer.setAlpha((float) Math.max(0.0, fade) * 0.85f);
        });
        travelAnim.start();
        activeAnimators.add(travelAnim);
    }

    private void bindMostRead() {
        String docKey = stats.getMostReadDocKey();
        if (docKey == null) return;

        String title = lookupTitleForDocKey(docKey);
        long seconds = stats.getSecondsForDocKey(docKey);
        if (title == null || seconds <= 0) return;

        findViewById(R.id.mostReadCard).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.mostReadTitle)).setText(title);
        ((TextView) findViewById(R.id.mostReadTime))
                .setText(ReadingStatsController.formatDuration(seconds) + " total");
    }

    private String lookupTitleForDocKey(String docKey) {
        try {
            String json = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_RECENT, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Uri uri = Uri.parse(obj.getString("uri"));
                if (String.valueOf(uri.hashCode()).equals(docKey)) {
                    String name = obj.getString("name");
                    return name.replaceAll("(?i)\\.pdf$", "").replace("_", " ").trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}