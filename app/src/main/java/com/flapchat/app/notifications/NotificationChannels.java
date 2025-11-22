package com.flapchat.app.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class NotificationChannels {

    public static final String MESSAGE_CHANNEL_ID = "flapchat_message_channel";
    public static final String MESSAGE_CHANNEL_NAME = "Messages";

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel messageChannel = new NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    MESSAGE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            messageChannel.setDescription("FlapChat message notifications");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(messageChannel);
            }
        }
    }
}