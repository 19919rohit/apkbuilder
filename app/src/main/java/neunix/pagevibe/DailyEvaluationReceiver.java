package neunix.pagevibe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Runs once daily at a fixed evening time and decides AT MOST ONE of
 * Continue Reading / Streak / Inactive Reader to send — deliberately
 * mutually exclusive per day so users are never double-pinged. Priority:
 * Inactive (if genuinely away) > Continue Reading > Streak, since a
 * long absence is the most useful thing to acknowledge first.
 */
public class DailyEvaluationReceiver extends BroadcastReceiver {

    private static final long MIN_COOLDOWN_MS = 20L * 60 * 60 * 1000; // 20h between same-category pings

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationPreferences prefs = new NotificationPreferences(context);

        long lastOpen = prefs.getLastAppOpenTimestamp();
        long now = System.currentTimeMillis();
        if (isSameLocalDay(lastOpen, now)) return; // opened today — nothing to nudge

        long daysInactive = (now - lastOpen) / (24L * 60 * 60 * 1000);

        if (daysInactive >= 3) {
            long lastInactive = prefs.getLastNotifiedAt("inactive");
            if (now - lastInactive > MIN_COOLDOWN_MS) {
                NotificationHelper.showInactive(context, (int) daysInactive);
                prefs.setLastNotifiedAt("inactive", now);
                return;
            }
        }

        LibraryManager library = new LibraryManager(context);
        List<LibraryManager.Entry> all = library.getAll();
        if (!all.isEmpty()) {
            Collections.sort(all, (a, b) -> Long.compare(b.lastOpenedAt, a.lastOpenedAt));
            LibraryManager.Entry mostRecent = all.get(0);
            long lastContinue = prefs.getLastNotifiedAt(NotificationPreferences.CAT_CONTINUE_READING);
            if (now - lastContinue > MIN_COOLDOWN_MS) {
                SharedPreferences appPrefs = context.getSharedPreferences("pagevibe_prefs", Context.MODE_PRIVATE);
                int lastPage = appPrefs.getInt("last_page_" + mostRecent.uri.hashCode(), 0);
                String title = LibraryManager.displayName(mostRecent);
                Uri uri = mostRecent.uri;
                NotificationHelper.showContinueReading(context, uri, lastPage + 1, title);
                prefs.setLastNotifiedAt(NotificationPreferences.CAT_CONTINUE_READING, now);
                return;
            }
        }

        ReadingStatsController stats = new ReadingStatsController(context);
        int streak = stats.getCurrentStreakDays();
        if (streak >= 1) {
            // Probability increases with a longer streak, per spec — a
            // 1-day streak is a light nudge; a 20-day streak is nearly
            // certain to remind, since more is genuinely at stake.
            double chance = Math.min(0.9, 0.15 + streak * 0.05);
            long lastStreak = prefs.getLastNotifiedAt(NotificationPreferences.CAT_STREAK);
            if (now - lastStreak > MIN_COOLDOWN_MS && new Random().nextDouble() < chance) {
                NotificationHelper.showStreak(context, streak);
                prefs.setLastNotifiedAt(NotificationPreferences.CAT_STREAK, now);
            }
        }
    }

    private boolean isSameLocalDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }
}