package com.expensetracker.app.model;

import java.math.BigDecimal;
import java.util.List;

public class SubscriptionOverview {
    public int activeCount;
    public BigDecimal monthlyBurn;   // every frequency normalised to per-month
    public BigDecimal dueTotal;      // what PROCESS DUE would add right now
    public List<Item> items;

    public static class Item {
        public Subscription subscription;
        public String categoryName;  // null if uncategorized
        public long daysUntilDue;    // negative when overdue
    }
}