package neunix.pageflow;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Handles Firebase Cloud Messaging — reserved for rare, important
 * announcements only (major updates, critical fixes, security notices),
 * never routine engagement pings. Those all stay local (AlarmManager-
 * driven) so they keep working fully offline regardless of FCM.
 */
public class PageVibeFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        String title = null;
        String body  = null;

        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body  = message.getNotification().getBody();
        }
        if (title == null && message.getData().containsKey("title")) {
            title = message.getData().get("title");
        }
        if (body == null && message.getData().containsKey("body")) {
            body = message.getData().get("body");
        }

        NotificationChannels.ensureCreated(this);
        NotificationHelper.showAnnouncement(this, title, body);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        new NotificationPreferences(this).setFcmToken(token);
    }
}