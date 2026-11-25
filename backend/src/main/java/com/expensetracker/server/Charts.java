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

/** Donut-chart data: month totals by category, and the latest expenses of one slice. */
public final class Charts {
    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 50;

    private Charts() {}

    public static void register(Router router, Db db) {
        router.secured("GET", "/api/charts/month", r -> Result.ok(db.tx(c -> month(c, r))))
              .secured("GET", "/api/charts/category-expenses", r -> Result.ok(db.tx(c -> recent(c, r))));
    }

    private static MonthSummary month(Connection conn, Req r) throws SQLException {
        YearMonth month = r.queryMonth("month");
        String sql = """
            SELECT e.category_id, c.name, c.color, c.icon, SUM(e.amount) AS total, COUNT(*) AS cnt
            FROM expenses e LEFT JOIN categories c ON c.id = e.category_id
            WHERE e.user_id = ? AND e.expense_date BETWEEN ? AND ?
            GROUP BY e.category_id, c.name, c.color, c.icon
            ORDER BY total DESC, c.name""";
        List<CategorySpend> spends = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.userId());
            ps.setObject(2, month.atDay(1));
            ps.setObject(3, month.atEndOfMonth());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cat = rs.getInt("category_id");
                    boolean uncategorized = rs.wasNull();
                    BigDecimal total = rs.getBigDecimal("total");
                    grand = grand.add(total);
                    spends.add(new CategorySpend(uncategorized ? null : cat,
                            uncategorized ? "Uncategorized" : rs.getString("name"),
                            uncategorized ? "#9E9E9E" : rs.getString("color"),
                            uncategorized ? "tag" : rs.getString("icon"), total, rs.getInt("cnt")));
                }
            }
        }
        List<Slice> slices = new ArrayList<>();
        for (CategorySpend s : spends) {
            int percent = grand.signum() == 0 ? 0
                    : s.total().multiply(BigDecimal.valueOf(100)).divide(grand, 0, RoundingMode.HALF_UP).intValue();
            slices.add(new Slice(s, percent));
        }
        return new MonthSummary(month.toString(), grand, slices);
    }

    private static List<Expense> recent(Connection conn, Req r) throws SQLException {
        YearMonth month = r.queryMonth("month");
        Integer categoryId = r.queryIntOrNull("categoryId");   // absent = the Uncategorized slice
        int limit = Math.max(1, Math.min(MAX_LIMIT, r.queryInt("limit", DEFAULT_LIMIT)));
        String sql = "SELECT * FROM expenses WHERE user_id = ? AND expense_date BETWEEN ? AND ? AND "
                + (categoryId == null ? "category_id IS NULL" : "category_id = ?")
                + " ORDER BY expense_date DESC, id DESC LIMIT ?";
        List<Expense> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, r.userId());
            ps.setObject(i++, month.atDay(1));
            ps.setObject(i++, month.atEndOfMonth());
            if (categoryId != null) {
                ps.setInt(i++, categoryId);
            }
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(Expenses.read(rs));
                }
            }
        }
        return out;
    }
}
