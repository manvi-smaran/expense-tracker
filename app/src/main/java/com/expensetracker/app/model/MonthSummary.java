package com.expensetracker.app.model;

import java.math.BigDecimal;
import java.util.List;

public class MonthSummary {
    public String month;          // "2026-11"
    public BigDecimal total;
    public List<Slice> slices;    // biggest first

    public static class Slice {
        public CategorySpend spend;
        public int percent;
    }
}