package com.expensetracker.app.ui.categories;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.expensetracker.app.R;
import com.expensetracker.app.ui.Fmt;

import java.util.ArrayList;
import java.util.List;

public final class PalettePicker {
    public static final String[] COLORS = {
            "#FFAEC9", "#6BE0D2", "#C8B6FF", "#FDF982", "#F8C9E6", "#47B8A8"};

    private final Context context;
    private final List<TextView> swatches = new ArrayList<>();
    private int selected = 0;

    public PalettePicker(LinearLayout container) {
        context = container.getContext();
        container.removeAllViews();
        for (int i = 0; i < COLORS.length; i++) {
            final int index = i;
            TextView swatch = new TextView(context);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(Fmt.dp(context, 40), Fmt.dp(context, 40));
            params.setMarginEnd(Fmt.dp(context, 8));
            swatch.setLayoutParams(params);
            swatch.setGravity(Gravity.CENTER);
            swatch.setTextSize(14);
            swatch.setTextColor(Fmt.primaryDark(context));
            swatch.setContentDescription("Colour " + (i + 1));
            swatch.setOnClickListener(v -> select(index));
            swatches.add(swatch);
            container.addView(swatch);
        }
        select(0);
    }

    public void select(int index) {
        selected = index;
        for (int i = 0; i < swatches.size(); i++) {
            TextView swatch = swatches.get(i);
            int color = Fmt.parseColor(COLORS[i], Fmt.color(context, R.color.primary_container));
            boolean on = i == selected;
            swatch.setBackground(Fmt.box(context, color, Fmt.primaryDark(context), on ? 4 : 2, 4));
            swatch.setText(on ? "●" : "");
        }
    }

    // Selects the swatch matching this colour (first one if it isn't in the palette)
    public void select(String hex) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equalsIgnoreCase(hex)) {
                select(i);
                return;
            }
        }
        select(0);
    }

    public String selectedColor() {
        return COLORS[selected];
    }
}