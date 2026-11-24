package com.expensetracker.app.model;

import java.math.BigDecimal;

public class BudgetAlert {
    public int categoryId;
    public BigDecimal spent;
    public BigDecimal limit;
    public int percentUsed;
    public String status;           // "WARNING" or "EXCEEDED"
}