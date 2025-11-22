package com.flapchat.app.utils;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

public class MarkdownParser {

    public static Spannable parseMarkdown(String text) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);

        // Example: bold for **text**
        int index = text.indexOf("**");
        while (index != -1) {
            int endIndex = text.indexOf("**", index + 2);
            if (endIndex != -1) {
                ssb.setSpan(new StyleSpan(Typeface.BOLD), index, endIndex + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = text.indexOf("**", endIndex + 2);
            } else {
                break;
            }
        }

        // Can add more rules for headers, italics, code blocks etc.
        return ssb;
    }
}