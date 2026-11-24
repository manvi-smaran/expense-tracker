package com.expensetracker.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.expensetracker.app.R;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Fmt {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);
    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    private Fmt() {}

    public static String money(BigDecimal value) {
        return "$" + MONEY.format(value == null ? BigDecimal.ZERO : value);
    }

    public static String displayDate(LocalDate date) {
        return date.format(DISPLAY_DATE);
    }

    // "2026-10-26" -> "10-26"
    public static String monthDay(String iso) {
        return iso != null && iso.length() >= 10 ? iso.substring(5, 10) : "";
    }

    // "2026-10-26" -> "2026"
    public static String year(String iso) {
        return iso != null && iso.length() >= 4 ? iso.substring(0, 4) : "";
    }

    // "Food" -> "FOOD", "Entertainment" -> "ENTE."
    public static String abbrev(String name) {
        if (name == null || name.isBlank()) {
            return "NONE";
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        return upper.length() > 5 ? upper.substring(0, 4) + "." : upper;
    }

    public static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public static int parseColor(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private static final float SELECTED_STROKE_DP = 3f;
    private static final float PILL_RADIUS_DP = 100f;
    private static final float MIN_BOX_RADIUS_DP = 8f;
    private static final int FAINT_STROKE_ALPHA = 0x40;

    // A soft rounded box for chips, tiles and banners. A thick stroke (>= 3dp) marks a
    // selected state and stays fully opaque; thinner strokes become a faint hairline outline.
    public static GradientDrawable box(Context context, int fill, int stroke, float strokeDp, float radiusDp) {
        boolean selected = strokeDp >= SELECTED_STROKE_DP;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(
                dp(context, selected ? SELECTED_STROKE_DP : 1f),
                selected ? stroke : ColorUtils.setAlphaComponent(stroke, FAINT_STROKE_ALPHA));
        float radius = radiusDp >= 10f ? PILL_RADIUS_DP : Math.max(radiusDp * 2f, MIN_BOX_RADIUS_DP);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    public static int color(Context context, int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    public static int primaryDark(Context context) {
        return color(context, R.color.primary_dark);
    }
    // "50", "50.5" or "50.00" -> BigDecimal with 2 decimals. Anything else (or <= 0) -> null.
    public static BigDecimal parseAmount(String text) {
        try {
            BigDecimal value = new BigDecimal(text.trim());
            return value.signum() <= 0 ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 82.5 -> "82.5%"
    public static String percent(BigDecimal value) {
        return String.format(Locale.US, "%.1f%%", value == null ? BigDecimal.ZERO : value);
    }
}