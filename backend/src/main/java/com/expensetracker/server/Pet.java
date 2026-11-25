package com.expensetracker.server;

import com.expensetracker.server.Dto.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** "Buddy Bot": all of its stats are derived from the user's expense history. */
public final class Pet {
    static final int XP_PER_EXPENSE = 10;
    private static final int XP_PER_LEVEL_STEP = 50;       // level n needs 50*n XP to reach n+1
    private static final int HUNGER_PER_IDLE_DAY = 25;
    private static final int STREAK_LOOKBACK_DAYS = 400;
    private static final String[] TITLES = {
        "Hatchling", "Coin Pup", "Budget Buddy", "Savings Scout", "Frugal Guardian", "Penny Sage"};

    private Pet() {}

    public static void register(Router router, Db db) {
        router.secured("GET", "/api/pet", r -> Result.ok(db.tx(c -> status(c, r))));
    }

    /** XP spent so far on levels below {@code level}. */
    static int xpToReach(int level) {
        return XP_PER_LEVEL_STEP * (level - 1) * level / 2;
    }

    static int levelFor(int xp) {
        int level = 1;
        while (xp >= xpToReach(level + 1)) {
            level++;
        }
        return level;
    }

    static int streak(List<LocalDate> datesDesc, LocalDate today) {
        LocalDate expected = !datesDesc.isEmpty() && datesDesc.get(0).equals(today) ? today : today.minusDays(1);
        int days = 0;
        for (LocalDate d : datesDesc) {
            if (d.equals(expected)) {
                days++;
                expected = expected.minusDays(1);
            } else if (d.isBefore(expected)) {
                break;
            }
        }
        return days;
    }

    private static PetStatus status(Connection conn, Req r) throws SQLException {
        LocalDate today = r.queryDate("date");
        int expenseCount;
        List<LocalDate> dates = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM expenses WHERE user_id = ?")) {
            ps.setInt(1, r.userId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                expenseCount = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT expense_date FROM expenses WHERE user_id = ? AND expense_date <= ? "
                        + "AND expense_date >= ? ORDER BY expense_date DESC")) {
            ps.setInt(1, r.userId());
            ps.setObject(2, today);
            ps.setObject(3, today.minusDays(STREAK_LOOKBACK_DAYS));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dates.add(rs.getObject(1, LocalDate.class));
                }
            }
        }
        int xp = expenseCount * XP_PER_EXPENSE;
        int level = levelFor(xp);
        int xpIntoLevel = xp - xpToReach(level);
        int xpForLevel = XP_PER_LEVEL_STEP * level;
        int streakDays = streak(dates, today);
        boolean loggedToday = !dates.isEmpty() && dates.get(0).equals(today);
        long idleDays = dates.isEmpty() ? 3 : ChronoUnit.DAYS.between(dates.get(0), today);
        int hunger = (int) Math.min(100, Math.max(0, idleDays * HUNGER_PER_IDLE_DAY));

        List<BudgetStatus> budgets = Budgets.statuses(conn, r.userId(), YearMonth.from(today));
        BudgetStatus worst = budgets.stream()
                .filter(b -> !b.status().equals("OK"))
                .max((a, b) -> a.percent().compareTo(b.percent()))
                .orElse(null);
        boolean exceeded = worst != null && worst.status().equals("EXCEEDED");
        int moodPercent = Math.max(0, 100 - hunger - (exceeded ? 25 : worst != null ? 10 : 0));

        return new PetStatus(level, TITLES[Math.min(level, TITLES.length) - 1], xp, xpIntoLevel, xpForLevel,
                xpForLevel == 0 ? 0 : xpIntoLevel * 100 / xpForLevel, streakDays, hunger, moodPercent,
                moodLabel(moodPercent), message(expenseCount, loggedToday, streakDays), tip(budgets, worst));
    }

    static String moodLabel(int percent) {
        if (percent >= 75) {
            return "HAPPY";
        }
        if (percent >= 50) {
            return "OKAY";
        }
        return percent >= 25 ? "HUNGRY" : "STARVING";
    }

    private static String message(int expenseCount, boolean loggedToday, int streakDays) {
        if (expenseCount == 0) {
            return "Hi! Log your first expense and I'll start growing.";
        }
        if (!loggedToday) {
            return "I'm hungry! Log today's expenses to feed me.";
        }
        return streakDays >= 3 ? "Yum! " + streakDays + " days in a row, keep it going!"
                : "Thanks for logging today!";
    }

    private static String tip(List<BudgetStatus> budgets, BudgetStatus worst) {
        if (budgets.isEmpty()) {
            return "Set a limit on a category and I'll keep watch for you.";
        }
        if (worst == null) {
            return "All budgets look healthy. Keep it up!";
        }
        return worst.status().equals("EXCEEDED")
                ? "You went over your " + worst.categoryName() + " budget. Time to ease off there."
                : worst.categoryName() + " is at " + worst.percent().stripTrailingZeros().toPlainString()
                        + "% of its limit. Easy does it.";
    }
}
