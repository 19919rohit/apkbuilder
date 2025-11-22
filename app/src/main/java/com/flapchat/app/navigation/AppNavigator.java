package com.flapchat.app.navigation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.flapchat.app.model.Chat;
import com.flapchat.app.model.Message;
import com.flapchat.app.model.User;
import com.flapchat.app.ui.activities.ChatActivity;
import com.flapchat.app.ui.activities.ProfileActivity;
import com.flapchat.app.ui.activities.PreviewMediaActivity;
import com.flapchat.app.ui.activities.ViewImageActivity;
import com.flapchat.app.ui.activities.SettingsActivity;

public class AppNavigator {

    private final Context context;

    public AppNavigator(Context context) {
        this.context = context;
    }

    // --------------------------------------------------
    // Open Chat Screen
    // --------------------------------------------------
    public void openChat(Chat chat, User otherUser) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("chat", chat);
        intent.putExtra("otherUser", otherUser);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
        applySlideAnimation();
    }

    // --------------------------------------------------
    // Open Profile Screen
    // --------------------------------------------------
    public void openProfile(User user) {
        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtra("user", user);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        applySlideAnimation();
    }

    // --------------------------------------------------
    // Open Settings Screen
    // --------------------------------------------------
    public void openSettings() {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        applySlideAnimation();
    }

    // --------------------------------------------------
    // Open Full Image Viewer
    // --------------------------------------------------
    public void openImageViewer(String imageUrl) {
        Intent intent = new Intent(context, ViewImageActivity.class);
        intent.putExtra("imageUrl", imageUrl);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        applyZoomAnimation();
    }

    // --------------------------------------------------
    // Open Media Preview (before sending)
    // --------------------------------------------------
    public void openMediaPreview(String filePath, String type) {
        Intent intent = new Intent(context, PreviewMediaActivity.class);
        intent.putExtra("filePath", filePath);
        intent.putExtra("type", type);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
        applyBottomUpAnimation();
    }

    // --------------------------------------------------
    // Open message reply scroll-to-message
    // --------------------------------------------------
    public void openMessageReply(Message message) {
        // ChatActivity will receive this
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("scrollToMessage", message.getId());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
        applySlideAnimation();
    }

    // --------------------------------------------------
    // Animations
    // --------------------------------------------------
    private void applySlideAnimation() {
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right
            );
        }
    }

    private void applyBottomUpAnimation() {
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );
        }
    }

    private void applyZoomAnimation() {
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );
        }
    }

    // --------------------------------------------------
    // Close current screen
    // --------------------------------------------------
    public void goBack() {
        if (context instanceof Activity) {
            ((Activity) context).finish();
            ((Activity) context).overridePendingTransition(
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right
            );
        }
    }
}