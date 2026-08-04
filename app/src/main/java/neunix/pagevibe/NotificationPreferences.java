package neunix.pagevibe.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Central store for every notification-related preference and piece of
 * derived state: per-category on/off toggles, anti-repeat history per
 * message pool, the learned "smart reading time", per-category cooldown
 * timestamps (so the same category never fires twice too close
 * together), and the last time the app was opened (drives the inactive-
 * reader tiers). One small SharedPreferences file, trivial to inspect
 * or reset.
 */
public class NotificationPreferences {

    private static final String PREFS_NAME = "pagevibe_notifications";

    public static final String CAT_DAILY_QUOTE      = "cat_daily_quote";
    public static final String CAT_READING_REMINDER = "cat_reading_reminder";
    public static final String CAT_CONTINUE_READING = "cat_continue_reading";
    public static final String CAT_STREAK           = "cat_streak";
    public static final String CAT_ANNOUNCEMENTS    = "cat_announcements";

    private static final String KEY_LEARNED_HOUR   = "learned_hour";
    private static final String KEY_LEARNED_MINUTE = "learned_minute";
    private static final String KEY_LAST_APP_OPEN  = "last_app_open_ts";
    private static final String KEY_FCM_TOKEN      = "fcm_token";

    private static final int ANTI_REPEAT_HISTORY_SIZE = 40;

    private final Context context;
    
    private static final String KEY_FCM_SUBSCRIBED = "fcm_topic_all_subscribed";

    public boolean isFcmTopicSubscribed() {
        return prefs().getBoolean(KEY_FCM_SUBSCRIBED, false);
    }

    public void setFcmTopicSubscribed(boolean subscribed) {
        prefs().edit().putBoolean(KEY_FCM_SUBSCRIBED, subscribed).apply();
    }

    public NotificationPreferences(Context context) {
        this.context = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isCategoryEnabled(String category) {
        return prefs().getBoolean(category, true);
    }

    public void setCategoryEnabled(String category, boolean enabled) {
        prefs().edit().putBoolean(category, enabled).apply();
    }

    public long getLastNotifiedAt(String category) {
        return prefs().getLong("last_notified_" + category, 0L);
    }

    public void setLastNotifiedAt(String category, long timestamp) {
        prefs().edit().putLong("last_notified_" + category, timestamp).apply();
    }

    public void setLearnedTime(int hour, int minute) {
        prefs().edit().putInt(KEY_LEARNED_HOUR, hour).putInt(KEY_LEARNED_MINUTE, minute).apply();
    }

    /** Default 8 PM until enough real sessions exist to learn a real pattern. */
    public int getLearnedHour()   { return prefs().getInt(KEY_LEARNED_HOUR, 20); }
    public int getLearnedMinute() { return prefs().getInt(KEY_LEARNED_MINUTE, 0); }

    public long getLastAppOpenTimestamp() {
        return prefs().getLong(KEY_LAST_APP_OPEN, System.currentTimeMillis());
    }

    public void recordAppOpenedNow() {
        prefs().edit().putLong(KEY_LAST_APP_OPEN, System.currentTimeMillis()).apply();
    }

    /** No backend wired up yet — stored locally so a future server sync
     *  step has somewhere to read the current FCM token from. */
    public void setFcmToken(String token) { prefs().edit().putString(KEY_FCM_TOKEN, token).apply(); }
    public String getFcmToken() { return prefs().getString(KEY_FCM_TOKEN, null); }

    // =========================================================
    // GENERIC ANTI-REPEAT HISTORY — used by both the quote pool and the
    // template combinator below, keyed by pool name.
    // =========================================================

    public List<Integer> getRecentIndices(String poolName) {
        String raw = prefs().getString("history_" + poolName, "");
        List<Integer> result = new ArrayList<>();
        if (raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            try { result.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    public void pushRecentIndex(String poolName, int index) {
        Deque<Integer> history = new ArrayDeque<>(getRecentIndices(poolName));
        history.addLast(index);
        while (history.size() > ANTI_REPEAT_HISTORY_SIZE) history.removeFirst();

        StringBuilder sb = new StringBuilder();
        for (Integer i : history) {
            if (sb.length() > 0) sb.append(',');
            sb.append(i);
        }
        prefs().edit().putString("history_" + poolName, sb.toString()).apply();
    }
}