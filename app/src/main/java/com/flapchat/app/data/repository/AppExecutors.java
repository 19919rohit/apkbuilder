package com.flapchat.app.data.repository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppExecutors {

    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();

    public static ExecutorService database() {
        return DB_EXECUTOR;
    }
}