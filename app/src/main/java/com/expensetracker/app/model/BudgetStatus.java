package com.expensetracker.app.model;

import java.math.BigDecimal;

public class BudgetStatus {
    public int budgetId;
    public int categoryId;
    public String categoryName;
    public String color;
    public String icon;
    public BigDecimal spent;
    public BigDecimal limit;
    public BigDecimal percent;          // e.g. 82.5
    public int alertThresholdPercent;
    public BigDecimal remaining;        // negative when over budget
    public String status;               // "OK", "WARNING" or "EXCEEDED"
}