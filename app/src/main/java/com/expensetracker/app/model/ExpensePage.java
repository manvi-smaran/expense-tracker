package com.expensetracker.app.model;

import java.util.List;

public class ExpensePage {
    public List<ExpenseRow> rows;
    public int page;
    public int pageSize;
    public int totalItems;
    public int totalPages;
}