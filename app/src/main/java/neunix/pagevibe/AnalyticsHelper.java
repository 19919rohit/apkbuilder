package neunix.pagevibe;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * The ONE custom analytics event in the whole app: "notification_opened",
 * fired only when the user actually taps a PageVibe notification and it
 * launches/foregrounds the app. Every other metric (DAU/WAU/MAU, new vs.
 * returning users, session count, engagement time, retention, app/OS
 * version distribution, device models, country, language) comes for free
 * from Firebase Analytics' automatic collection — no code needed for any
 * of that here.
 *
 * HOW ATTRIBUTION WORKS: tagIntent() stamps two short strings (type,
 * source) onto the Intent a notification's PendingIntent will deliver.
 * Tagging alone logs nothing. Only if the user taps the notification —
 * which hands that exact Intent to MainActivity/PdfActivity's
 * onCreate()/onNewIntent() — does logIfTagged() find the tag and log the
 * event, then immediately strips the tag from that SAME Intent object.
 * That strip is what prevents a screen rotation or process-death
 * recreate() (which re-reads the identical Intent via getIntent()) from
 * ever double-logging the same open.
 */
public class AnalyticsHelper {

    private AnalyticsHelper() {}

    // Notification type values — extend freely; nothing else needs to
    // change to add a new category later.
    public static final String TYPE_DAILY_QUOTE     = "daily_quote";
    public static final String TYPE_READING_REMINDER = "reminder";
    public static final String TYPE_CONTINUE_READING = "continue_reading";
    public static final String TYPE_STREAK           = "streak";
    public static final String TYPE_INACTIVE         = "inactive";
    public static final String TYPE_ANNOUNCEMENT     = "announcement";

    public static final String SOURCE_LOCAL = "local";
    public static final String SOURCE_FCM   = "fcm";

    private static final String EXTRA_NOTIF_TYPE   = "pagevibe_notif_type";
    private static final String EXTRA_NOTIF_SOURCE = "pagevibe_notif_source";

    private static final String EVENT_NOTIFICATION_OPENED = "notification_opened";
    private static final String PARAM_NOTIFICATION_TYPE   = "notification_type";
    private static final String PARAM_NOTIFICATION_SOURCE = "notification_source";

    public static void tagIntent(Intent intent, String type, String source) {
        if (intent == null) return;
        intent.putExtra(EXTRA_NOTIF_TYPE, type);
        intent.putExtra(EXTRA_NOTIF_SOURCE, source);
    }

    /** Call from an Activity's onCreate() (and onNewIntent() if the
     *  activity can receive intents while already running). */
    public static void logIfTagged(Context context, Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_NOTIF_TYPE)) return;

        String type   = intent.getStringExtra(EXTRA_NOTIF_TYPE);
        String source  = intent.getStringExtra(EXTRA_NOTIF_SOURCE);

        // Strip immediately, before doing anything else — this is the
        // actual double-log guard, see class doc.
        intent.removeExtra(EXTRA_NOTIF_TYPE);
        intent.removeExtra(EXTRA_NOTIF_SOURCE);

        try {
            Bundle params = new Bundle();
            if (type != null)   params.putString(PARAM_NOTIFICATION_TYPE, type);
            if (source != null) params.putString(PARAM_NOTIFICATION_SOURCE, source);
            FirebaseAnalytics.getInstance(context).logEvent(EVENT_NOTIFICATION_OPENED, params);
        } catch (Throwable ignored) {
            // Analytics must never be able to crash app startup.
        }
    }
}