package com.expensetracker.server;

import com.google.gson.JsonParseException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** One incoming request, after routing and authentication. Immutable. */
public record Req(String method, String path, Map<String, String> query,
                  Map<String, String> pathVars, String body, int userId) {

    public <T> T json(Class<T> type) {
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest("Request body is required");
        }
        try {
            T parsed = Json.GSON.fromJson(body, type);
            if (parsed == null) {
                throw ApiException.badRequest("Request body is required");
            }
            return parsed;
        } catch (JsonParseException e) {
            throw ApiException.badRequest("Malformed JSON");
        }
    }

    public int pathInt(String name) {
        try {
            return Integer.parseInt(pathVars.get(name));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Invalid " + name);
        }
    }

    public int queryInt(String name, int fallback) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Invalid " + name);
        }
    }

    public Integer queryIntOrNull(String name) {
        String raw = query.get(name);
        return raw == null || raw.isBlank() ? null : queryInt(name, 0);
    }

    /** ?date=yyyy-MM-dd, defaulting to the server's today. */
    public LocalDate queryDate(String name) {
        String raw = query.get(name);
        return raw == null || raw.isBlank() ? LocalDate.now() : parseDate(raw, name);
    }

    /** ?month=yyyy-MM, defaulting to the current month. */
    public YearMonth queryMonth(String name) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Invalid " + name + " (expected yyyy-MM)");
        }
    }

    public static LocalDate parseDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Date is required");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Invalid " + field + " (expected yyyy-MM-dd)");
        }
    }

    public static BigDecimal money(BigDecimal raw, String label) {
        if (raw == null || raw.signum() <= 0) {
            throw ApiException.badRequest(label + " must be greater than 0");
        }
        if (raw.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw ApiException.badRequest(label + " is too large");
        }
        return raw.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
