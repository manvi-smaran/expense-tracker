package com.expensetracker.app.model;

import java.math.BigDecimal;

public class ExpenseRequest {
    public final Integer categoryId;
    public final BigDecimal amount;
    public final String description;
    public final String date;       // "2026-10-26"
    public final String note;

    public ExpenseRequest(Integer categoryId, BigDecimal amount, String description, String date, String note) {
        this.categoryId = categoryId;
        this.amount = amount;
        this.description = description;
        this.date = date;
        this.note = note;
    }
}