package com.expensetracker.app.model;

import java.math.BigDecimal;

public class CategoryRequest {
    public final String name;
    public final String color;     // "#FFAEC9"
    public final String icon;      // icon key, e.g. "tag"
    public final BigDecimal limit; // null = don't touch the budget

    public CategoryRequest(String name, String color, String icon, BigDecimal limit) {
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.limit = limit;
    }
}