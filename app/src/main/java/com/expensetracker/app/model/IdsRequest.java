package com.expensetracker.app.model;

import java.util.List;

public class IdsRequest {
    public final List<Integer> ids;

    public IdsRequest(List<Integer> ids) {
        this.ids = ids;
    }
}