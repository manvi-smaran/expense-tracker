package com.expensetracker.app.ui;

import com.expensetracker.app.R;

public final class CategoryIcons {
    private CategoryIcons() {}

    public static int forKey(String key) {
        if (key == null) {
            return R.drawable.ic_cat_tag;
        }
        switch (key) {
            case "food":
                return R.drawable.ic_cat_food;
            case "transit":
                return R.drawable.ic_cat_transit;
            case "game":
                return R.drawable.ic_cat_game;
            case "bolt":
                return R.drawable.ic_cat_bolt;
            default:
                return R.drawable.ic_cat_tag;
        }
    }
}