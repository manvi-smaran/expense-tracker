package com.expensetracker.app.model;

import java.math.BigDecimal;

public class BudgetRequest {
    public final BigDecimal limit;
    public final Integer alertThresholdPercent;

    public BudgetRequest(BigDecimal limit, Integer alertThresholdPercent) {
        this.limit = limit;
        this.alertThresholdPercent = alertThresholdPercent;
    }
}