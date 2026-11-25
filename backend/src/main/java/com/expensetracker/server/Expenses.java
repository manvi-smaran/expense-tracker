package com.expensetracker.server;

import com.expensetracker.server.Dto.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class Expenses {
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 10;

    private Expenses() {}

    public static void register(Router router, Db db) {
        router.secured("GET", "/api/expenses", r -> Result.ok(db.tx(c -> page(c, r))))
              .secured("GET", "/api/expenses/today", r -> Result.ok(db.tx(c -> dayTotal(c, r))))
              .secured("POST", "/api/expenses", r -> Result.created(db.tx(c -> save(c, r, null))))
              .secured("PUT", "/api/expenses/{id}", r -> Result.ok(db.tx(c -> save(c, r, r.pathInt("id")))))
              .secured("DELETE", "/api/expenses/{id}", r -> db.tx(c -> delete(c, r)));
    }

    static Expense read(ResultSet rs) throws SQLException {
        int cat = rs.getInt("category_id");
        Integer categoryId = rs.wasNull() ? null : cat;
        return new Expense(rs.getInt("id"), categoryId, rs.getBigDecimal("amount"),
                rs.getString("description"), rs.getObject("expense_date", LocalDate.class).toString(),
                rs.getString("note"));
    }

    /** Inserts an expense and returns its id. Shared with subscription processing. */
    static int insert(Connection conn, int userId, Integer categoryId, BigDecimal amount,
                      String description, LocalDate date, String note) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO expenses (user_id, category_id, amount, description, expense_date, note) VALUES (?,?,?,?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setObject(2, categoryId);
            ps.setBigDecimal(3, amount);
            ps.setString(4, description);
            ps.setObject(5, date);
            ps.setString(6, note);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static ExpensePage page(Connection conn, Req r) throws SQLException {
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, r.queryInt("pageSize", DEFAULT_PAGE_SIZE)));
        Integer categoryId = r.queryIntOrNull("categoryId");
        String where = categoryId == null ? "e.user_id = ?" : "e.user_id = ? AND e.category_id = ?";
        int total;
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM expenses e WHERE " + where)) {
            bind(ps, r.userId(), categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                total = rs.getInt(1);
            }
        }
        int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        int page = Math.max(1, Math.min(totalPages, r.queryInt("page", 1)));   // out-of-range pages are clamped
        String sql = "SELECT e.*, c.name AS cat_name, c.color AS cat_color FROM expenses e "
                + "LEFT JOIN categories c ON c.id = e.category_id WHERE " + where
                + " ORDER BY e.expense_date DESC, e.id DESC LIMIT ? OFFSET ?";
        List<ExpenseRow> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int next = bind(ps, r.userId(), categoryId);
            ps.setInt(next, pageSize);
            ps.setInt(next + 1, (page - 1) * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ExpenseRow(read(rs), rs.getString("cat_name"), rs.getString("cat_color")));
                }
            }
        }
        return new ExpensePage(rows, page, pageSize, total, totalPages);
    }

    /** Binds userId (and categoryId when present); returns the next free parameter index. */
    private static int bind(PreparedStatement ps, int userId, Integer categoryId) throws SQLException {
        ps.setInt(1, userId);
        if (categoryId == null) {
            return 2;
        }
        ps.setInt(2, categoryId);
        return 3;
    }

    private static DayTotal dayTotal(Connection conn, Req r) throws SQLException {
        LocalDate date = r.queryDate("date");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE user_id = ? AND expense_date = ?")) {
            ps.setInt(1, r.userId());
            ps.setObject(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new DayTotal(date.toString(), rs.getBigDecimal(1));
            }
        }
    }

    private static ExpenseResponse save(Connection conn, Req r, Integer existingId) throws SQLException {
        ExpenseRequest body = r.json(ExpenseRequest.class);
        BigDecimal amount = Req.money(body.amount(), "Amount");
        String description = body.description() == null ? "" : body.description().trim();
        if (description.isEmpty()) {
            throw ApiException.badRequest("Description is required");
        }
        if (description.length() > 200) {
            throw ApiException.badRequest("Description is too long (max 200)");
        }
        String note = body.note() == null || body.note().isBlank() ? null : body.note().trim();
        if (note != null && note.length() > 500) {
            throw ApiException.badRequest("Note is too long (max 500)");
        }
        LocalDate date = Req.parseDate(body.date(), "date");
        Categories.requireOwned(conn, r.userId(), body.categoryId());

        int id;
        if (existingId == null) {
            id = insert(conn, r.userId(), body.categoryId(), amount, description, date, note);
        } else {
            id = existingId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE expenses SET category_id=?, amount=?, description=?, expense_date=?, note=? WHERE id=? AND user_id=?")) {
                ps.setObject(1, body.categoryId());
                ps.setBigDecimal(2, amount);
                ps.setString(3, description);
                ps.setObject(4, date);
                ps.setString(5, note);
                ps.setInt(6, id);
                ps.setInt(7, r.userId());
                if (ps.executeUpdate() == 0) {
                    throw ApiException.notFound("Expense not found");
                }
            }
        }
        Expense saved = new Expense(id, body.categoryId(), amount, description, date.toString(), note);
        BudgetAlert alert = Budgets.alertFor(conn, r.userId(), body.categoryId(), YearMonth.from(date));
        return new ExpenseResponse(saved, alert);
    }

    private static Result delete(Connection conn, Req r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM expenses WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, r.pathInt("id"));
            ps.setInt(2, r.userId());
            if (ps.executeUpdate() == 0) {
                throw ApiException.notFound("Expense not found");
            }
        }
        return Result.noContent();
    }
}
