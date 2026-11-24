package com.expensetracker.app;

import android.app.Application;
import android.content.Context;

public class ExpenseTrackerApp extends Application {
    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
    }

    public static Context context() {
        return appContext;
    }
}