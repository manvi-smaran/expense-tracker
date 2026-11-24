package com.expensetracker.app.model;

public class ExpenseResponse {
    public Expense expense;
    public BudgetAlert alert;       // null when no budget threshold was crossed
}