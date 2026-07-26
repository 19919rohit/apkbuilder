package neunix.pagevibe;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Single gatekeeper for actually showing a notification: every method
 * here checks its category's on/off toggle first, and (for local
 * reminders only — not FCM announcements) whether the reader is
 * currently in the foreground, so a user actively reading is never
 * interrupted mid-session.
 */
public class NotificationHelper {

    /** Set true while PdfActivity is foreground, false otherwise. */
    public static volatile boolean isReaderForeground = false;

    private static final int ID_DAILY_QUOTE      = 1001;
    private static final int ID_READING_REMINDER = 1002;
    private static final int ID_CONTINUE_READING = 1003;
    private static final int ID_STREAK           = 1004;
    private static final int ID_ANNOUNCEMENT     = 1005;

    public static void showDailyQuote(Context context) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        if (!prefs.isCategoryEnabled(NotificationPreferences.CAT_DAILY_QUOTE)) return;
        if (isReaderForeground) return;

        String quote = NotificationQuoteProvider.pickQuote(context);
        show(context, NotificationChannels.DAILY_QUOTE, ID_DAILY_QUOTE, "PageVibe", quote, openAppIntent(context));
        prefs.setLastNotifiedAt(NotificationPreferences.CAT_DAILY_QUOTE, System.currentTimeMillis());
    }

    public static void showReadingReminder(Context context) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        if (!prefs.isCategoryEnabled(NotificationPreferences.CAT_READING_REMINDER)) return;
        if (isReaderForeground) return;

        String message = NotificationTemplates.pickReadingReminder(context);
        show(context, NotificationChannels.READING_REMINDER, ID_READING_REMINDER,
                "Time to read", message, openAppIntent(context));
        prefs.setLastNotifiedAt(NotificationPreferences.CAT_READING_REMINDER, System.currentTimeMillis());
    }

    public static void showContinueReading(Context context, Uri bookUri, int pageOneBased, String bookTitle) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        if (!prefs.isCategoryEnabled(NotificationPreferences.CAT_CONTINUE_READING)) return;
        if (isReaderForeground) return;

        String message = NotificationTemplates.pickContinueReading(context, pageOneBased, bookTitle);
        show(context, NotificationChannels.CONTINUE_READING, ID_CONTINUE_READING,
                "Continue Reading", message, openReaderIntent(context, bookUri));
    }

    public static void showStreak(Context context, int streakDays) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        if (!prefs.isCategoryEnabled(NotificationPreferences.CAT_STREAK)) return;
        if (isReaderForeground) return;

        String message = NotificationTemplates.pickStreak(context, streakDays);
        show(context, NotificationChannels.STREAK, ID_STREAK, "Keep your streak", message, openAppIntent(context));
    }

    public static void showInactive(Context context, int daysInactive) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        if (!prefs.isCategoryEnabled(NotificationPreferences.CAT_READING_REMINDER)) return;
        if (isReaderForeground) return;

        String message = NotificationTemplates.pickInactive(context, daysInactive);
        show(context, NotificationChannels.READING_REMINDER, ID_READING_REMINDER, "PageVibe", message, openAppIntent(context));
    }

    public static void showAnnouncement(Context context, String title, String body) {
        NotificationPreferences prefs = new NotificationPreferences(context);
        if (!prefs.isCategoryEnabled(NotificationPreferences.CAT_ANNOUNCEMENTS)) return;
        // Deliberately does NOT check isReaderForeground — announcements
        // are rare and important by design (major update, security fix),
        // not routine engagement pings.
        show(context, NotificationChannels.ANNOUNCEMENTS, ID_ANNOUNCEMENT,
                title != null ? title : "PageVibe", body != null ? body : "", openAppIntent(context));
    }

    private static void show(Context context, String channel, int id, String title, String body, PendingIntent intent) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(intent);
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted on API 33+ — fail silently,
            // never crash the calling alarm receiver.
        }
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openReaderIntent(Context context, Uri bookUri) {
        if (bookUri == null) return openAppIntent(context);
        Intent intent = new Intent(context, PdfActivity.class);
        intent.setData(bookUri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}