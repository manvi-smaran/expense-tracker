package com.expensetracker.app.model;

import java.math.BigDecimal;

public class Subscription {
    public int id;
    public Integer categoryId;      // null if its category was deleted
    public String name;
    public BigDecimal amount;
    public String frequency;        // "WEEKLY", "MONTHLY" or "YEARLY"
    public String nextDueDate;      // "2026-11-02"
    public boolean active;
}