package com.expensetracker.server;

import com.expensetracker.server.Dto.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Pattern;

/** Register, login and the profile. */
public final class Auth {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final int MIN_PASSWORD = 6;
    private static final int MAX_PASSWORD = 128;

    private Auth() {}

    public static void register(Router router, Db db) {
        router.open("POST", "/api/auth/register", r -> Result.created(db.tx(c -> signUp(c, r))))
              .open("POST", "/api/auth/login", r -> Result.ok(db.tx(c -> logIn(c, r))))
              .secured("GET", "/api/me", r -> Result.ok(db.tx(c -> me(c, r.userId()))))
              .secured("PUT", "/api/me", r -> Result.ok(db.tx(c -> rename(c, r))));
    }

    private static AuthResponse signUp(Connection conn, Req r) throws SQLException {
        RegisterRequest body = r.json(RegisterRequest.class);
        String name = cleanName(body.name());
        String email = cleanEmail(body.email());
        String password = body.password() == null ? "" : body.password();
        if (password.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD + " characters");
        }
        if (password.length() > MAX_PASSWORD) {
            throw ApiException.badRequest("Password is too long");
        }
        int id;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, Security.hashPassword(password));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getInt(1);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ApiException(409, "An account with that email already exists");
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new ApiException(409, "An account with that email already exists");
            }
            throw e;
        }
        Categories.seedDefaults(conn, id);
        return new AuthResponse(issueToken(conn, id), new User(id, name, email));
    }

    private static AuthResponse logIn(Connection conn, Req r) throws SQLException {
        LoginRequest body = r.json(LoginRequest.class);
        String email = body.email() == null ? "" : body.email().trim().toLowerCase();
        String password = body.password() == null ? "" : body.password();
        if (email.isEmpty() || password.isEmpty()) {
            throw ApiException.badRequest("Email and password are required");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, password_hash FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    Security.burnTime(password);
                    throw ApiException.unauthorized("Invalid email or password");
                }
                if (!Security.verifyPassword(password, rs.getString("password_hash"))) {
                    throw ApiException.unauthorized("Invalid email or password");
                }
                int id = rs.getInt("id");
                return new AuthResponse(issueToken(conn, id), new User(id, rs.getString("name"), email));
            }
        }
    }

    static User me(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name, email FROM users WHERE id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.unauthorized("Log in required");
                }
                return new User(userId, rs.getString("name"), rs.getString("email"));
            }
        }
    }

    private static User rename(Connection conn, Req r) throws SQLException {
        String name = cleanName(r.json(UpdateProfileRequest.class).name());
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET name = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setInt(2, r.userId());
            ps.executeUpdate();
        }
        return me(conn, r.userId());
    }

    private static String issueToken(Connection conn, int userId) throws SQLException {
        String token = Security.newToken();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tokens (token_hash, user_id) VALUES (?, ?)")) {
            ps.setString(1, Security.sha256Hex(token));
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
        return token;
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("Name is required");
        }
        if (name.length() > 100) {
            throw ApiException.badRequest("Name is too long (max 100)");
        }
        return name;
    }

    private static String cleanEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase();
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("Enter a valid email address");
        }
        return email;
    }
}
