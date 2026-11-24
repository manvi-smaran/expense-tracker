package com.expensetracker.app.model;

import java.math.BigDecimal;

public class CategorySpend {
    public Integer categoryId;   // null = the "Uncategorized" group
    public String name;
    public String color;
    public String icon;
    public BigDecimal total;
    public int count;
}