package com.expensetracker.app.model;

import java.math.BigDecimal;

public class SubscriptionRequest {
    public final String name;
    public final BigDecimal amount;
    public final Integer categoryId;
    public final String frequency;      // "WEEKLY", "MONTHLY" or "YEARLY"
    public final String nextDueDate;    // "2026-11-02"

    public SubscriptionRequest(String name, BigDecimal amount, Integer categoryId,
                               String frequency, String nextDueDate) {
        this.name = name;
        this.amount = amount;
        this.categoryId = categoryId;
        this.frequency = frequency;
        this.nextDueDate = nextDueDate;
    }
}