package com.expensetracker.server;

/** A client-visible error: becomes {"error": message} with the given HTTP status. */
public final class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, message);
    }
}
