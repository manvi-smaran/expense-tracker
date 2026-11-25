package com.expensetracker.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/**
 * Database access: plain JDBC over a HikariCP pool. Production runs on PostgreSQL (Neon); the
 * connection settings come from the environment, see {@link DbConfig}.
 */
public final class Db {
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection conn) throws SQLException;
    }

    private static final String SCHEMA_RESOURCE = "/schema.sql";

    private static List<String> loadSchema() {
        try (InputStream in = Db.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + SCHEMA_RESOURCE);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
            return Arrays.stream(sql.split(";")).map(String::strip).filter(x -> !x.isEmpty()).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + SCHEMA_RESOURCE, e);
        }
    }

    private final HikariDataSource pool;

    public Db(DbConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        if (config.user() != null) {
            hc.setUsername(config.user());
            hc.setPassword(config.password());
        }
        hc.setMaximumPoolSize(config.poolSize());
        hc.setPoolName("expense-tracker");
        this.pool = new HikariDataSource(hc);
        tx(conn -> {
            try (Statement st = conn.createStatement()) {
                for (String ddl : loadSchema()) {
                    st.execute(ddl);
                }
            }
            return null;
        });
    }

    /** Runs {@code work} in one transaction: commit on success, roll back on any exception. */
    public <T> T tx(Work<T> work) {
        try (Connection conn = pool.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
    }

    /** Closes the pool; called on shutdown. */
    public void close() {
        pool.close();
    }
}
