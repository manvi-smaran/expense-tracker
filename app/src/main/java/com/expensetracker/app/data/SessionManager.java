package com.expensetracker.app.data;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.expensetracker.app.ExpenseTrackerApp;
import com.expensetracker.app.model.AuthResponse;
import com.expensetracker.app.ui.AuthActivity;

// Keeps the login token and user name on the phone
public final class SessionManager {
    private SessionManager() {}

    private static SharedPreferences prefs() {
        return ExpenseTrackerApp.context().getSharedPreferences("session", Context.MODE_PRIVATE);
    }

    public static void save(AuthResponse response) {
        prefs().edit()
                .putString("token", response.token)
                .putString("name", response.user.name)
                .putString("email", response.user.email)
                .apply();
    }

    public static String token() {
        return prefs().getString("token", null);
    }

    public static boolean isLoggedIn() {
        return token() != null;
    }

    public static String userName() {
        return prefs().getString("name", "");
    }

    public static void logout() {
        prefs().edit().clear().apply();
    }

    // Token expired or rejected by the server: log out and go back to the login screen
    public static void expire() {
        logout();
        Context context = ExpenseTrackerApp.context();
        Intent intent = new Intent(context, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    public static String userEmail() {
        return prefs().getString("email", "");
    }

    public static void saveName(String name) {
        prefs().edit().putString("name", name).apply();
    }

}
