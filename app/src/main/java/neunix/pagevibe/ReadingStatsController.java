package neunix.pagevibe;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Tracks reading activity: total time spent reading, pages turned, a
 * per-day activity log (used for streaks and the recent-activity chart),
 * and per-document totals (used to surface "most read" books). Persisted
 * as one small JSON blob in SharedPreferences.
 */
public class ReadingStatsController {

    private static final String PREFS_NAME = "pagevibe_prefs";
    private static final String KEY_STATS  = "reading_stats_v1";

    private static final String K_TOTAL_SECONDS   = "totalSeconds";
    private static final String K_TOTAL_PAGES     = "totalPagesTurned";
    private static final String K_PER_DOC_SECONDS = "perDocSeconds";
    private static final String K_DAILY_SECONDS   = "dailySeconds";

    // Hard cap on how many days back the recent-activity chart will ever
    // show, regardless of how long the app has been installed — keeps the
    // chart a fixed, glanceable width rather than growing unbounded.
    private static final int MAX_CHART_DAYS = 7;

    private final Context context;
    private final SimpleDateFormat dayKeyFormat;

    private long   sessionStartMillis = -1L;
    private String sessionDocKey      = null;

    public ReadingStatsController(Context context) {
        this.context = context.getApplicationContext();
        // Fixed, locale-independent day KEY format — used only as a JSON
        // storage key and for date arithmetic, never shown to the user
        // (display labels are built separately, see getRecentDayEntries).
        this.dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        this.dayKeyFormat.setTimeZone(TimeZone.getDefault());
    }

    // =========================================================
    // SESSION TRACKING
    // =========================================================

    public void startSession(Uri documentUri) {
        sessionStartMillis = System.currentTimeMillis();
        sessionDocKey = documentUri != null ? String.valueOf(documentUri.hashCode()) : "unknown";
    }

    public void endSession() {
        if (sessionStartMillis <= 0) return;
        long elapsedSeconds = (System.currentTimeMillis() - sessionStartMillis) / 1000L;
        sessionStartMillis = -1L;
        String docKey = sessionDocKey;
        sessionDocKey = null;

        if (elapsedSeconds < 3 || elapsedSeconds > 12 * 3600L) return;

        try {
            JSONObject stats = loadStats();

            stats.put(K_TOTAL_SECONDS, stats.optLong(K_TOTAL_SECONDS, 0L) + elapsedSeconds);

            JSONObject perDoc = stats.optJSONObject(K_PER_DOC_SECONDS);
            if (perDoc == null) perDoc = new JSONObject();
            if (docKey != null) {
                perDoc.put(docKey, perDoc.optLong(docKey, 0L) + elapsedSeconds);
            }
            stats.put(K_PER_DOC_SECONDS, perDoc);

            JSONObject daily = stats.optJSONObject(K_DAILY_SECONDS);
            if (daily == null) daily = new JSONObject();
            String today = dayKeyFormat.format(new Date());
            daily.put(today, daily.optLong(today, 0L) + elapsedSeconds);
            stats.put(K_DAILY_SECONDS, daily);

            saveStats(stats);
        } catch (JSONException ignored) {
            // Never let a stats-persistence hiccup affect the reading experience.
        }
    }

    public void recordPageTurn() {
        try {
            JSONObject stats = loadStats();
            stats.put(K_TOTAL_PAGES, stats.optLong(K_TOTAL_PAGES, 0L) + 1);
            saveStats(stats);
        } catch (JSONException ignored) {}
    }

    // =========================================================
    // READ-BACK — TOTALS
    // =========================================================

    public long getTotalSeconds() {
        return loadStats().optLong(K_TOTAL_SECONDS, 0L);
    }

    public long getTotalPagesTurned() {
        return loadStats().optLong(K_TOTAL_PAGES, 0L);
    }

    // =========================================================
    // READ-BACK — RECENT ACTIVITY CHART
    //
    // Returns entries ORDERED Today FIRST, then each preceding day, for
    // however many days of real history actually exist — never padded
    // with days before the app's first-ever recorded reading, and never
    // more than MAX_CHART_DAYS. A brand-new install with zero recorded
    // history still returns exactly one entry: Today (0 seconds), so the
    // chart always has something sensible to show instead of an empty
    // gap or six confusing blank days.
    //
    // Day LABELS are never a hardcoded name list: "Today" and
    // "Yesterday" are relative labels, and every day before that is
    // formatted via SimpleDateFormat("EEE", currentLocale) — the actual
    // weekday abbreviation for that date, computed fresh each call and
    // correct in the device's own locale/language.
    // =========================================================

    public static class DayEntry {
        public final String  label;
        public final long    seconds;
        public final boolean isToday;

        public DayEntry(String label, long seconds, boolean isToday) {
            this.label = label;
            this.seconds = seconds;
            this.isToday = isToday;
        }
    }

    public List<DayEntry> getRecentDayEntries() {
        List<DayEntry> result = new ArrayList<>();
        JSONObject stats = loadStats();
        JSONObject daily = stats.optJSONObject(K_DAILY_SECONDS);

        // How many days of REAL history exist, capped at MAX_CHART_DAYS.
        // Starts at 1 (Today always shows, even with zero seconds so far
        // today) and extends backward only as far as actual recorded
        // activity reaches.
        int daysToShow = 1;
        if (daily != null && daily.length() > 0) {
            Calendar today = Calendar.getInstance();
            Calendar probe = Calendar.getInstance();
            for (int offset = 1; offset < MAX_CHART_DAYS; offset++) {
                probe.setTime(today.getTime());
                probe.add(Calendar.DAY_OF_YEAR, -offset);
                String key = dayKeyFormat.format(probe.getTime());
                if (daily.has(key)) {
                    daysToShow = offset + 1;
                }
            }
        }

        Calendar base = Calendar.getInstance();
        for (int offset = 0; offset < daysToShow; offset++) {
            Calendar day = (Calendar) base.clone();
            day.add(Calendar.DAY_OF_YEAR, -offset);
            String key = dayKeyFormat.format(day.getTime());
            long seconds = daily != null ? daily.optLong(key, 0L) : 0L;
            result.add(new DayEntry(labelForOffset(offset, day), seconds, offset == 0));
        }
        return result;
    }

    private String labelForOffset(int offset, Calendar day) {
        if (offset == 0) return "Today";
        if (offset == 1) return "Yesterday";
        SimpleDateFormat weekdayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        return weekdayFormat.format(day.getTime());
    }

    // =========================================================
    // READ-BACK — STREAK
    // =========================================================

    /**
     * Consecutive days, counting back from today, with at least some
     * recorded reading time. A gap of a full day with zero seconds
     * breaks it. Today counts only once time is actually logged today.
     */
    public int getCurrentStreakDays() {
        JSONObject stats = loadStats();
        JSONObject daily = stats.optJSONObject(K_DAILY_SECONDS);
        if (daily == null) return 0;

        Calendar cal = Calendar.getInstance();
        int streak = 0;
        for (int i = 0; i < 3650; i++) {
            String key = dayKeyFormat.format(cal.getTime());
            long seconds = daily.optLong(key, 0L);
            if (seconds <= 0) break;
            streak++;
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    // =========================================================
    // READ-BACK — MOST READ
    // =========================================================

    public String getMostReadDocKey() {
        JSONObject stats = loadStats();
        JSONObject perDoc = stats.optJSONObject(K_PER_DOC_SECONDS);
        if (perDoc == null || perDoc.length() == 0) return null;

        String bestKey = null;
        long bestSeconds = -1L;
        java.util.Iterator<String> keys = perDoc.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            long v = perDoc.optLong(k, 0L);
            if (v > bestSeconds) { bestSeconds = v; bestKey = k; }
        }
        return bestKey;
    }

    public long getSecondsForDocKey(String docKey) {
        if (docKey == null) return 0L;
        JSONObject stats = loadStats();
        JSONObject perDoc = stats.optJSONObject(K_PER_DOC_SECONDS);
        return perDoc != null ? perDoc.optLong(docKey, 0L) : 0L;
    }

    // =========================================================
    // FORMATTING
    // =========================================================

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + "s";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    // =========================================================
    // PERSISTENCE
    // =========================================================

    private JSONObject loadStats() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_STATS, null);
            if (json == null) return new JSONObject();
            return new JSONObject(json);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void saveStats(JSONObject stats) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATS, stats.toString())
                .apply();
    }
}