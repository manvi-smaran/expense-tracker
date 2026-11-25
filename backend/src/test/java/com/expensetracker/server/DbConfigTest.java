package com.expensetracker.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DbConfigTest {
    @Test
    void neonConnectionStringBecomesJdbcUrl() {
        DbConfig c = DbConfig.fromEnv(Map.of(
                "DATABASE_URL", "postgresql://alice:p%40ss@ep-cool-123.neon.tech/neondb?sslmode=require"));
        assertEquals("jdbc:postgresql://ep-cool-123.neon.tech/neondb?sslmode=require", c.jdbcUrl());
        assertEquals("alice", c.user());
        assertEquals("p@ss", c.password());
    }

    @Test
    void sslDefaultsToRequired() {
        DbConfig c = DbConfig.fromEnv(Map.of("DATABASE_URL", "postgres://u:p@host:5432/db"));
        assertEquals("jdbc:postgresql://host:5432/db?sslmode=require", c.jdbcUrl());
    }

    @Test
    void jdbcUrlVariantAndMissingConfig() {
        DbConfig c = DbConfig.fromEnv(Map.of("DB_URL", "jdbc:postgresql://h/db", "DB_USER", "u", "DB_PASSWORD", "p"));
        assertEquals("u", c.user());
        assertEquals(DbConfig.LOCAL_URL, DbConfig.fromEnv(Map.of()).jdbcUrl());
    }
}
