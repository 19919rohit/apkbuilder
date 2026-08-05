package neunix.pagevibe.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Learns the user's typical daily reading start time from real session
 * starts. Keeps a rolling window of the last SAMPLE_LIMIT session-start
 * minute-of-day values and recomputes the median whenever a new sample
 * arrives, storing the result via NotificationPreferences and
 * immediately rescheduling the smart-reading alarm to match — so the
 * reminder genuinely tracks the user's habit over time rather than
 * staying fixed at a default.
 */
public class ReadingPatternLearner {

    private static final String PREFS_NAME = "pagevibe_notifications";
    private static final String KEY_SAMPLES = "reading_time_samples";
    private static final int SAMPLE_LIMIT = 20;

    public static void recordSessionStart(Context context) {
        Context appContext = context.getApplicationContext();
        Calendar now = Calendar.getInstance();
        int minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<Integer> samples = loadSamples(prefs);

        Deque<Integer> deque = new ArrayDeque<>(samples);
        deque.addLast(minuteOfDay);
        while (deque.size() > SAMPLE_LIMIT) deque.removeFirst();

        saveSamples(prefs, new ArrayList<>(deque));
        recomputeLearnedTime(appContext, new ArrayList<>(deque));
    }

    private static void recomputeLearnedTime(Context context, List<Integer> samples) {
        if (samples.isEmpty()) return;
        List<Integer> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int median = sorted.get(sorted.size() / 2);

        // Fire the reminder slightly BEFORE the user's typical time, per
        // the spec's example (usual ~8:45 PM -> remind at 8:35 PM).
        int adjusted = median - 10;
        if (adjusted < 0) adjusted += 24 * 60;
        int hour = adjusted / 60;
        int minute = adjusted % 60;

        new NotificationPreferences(context).setLearnedTime(hour, minute);
        NotificationScheduler.scheduleSmartReadingReminder(context);
    }

    private static List<Integer> loadSamples(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_SAMPLES, "");
        List<Integer> result = new ArrayList<>();
        if (raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            try { result.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private static void saveSamples(SharedPreferences prefs, List<Integer> samples) {
        StringBuilder sb = new StringBuilder();
        for (Integer s : samples) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        prefs.edit().putString(KEY_SAMPLES, sb.toString()).apply();
    }
}   