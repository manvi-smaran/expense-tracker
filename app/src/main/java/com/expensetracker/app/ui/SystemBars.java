package com.expensetracker.app.ui;

import android.graphics.Color;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

final class SystemBars {
    private SystemBars() {}

    // Call after setContentView. hideWhenKeyboardShows may be null.
    static void apply(ComponentActivity activity, View root, View hideWhenKeyboardShows) {
        SystemBarStyle light = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT);
        EdgeToEdge.enable(activity, light, light);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            if (hideWhenKeyboardShows != null) {
                boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                hideWhenKeyboardShows.setVisibility(keyboardVisible ? View.GONE : View.VISIBLE);
            }
            return insets;
        });
    }
}