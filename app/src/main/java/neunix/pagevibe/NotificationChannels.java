package neunix.pagevibe;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class NotificationChannels {

    public static final String DAILY_QUOTE      = "channel_daily_quote";
    public static final String READING_REMINDER = "channel_reading_reminder";
    public static final String CONTINUE_READING = "channel_continue_reading";
    public static final String STREAK           = "channel_streak";
    public static final String ANNOUNCEMENTS    = "channel_announcements";

    public static void ensureCreated(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        create(manager, DAILY_QUOTE, "Daily Quotes",
                "A short motivational line once a day", NotificationManager.IMPORTANCE_LOW);
        create(manager, READING_REMINDER, "Reading Reminders",
                "Gentle nudges around your usual reading time", NotificationManager.IMPORTANCE_DEFAULT);
        create(manager, CONTINUE_READING, "Continue Reading",
                "Reminders about the book you're currently reading", NotificationManager.IMPORTANCE_DEFAULT);
        create(manager, STREAK, "Reading Streak",
                "Keep your daily reading streak alive", NotificationManager.IMPORTANCE_DEFAULT);
        create(manager, ANNOUNCEMENTS, "Announcements",
                "Important PageVibe updates and news", NotificationManager.IMPORTANCE_HIGH);
    }

    private static void create(NotificationManager manager, String id, String name, String desc, int importance) {
        if (manager.getNotificationChannel(id) != null) return;
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(desc);
        manager.createNotificationChannel(channel);
    }
}