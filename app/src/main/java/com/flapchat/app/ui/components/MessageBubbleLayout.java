package com.flapchat.app.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.flapchat.app.R;

public class MessageBubbleLayout extends FrameLayout {

    private TextView messageText;

    public MessageBubbleLayout(Context context) {
        super(context);
        init(context);
    }

    public MessageBubbleLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MessageBubbleLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_message_bubble, this, true);
        messageText = findViewById(R.id.message_text);
    }

    public void setMessage(String text) {
        messageText.setText(text);
    }
}