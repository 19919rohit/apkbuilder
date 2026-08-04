package neunix.pagevibe.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DailyQuoteAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.showDailyQuote(context);
    }
}