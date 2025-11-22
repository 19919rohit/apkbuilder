package com.flapchat.app.ui.components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import com.flapchat.app.R;

public class TypingIndicatorView extends LinearLayout {

    public TypingIndicatorView(Context context) {
        super(context);
        init(context);
    }

    public TypingIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TypingIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_typing_indicator, this, true);
        setVisibility(GONE);
    }

    public void showTyping() {
        setVisibility(VISIBLE);
    }

    public void hideTyping() {
        setVisibility(GONE);
    }
}