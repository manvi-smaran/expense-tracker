package com.expensetracker.server;

import com.expensetracker.server.Dto.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Category routes plus the shared lookups other routes need. */
public final class Categories {
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final List<String> ICONS = List.of("food", "transit", "game", "bolt", "tag");
    private static final String[][] DEFAULTS = {
        {"Food", "#FFAEC9", "food"},
        {"Transport", "#A0C4FF", "transit"},
        {"Entertainment", "#FFD6A5", "game"},
        {"Bills", "#CDB4DB", "bolt"},
        {"Other", "#CFD8DC", "tag"},
    };

    private Categories() {}

    public static void register(Router router, Db db) {
        router.secured("GET", "/api/categories", r -> Result.ok(db.tx(c -> list(c, r.userId()))))
              .secured("POST", "/api/categories", r -> Result.created(db.tx(c -> create(c, r))))
              .secured("PUT", "/api/categories/{id}", r -> db.tx(c -> update(c, r)))
              .secured("GET", "/api/categories/{id}/unlink-count", r -> db.tx(c -> unlinkCount(c, r)))
              .secured("POST", "/api/categories/purge", r -> db.tx(c -> purge(c, r)));
    }

    public static void seedDefaults(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO categories (user_id, name, color, icon, built_in) VALUES (?, ?, ?, ?, TRUE)")) {
            for (String[] d : DEFAULTS) {
                ps.setInt(1, userId);
                ps.setString(2, d[0]);
                ps.setString(3, d[1]);
                ps.setString(4, d[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Throws 400 unless the category exists and belongs to the user. */
    public static void requireOwned(Connection conn, int userId, Integer categoryId) throws SQLException {
        if (categoryId == null) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM categories WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, categoryId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.badRequest("Category not found");
                }
            }
        }
    }

    private static List<CategoryItem> list(Connection conn, int userId) throws SQLException {
        String sql = """
            SELECT c.id, c.name, c.color, c.icon, c.built_in,
                   COUNT(e.id) AS items, COALESCE(SUM(e.amount), 0) AS total, b.limit_amount
            FROM categories c
            LEFT JOIN expenses e ON e.category_id = c.id
            LEFT JOIN budgets b ON b.category_id = c.id AND b.user_id = c.user_id
            WHERE c.user_id = ?
            GROUP BY c.id, c.name, c.color, c.icon, c.built_in, b.limit_amount
            ORDER BY c.built_in DESC, c.name""";
        List<CategoryItem> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CategoryItem(rs.getInt("id"), rs.getString("name"), rs.getString("color"),
                            rs.getString("icon"), rs.getBoolean("built_in"), rs.getInt("items"),
                            rs.getBigDecimal("total"), rs.getBigDecimal("limit_amount")));
                }
            }
        }
        return out;
    }

    private static Void create(Connection conn, Req r) throws SQLException {
        CategoryRequest body = r.json(CategoryRequest.class);
        String name = validName(body.name());
        String color = validColor(body.color());
        String icon = validIcon(body.icon());
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM categories WHERE user_id = ? AND LOWER(name) = LOWER(?)")) {
            ps.setInt(1, r.userId());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new ApiException(409, "A category with that name already exists");
                }
            }
        }
        int id;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO categories (user_id, name, color, icon, built_in) VALUES (?, ?, ?, ?, FALSE)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.userId());
            ps.setString(2, name);
            ps.setString(3, color);
            ps.setString(4, icon);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getInt(1);
            }
        }
        if (body.limit() != null) {
            Budgets.upsert(conn, r.userId(), id, Req.money(body.limit(), "Limit"), null);
        }
        return null;
    }

    private static Result update(Connection conn, Req r) throws SQLException {
        int id = r.pathInt("id");
        CategoryRequest body = r.json(CategoryRequest.class);
        boolean builtIn;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT built_in FROM categories WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, id);
            ps.setInt(2, r.userId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound("Category not found");
                }
                builtIn = rs.getBoolean(1);
            }
        }
        // Built-in categories keep their name; colour/icon/limit stay editable.
        String name = builtIn ? null : validName(body.name());
        String color = validColor(body.color());
        String icon = validIcon(body.icon());
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE categories SET name = COALESCE(?, name), color = ?, icon = ? WHERE id = ? AND user_id = ?")) {
            ps.setString(1, name);
            ps.setString(2, color);
            ps.setString(3, icon);
            ps.setInt(4, id);
            ps.setInt(5, r.userId());
            ps.executeUpdate();
        }
        if (body.limit() != null) {
            Budgets.upsert(conn, r.userId(), id, Req.money(body.limit(), "Limit"), null);
        }
        return Result.noContent();
    }

    private static Result unlinkCount(Connection conn, Req r) throws SQLException {
        int id = r.pathInt("id");
        requireOwned(conn, r.userId(), id);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM expenses WHERE user_id = ? AND category_id = ?")) {
            ps.setInt(1, r.userId());
            ps.setInt(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return Result.ok(new CountResponse(rs.getInt(1)));
            }
        }
    }

    private static Result purge(Connection conn, Req r) throws SQLException {
        IdsRequest body = r.json(IdsRequest.class);
        if (body.ids() == null || body.ids().isEmpty()) {
            throw ApiException.badRequest("Pick at least one category");
        }
        int deleted = 0;
        // ON DELETE SET NULL unlinks expenses/subscriptions; ON DELETE CASCADE drops the budget.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM categories WHERE id = ? AND user_id = ? AND built_in = FALSE")) {
            for (Integer id : body.ids()) {
                if (id == null) {
                    continue;
                }
                ps.setInt(1, id);
                ps.setInt(2, r.userId());
                deleted += ps.executeUpdate();
            }
        }
        return Result.ok(new DeletedResponse(deleted));
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("Category name is required");
        }
        if (name.length() > 50) {
            throw ApiException.badRequest("Category name is too long (max 50)");
        }
        return name;
    }

    private static String validColor(String raw) {
        if (raw == null || !COLOR.matcher(raw.trim()).matches()) {
            throw ApiException.badRequest("Colour must look like #RRGGBB");
        }
        return raw.trim().toUpperCase();
    }

    private static String validIcon(String raw) {
        String icon = raw == null || raw.isBlank() ? "tag" : raw.trim();
        return ICONS.contains(icon) ? icon : "tag";
    }

}
