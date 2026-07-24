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

import java.util.ArrayList;
import java.util.List;

public class StatsActivity extends AppCompatActivity {

    private ReadingStatsController stats;
    private LibraryManager         libraryManager;

    private final List<ValueAnimator> activeAnimators = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        stats          = new ReadingStatsController(this);
        libraryManager = new LibraryManager(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        bindTopNumbers();
        bindStreak();
        bindWeekChart();
        bindMostRead();
        bindDailyQuote();
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

    /**
     * Two independent visual signals, deliberately not conflated:
     *  - The historical BEST day gets a static amber tint (informational).
     *  - TODAY always gets the animated glow + shimmer, regardless of
     *    whether it happens to also be the best day — this is what
     *    previously made the animation misleadingly look like "today is
     *    always special" for new users, since a brand-new user's only
     *    day of data IS today, so best-day and today were the same thing
     *    every single time. Now the animation means "still counting",
     *    and the amber tint alone means "your best day so far".
     */
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

        final int amberColor = Color.parseColor("#FFC400");
        final int blueColor  = Color.parseColor("#4488FF");
        final int greyColor  = Color.parseColor("#222222");
        final int amberGlow  = Color.parseColor("#77FFC400");
        final int blueGlow   = Color.parseColor("#774488FF");

        for (int i = 0; i < entries.size(); i++) {
            ReadingStatsController.DayEntry entry = entries.get(i);
            boolean isBest  = (i == bestIndex);
            boolean isToday = entry.isToday;

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

            int barColor  = isBest ? amberColor : (entry.seconds > 0 ? blueColor : greyColor);
            int glowColor = isBest ? amberGlow  : blueGlow;

            if (isToday) {
                addBreathingGlow(barSlot, barWidthPx, barHeight, glowExtraPx, glowColor);
            }

            View bar = new View(this);
            FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(barWidthPx, barHeight);
            barLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            bar.setLayoutParams(barLp);

            GradientDrawable barBg = new GradientDrawable();
            barBg.setShape(GradientDrawable.RECTANGLE);
            barBg.setCornerRadius(dpToPx(5));
            barBg.setColor(barColor);
            bar.setBackground(barBg);
            barSlot.addView(bar);

            if (isToday) {
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
     * A soft rounded halo behind a bar that breathes — alpha and scale
     * animated together on one ValueAnimator so they stay perfectly in
     * sync, using an ease-in/ease-out curve so the pulse feels organic.
     * Color is passed in so it can match either the "best day" (amber)
     * or plain "today" (blue) styling.
     */
    private void addBreathingGlow(FrameLayout barSlot, int barWidthPx, int barHeightPx,
                                   int glowExtraPx, int glowColor) {
        View glow = new View(this);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(
                barWidthPx + glowExtraPx * 2, barHeightPx + glowExtraPx);
        glowLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        glow.setLayoutParams(glowLp);

        GradientDrawable glowBg = new GradientDrawable();
        glowBg.setShape(GradientDrawable.RECTANGLE);
        glowBg.setCornerRadius(dpToPx(14));
        glowBg.setColor(glowColor);
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
     * A soft streak of light that travels up through today's bar and
     * fades in/out at both ends via a sine curve — layered on top of the
     * breathing glow for a genuinely lively "still counting" indicator.
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

    /**
     * FIXED: previously looked up the title via the old "recent_files"
     * SharedPreferences key, which stopped being written to once the app
     * moved to LibraryManager as the actual source of truth — so this
     * search always came back empty and silently hid the Most Read card.
     * Now resolves the title through LibraryManager, matching by the
     * same uri.hashCode() key ReadingStatsController already uses.
     */
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
        if (docKey == null || libraryManager == null) return null;
        try {
            for (LibraryManager.Entry entry : libraryManager.getAll()) {
                if (entry.uri == null) continue;
                if (String.valueOf(entry.uri.hashCode()).equals(docKey)) {
                    return LibraryManager.displayName(entry);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void bindDailyQuote() {
        TextView quoteView = findViewById(R.id.dailyQuoteText);
        if (quoteView == null) return;
        quoteView.setText(DailyQuoteProvider.getTodayQuote());
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}