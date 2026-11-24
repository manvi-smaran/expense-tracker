package com.expensetracker.app.model;

import java.math.BigDecimal;

public class Expense {
    public int id;
    public Integer categoryId;      // null when its category was deleted
    public BigDecimal amount;
    public String description;
    public String expenseDate;      // "2026-10-26"
    public String note;
}