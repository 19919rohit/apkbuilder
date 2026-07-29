package neunix.pagevibe;

import android.app.Application;

/**
 * Custom Application subclass — this runs the instant the process
 * starts, for ANY reason (user opening the app, or the system spinning
 * up the process purely to deliver an FCM message while the app was
 * never launched or was killed). Creating notification channels here,
 * rather than only inside MainActivity.onCreate() or the FCM service's
 * onMessageReceived(), is what guarantees a channel genuinely exists
 * before Android ever attempts to auto-post a notification into it —
 * previously, a message arriving on a fresh install or after a process
 * kill could reference a channel that didn't exist yet, which several
 * OEM Android skins handle by silently dropping the notification instead
 * of falling back sensibly.
 */
public class PageVibeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.ensureCreated(this);
    }
}