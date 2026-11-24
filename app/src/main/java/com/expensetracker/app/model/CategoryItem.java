package com.expensetracker.app.model;

import java.math.BigDecimal;

public class CategoryItem {
    public int id;
    public String name;
    public String color;
    public String icon;
    public boolean builtIn;
    public int itemCount;
    public BigDecimal totalSpent;
    public BigDecimal monthlyLimit;   // null when no budget
}