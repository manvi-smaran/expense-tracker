package com.expensetracker.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.expensetracker.app.databinding.DialogConfirmBinding;

public final class Dialogs {
    private Dialogs() {}

    // Removes the default dialog frame so our own retro window shows. Call after dialog.show().
    public static void style(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    // Red "are you sure?" window. onYes runs only if the user confirms.
    public static void confirm(Context context, String title, String message, String yesLabel, Runnable onYes) {
        DialogConfirmBinding b = DialogConfirmBinding.inflate(LayoutInflater.from(context));
        AlertDialog dialog = new AlertDialog.Builder(context).setView(b.getRoot()).create();
        b.textTitle.setText(title);
        b.textMessage.setText(message);
        b.btnYes.setText(yesLabel);
        b.btnNo.setOnClickListener(v -> dialog.dismiss());
        b.btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            onYes.run();
        });
        dialog.show();
        style(dialog);
    }
}