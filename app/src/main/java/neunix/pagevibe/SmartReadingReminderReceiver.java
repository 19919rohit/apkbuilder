package neunix.pageflow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

public class SmartReadingReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ReadingStatsController stats = new ReadingStatsController(context);
        List<ReadingStatsController.DayEntry> days = stats.getRecentDayEntries();
        boolean readToday = !days.isEmpty() && days.get(0).seconds > 0;

        if (!readToday) {
            NotificationHelper.showReadingReminder(context);
        }
    }
}