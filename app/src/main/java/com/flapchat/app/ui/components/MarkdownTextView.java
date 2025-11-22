package com.flapchat.app.ui.components;

import android.content.Context;
import android.text.Spannable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;
import com.flapchat.app.utils.MarkdownParser;

public class MarkdownTextView extends AppCompatTextView {

    public MarkdownTextView(Context context) {
        super(context);
    }

    public MarkdownTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarkdownTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setMarkdownText(String markdown) {
        Spannable parsed = MarkdownParser.parse(markdown, getContext());
        setText(parsed);
    }
}