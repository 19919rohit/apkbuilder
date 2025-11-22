package com.flapchat.app.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeFormatter {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public static String format(long timestamp) {
        return sdf.format(new Date(timestamp));
    }
}