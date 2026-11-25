package com.expensetracker.server;

import com.expensetracker.server.Dto.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class Subscriptions {
    private static final List<String> FREQUENCIES = List.of("WEEKLY", "MONTHLY", "YEARLY");
    /** Safety valve so an ancient due date can't create thousands of expenses in one call. */
    private static final int MAX_CATCH_UP = 120;

    private Subscriptions() {}

    public static void register(Router router, Db db) {
        router.secured("GET", "/api/subscriptions", r -> Result.ok(db.tx(c -> overview(c, r))))
              .secured("POST", "/api/subscriptions", r -> db.tx(c -> add(c, r)))
              .secured("POST", "/api/subscriptions/process-due", r -> Result.ok(db.tx(c -> processDue(c, r))))
              .secured("POST", "/api/subscriptions/delete", r -> Result.ok(db.tx(c -> delete(c, r))));
    }

    static LocalDate advance(LocalDate from, String frequency) {
        return switch (frequency) {
            case "WEEKLY" -> from.plusWeeks(1);
            case "MONTHLY" -> from.plusMonths(1);
            case "YEARLY" -> from.plusYears(1);
            default -> throw new IllegalArgumentException("Unknown frequency " + frequency);
        };
    }

    /** What the subscription costs per month, whatever its billing cycle. */
    static BigDecimal perMonth(BigDecimal amount, String frequency) {
        return switch (frequency) {
            case "WEEKLY" -> amount.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case "YEARLY" -> amount.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            default -> amount;
        };
    }

    private static SubscriptionOverview overview(Connection conn, Req r) throws SQLException {
        LocalDate today = r.queryDate("date");
        String sql = """
            SELECT s.*, c.name AS cat_name FROM subscriptions s
            LEFT JOIN categories c ON c.id = s.category_id
            WHERE s.user_id = ? ORDER BY s.active DESC, s.next_due_date, s.id""";
        List<SubscriptionItem> items = new ArrayList<>();
        BigDecimal burn = BigDecimal.ZERO;
        BigDecimal due = BigDecimal.ZERO;
        int active = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.userId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cat = rs.getInt("category_id");
                    Integer categoryId = rs.wasNull() ? null : cat;
                    LocalDate next = rs.getObject("next_due_date", LocalDate.class);
                    BigDecimal amount = rs.getBigDecimal("amount");
                    String frequency = rs.getString("frequency");
                    boolean isActive = rs.getBoolean("active");
                    if (isActive) {
                        active++;
                        burn = burn.add(perMonth(amount, frequency));
                        if (!next.isAfter(today)) {
                            due = due.add(amount);
                        }
                    }
                    items.add(new SubscriptionItem(
                            new Subscription(rs.getInt("id"), categoryId, rs.getString("name"), amount,
                                    frequency, next.toString(), isActive),
                            rs.getString("cat_name"), ChronoUnit.DAYS.between(today, next)));
                }
            }
        }
        return new SubscriptionOverview(active, burn, due, items);
    }

    private static Result add(Connection conn, Req r) throws SQLException {
        SubscriptionRequest body = r.json(SubscriptionRequest.class);
        String name = body.name() == null ? "" : body.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("Name is required");
        }
        if (name.length() > 100) {
            throw ApiException.badRequest("Name is too long (max 100)");
        }
        BigDecimal amount = Req.money(body.amount(), "Amount");
        String frequency = body.frequency() == null ? "" : body.frequency().trim().toUpperCase();
        if (!FREQUENCIES.contains(frequency)) {
            throw ApiException.badRequest("Frequency must be WEEKLY, MONTHLY or YEARLY");
        }
        LocalDate next = Req.parseDate(body.nextDueDate(), "next due date");
        Categories.requireOwned(conn, r.userId(), body.categoryId());
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO subscriptions (user_id, category_id, name, amount, frequency, next_due_date, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, TRUE)")) {
            ps.setInt(1, r.userId());
            ps.setObject(2, body.categoryId());
            ps.setString(3, name);
            ps.setBigDecimal(4, amount);
            ps.setString(5, frequency);
            ps.setObject(6, next);
            ps.executeUpdate();
        }
        return Result.created(null);
    }

    /** Turns every due subscription into an expense and moves its due date forward. */
    private static ProcessedResponse processDue(Connection conn, Req r) throws SQLException {
        LocalDate today = r.queryDate("date");
        record Due(int id, Integer categoryId, String name, BigDecimal amount, String frequency, LocalDate next) {}
        List<Due> dues = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM subscriptions WHERE user_id = ? AND active = TRUE AND next_due_date <= ?")) {
            ps.setInt(1, r.userId());
            ps.setObject(2, today);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cat = rs.getInt("category_id");
                    Integer categoryId = rs.wasNull() ? null : cat;   // must be read right after getInt
                    dues.add(new Due(rs.getInt("id"), categoryId, rs.getString("name"),
                            rs.getBigDecimal("amount"), rs.getString("frequency"),
                            rs.getObject("next_due_date", LocalDate.class)));
                }
            }
        }
        int created = 0;
        for (Due d : dues) {
            LocalDate next = d.next();
            for (int i = 0; i < MAX_CATCH_UP && !next.isAfter(today); i++) {
                Expenses.insert(conn, r.userId(), d.categoryId(), d.amount(), d.name(), next, "Subscription");
                created++;
                next = advance(next, d.frequency());
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE subscriptions SET next_due_date = ? WHERE id = ?")) {
                ps.setObject(1, next);
                ps.setInt(2, d.id());
                ps.executeUpdate();
            }
        }
        return new ProcessedResponse(created);
    }

    private static DeletedResponse delete(Connection conn, Req r) throws SQLException {
        IdsRequest body = r.json(IdsRequest.class);
        if (body.ids() == null || body.ids().isEmpty()) {
            throw ApiException.badRequest("Pick at least one subscription");
        }
        int deleted = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM subscriptions WHERE id = ? AND user_id = ?")) {
            for (Integer id : body.ids()) {
                if (id == null) {
                    continue;
                }
                ps.setInt(1, id);
                ps.setInt(2, r.userId());
                deleted += ps.executeUpdate();
            }
        }
        return new DeletedResponse(deleted);
    }
}
