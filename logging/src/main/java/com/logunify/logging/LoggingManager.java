package com.logunify.logging;

import android.util.Log;

import com.logunify.logging.event.Event;

public class LoggingManager {
    public static LoggingManager instance;

    public static LoggingManager getInstance(String apiKey) {
        if (instance == null) {
            instance = new LoggingManager();
        }
        return instance;
    }

    public static void init(String apiKey) {
        if (instance == null) {
            instance = new LoggingManager();
        }
    }

    public void log(Event event) {
    }
}
