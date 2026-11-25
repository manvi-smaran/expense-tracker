package com.expensetracker.server;

import java.math.BigDecimal;
import java.util.List;

/** JSON shapes. Field names must match the Android app's model classes exactly. */
public final class Dto {
    private Dto() {}

    // ---- auth ----
    public record User(int id, String name, String email) {}
    public record AuthResponse(String token, User user) {}
    public record LoginRequest(String email, String password) {}
    public record RegisterRequest(String name, String email, String password) {}
    public record UpdateProfileRequest(String name) {}

    // ---- expenses ----
    public record Expense(int id, Integer categoryId, BigDecimal amount, String description,
                          String expenseDate, String note) {}
    public record ExpenseRequest(Integer categoryId, BigDecimal amount, String description,
                                 String date, String note) {}
    public record ExpenseRow(Expense expense, String categoryName, String categoryColor) {}
    public record ExpensePage(List<ExpenseRow> rows, int page, int pageSize, int totalItems,
                              int totalPages) {}
    public record BudgetAlert(int categoryId, BigDecimal spent, BigDecimal limit, int percentUsed,
                              String status) {}
    public record ExpenseResponse(Expense expense, BudgetAlert alert) {}
    public record DayTotal(String date, BigDecimal total) {}

    // ---- budgets ----
    public record BudgetRequest(BigDecimal limit, Integer alertThresholdPercent) {}
    public record BudgetStatus(int budgetId, int categoryId, String categoryName, String color,
                               String icon, BigDecimal spent, BigDecimal limit, BigDecimal percent,
                               int alertThresholdPercent, BigDecimal remaining, String status) {}

    // ---- categories ----
    public record CategoryItem(int id, String name, String color, String icon, boolean builtIn,
                               int itemCount, BigDecimal totalSpent, BigDecimal monthlyLimit) {}
    public record CategoryRequest(String name, String color, String icon, BigDecimal limit) {}
    public record IdsRequest(List<Integer> ids) {}
    public record CountResponse(int count) {}
    public record DeletedResponse(int deleted) {}

    // ---- charts ----
    public record CategorySpend(Integer categoryId, String name, String color, String icon,
                                BigDecimal total, int count) {}
    public record Slice(CategorySpend spend, int percent) {}
    public record MonthSummary(String month, BigDecimal total, List<Slice> slices) {}

    // ---- subscriptions ----
    public record Subscription(int id, Integer categoryId, String name, BigDecimal amount,
                               String frequency, String nextDueDate, boolean active) {}
    public record SubscriptionRequest(String name, BigDecimal amount, Integer categoryId,
                                      String frequency, String nextDueDate) {}
    public record SubscriptionItem(Subscription subscription, String categoryName,
                                   long daysUntilDue) {}
    public record SubscriptionOverview(int activeCount, BigDecimal monthlyBurn, BigDecimal dueTotal,
                                       List<SubscriptionItem> items) {}
    public record ProcessedResponse(int created) {}

    // ---- pet ----
    public record PetStatus(int level, String title, int xp, int xpIntoLevel, int xpForLevel,
                            int levelPercent, int streakDays, int hunger, int moodPercent,
                            String mood, String message, String budgetTip) {}
}
