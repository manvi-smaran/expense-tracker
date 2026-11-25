package com.expensetracker.server;

/** A handler's answer: status plus an optional JSON body (null = empty body). */
public record Result(int status, Object body) {
    public static Result ok(Object body) {
        return new Result(200, body);
    }

    public static Result created(Object body) {
        return new Result(201, body);
    }

    public static Result noContent() {
        return new Result(204, null);
    }
}
