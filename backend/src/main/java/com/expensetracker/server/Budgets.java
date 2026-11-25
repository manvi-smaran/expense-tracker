package com.expensetracker.server;

import com.expensetracker.server.Dto.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Monthly per-category budgets and the alert shown right after saving an expense. */
public final class Budgets {
    static final int DEFAULT_THRESHOLD = 80;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Budgets() {}

    public static void register(Router router, Db db) {
        router.secured("GET", "/api/budgets", r -> Result.ok(db.tx(c -> statuses(c, r.userId(), r.queryMonth("month")))))
              .secured("PUT", "/api/budgets/{categoryId}", r -> db.tx(c -> save(c, r)));
    }

    /** OK below the threshold, WARNING from the threshold up to the limit, EXCEEDED above it. */
    static String statusOf(BigDecimal spent, BigDecimal limit, int threshold) {
        if (spent.compareTo(limit) > 0) {
            return "EXCEEDED";
        }
        return percentOf(spent, limit).compareTo(BigDecimal.valueOf(threshold)) >= 0 ? "WARNING" : "OK";
    }

    static BigDecimal percentOf(BigDecimal spent, BigDecimal limit) {
        return spent.multiply(HUNDRED).divide(limit, 1, RoundingMode.HALF_UP);
    }

    static void upsert(Connection conn, int userId, int categoryId, BigDecimal limit, Integer threshold)
            throws SQLException {
        Integer existing = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT alert_threshold_percent FROM budgets WHERE user_id = ? AND category_id = ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existing = rs.getInt(1);
                }
            }
        }
        int effective = threshold != null ? threshold : existing != null ? existing : DEFAULT_THRESHOLD;
        String sql = existing == null
                ? "INSERT INTO budgets (limit_amount, alert_threshold_percent, user_id, category_id) VALUES (?, ?, ?, ?)"
                : "UPDATE budgets SET limit_amount = ?, alert_threshold_percent = ? WHERE user_id = ? AND category_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, limit);
            ps.setInt(2, effective);
            ps.setInt(3, userId);
            ps.setInt(4, categoryId);
            ps.executeUpdate();
        }
    }

    /** The alert for this category and month, or null when it has no budget or is still OK. */
    static BudgetAlert alertFor(Connection conn, int userId, Integer categoryId, YearMonth month)
            throws SQLException {
        if (categoryId == null) {
            return null;
        }
        String sql = """
            SELECT b.limit_amount, b.alert_threshold_percent,
                   (SELECT COALESCE(SUM(e.amount), 0) FROM expenses e
                     WHERE e.user_id = b.user_id AND e.category_id = b.category_id
                       AND e.expense_date BETWEEN ? AND ?) AS spent
            FROM budgets b WHERE b.user_id = ? AND b.category_id = ?""";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, month.atDay(1));
            ps.setObject(2, month.atEndOfMonth());
            ps.setInt(3, userId);
            ps.setInt(4, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                BigDecimal limit = rs.getBigDecimal(1);
                BigDecimal spent = rs.getBigDecimal("spent");
                String status = statusOf(spent, limit, rs.getInt(2));
                if (status.equals("OK")) {
                    return null;
                }
                return new BudgetAlert(categoryId, spent, limit,
                        percentOf(spent, limit).intValue(), status);
            }
        }
    }

    static List<BudgetStatus> statuses(Connection conn, int userId, YearMonth month) throws SQLException {
        String sql = """
            SELECT b.id, b.category_id, c.name, c.color, c.icon, b.limit_amount, b.alert_threshold_percent,
                   (SELECT COALESCE(SUM(e.amount), 0) FROM expenses e
                     WHERE e.user_id = b.user_id AND e.category_id = b.category_id
                       AND e.expense_date BETWEEN ? AND ?) AS spent
            FROM budgets b JOIN categories c ON c.id = b.category_id
            WHERE b.user_id = ? ORDER BY c.name""";
        List<BudgetStatus> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, month.atDay(1));
            ps.setObject(2, month.atEndOfMonth());
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal limit = rs.getBigDecimal("limit_amount");
                    BigDecimal spent = rs.getBigDecimal("spent");
                    int threshold = rs.getInt("alert_threshold_percent");
                    out.add(new BudgetStatus(rs.getInt("id"), rs.getInt("category_id"), rs.getString("name"),
                            rs.getString("color"), rs.getString("icon"), spent, limit,
                            percentOf(spent, limit), threshold, limit.subtract(spent),
                            statusOf(spent, limit, threshold)));
                }
            }
        }
        return out;
    }

    private static Result save(Connection conn, Req r) throws SQLException {
        int categoryId = r.pathInt("categoryId");
        BudgetRequest body = r.json(BudgetRequest.class);
        BigDecimal limit = Req.money(body.limit(), "Limit");
        Integer threshold = body.alertThresholdPercent();
        if (threshold != null && (threshold < 1 || threshold > 100)) {
            throw ApiException.badRequest("Alert threshold must be between 1 and 100");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM categories WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, categoryId);
            ps.setInt(2, r.userId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw ApiException.notFound("Category not found");
                }
            }
        }
        upsert(conn, r.userId(), categoryId, limit, threshold);
        return Result.noContent();
    }
}
