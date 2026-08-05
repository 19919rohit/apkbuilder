package neunix.pagevibe.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/**
 * Central owner of every AlarmManager schedule in the app. All scheduling
 * goes through here so "cancel outdated reminders before setting new
 * ones" and "reschedule after boot / timezone change" stay consistent
 * instead of duplicated logic scattered across receivers.
 */
public class NotificationScheduler {

    private static final int REQ_DAILY_QUOTE   = 5001;
    private static final int REQ_SMART_READING = 5002;
    private static final int REQ_DAILY_EVAL    = 5003;

    /** Call once from MainActivity.onCreate(), and from the boot/timezone receiver. */
    public static void initialize(Context context) {
        NotificationChannels.ensureCreated(context);
        scheduleDailyQuote(context);
        scheduleSmartReadingReminder(context);
        scheduleDailyEvaluation(context);
    }

    public static void scheduleDailyQuote(Context context) {
        scheduleDailyAt(context, DailyQuoteAlarmReceiver.class, REQ_DAILY_QUOTE, 7, 0);
    }

    public static void scheduleSmartReadingReminder(Context context) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        scheduleDailyAt(context, SmartReadingReminderReceiver.class, REQ_SMART_READING,
                prefs.getLearnedHour(), prefs.getLearnedMinute());
    }

    public static void scheduleDailyEvaluation(Context context) {
        // Fixed evening check for continue-reading/streak/inactive — late
        // enough that "did they read today" is a meaningful question.
        scheduleDailyAt(context, DailyEvaluationReceiver.class, REQ_DAILY_EVAL, 20, 30);
    }

    private static void scheduleDailyAt(Context context, Class<?> receiver, int requestCode, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, receiver);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Always cancel any previously scheduled instance first — this is
        // "cancel outdated reminders" in practice: never leave a stale
        // alarm firing at yesterday's learned time alongside a freshly
        // rescheduled one.
        alarmManager.cancel(pendingIntent);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        try {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, pendingIntent);
        } catch (SecurityException ignored) {
            // Defensive only — inexact repeating alarms don't require the
            // exact-alarm permission some OEMs gate on newer Android.
        }
    }
}   