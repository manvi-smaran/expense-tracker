package com.expensetracker.server;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Connection settings, read from environment variables so no credential is ever in the source. */
public record DbConfig(String jdbcUrl, String user, String password, int poolSize) {

    private static final int DEFAULT_POOL_SIZE = 5;
    /** Used when no database is configured: files in ./data/ (expense-tracker.mv.db). */
    static final String LOCAL_URL = "jdbc:h2:file:./data/expense-tracker;MODE=PostgreSQL";

    /**
     * Either {@code DATABASE_URL} (Neon's {@code postgresql://user:pass@host/db?sslmode=require}),
     * or {@code DB_URL} (a JDBC URL) with optional {@code DB_USER} and {@code DB_PASSWORD}.
     * With none of them set, a local H2 file database is used.
     */
    public static DbConfig fromEnv(Map<String, String> env) {
        int pool = parsePool(env.get("DB_POOL_SIZE"));
        String databaseUrl = blankToNull(env.get("DATABASE_URL"));
        if (databaseUrl != null) {
            return fromConnectionString(databaseUrl, pool);
        }
        String jdbc = blankToNull(env.get("DB_URL"));
        if (jdbc == null) {
            return new DbConfig(LOCAL_URL, "sa", "", pool);
        }
        return new DbConfig(jdbc, blankToNull(env.get("DB_USER")), env.getOrDefault("DB_PASSWORD", ""), pool);
    }

    /** Converts {@code postgresql://user:pass@host/db?query} into a JDBC URL plus credentials. */
    static DbConfig fromConnectionString(String connectionString, int poolSize) {
        URI uri = URI.create(connectionString.replaceFirst("^postgres(ql)?://", "postgresql://"));
        if (uri.getHost() == null) {
            throw new IllegalStateException("DATABASE_URL is not a valid PostgreSQL connection string.");
        }
        String user = null;
        String password = "";
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            user = decode(parts[0]);
            password = parts.length > 1 ? decode(parts[1]) : "";
        }
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String query = uri.getRawQuery() == null ? "sslmode=require" : uri.getRawQuery();
        String jdbc = "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath() + "?" + query;
        return new DbConfig(jdbc, user, password, poolSize);
    }

    private static int parsePool(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_POOL_SIZE;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("DB_POOL_SIZE must be a number, got: " + raw, e);
        }
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
