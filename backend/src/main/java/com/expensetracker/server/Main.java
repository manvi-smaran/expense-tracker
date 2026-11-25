package com.expensetracker.server;

import java.io.IOException;
import java.util.Map;

public final class Main {
    private Main() {}

    /** Wires every route onto one router. */
    public static Router buildRouter(Db db) {
        Router router = new Router();
        router.open("GET", "/health", r -> Result.ok(Map.of("status", "ok")));
        Auth.register(router, db);
        Expenses.register(router, db);
        Categories.register(router, db);
        Budgets.register(router, db);
        Charts.register(router, db);
        Subscriptions.register(router, db);
        Pet.register(router, db);
        return router;
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "8080"));
        DbConfig config = DbConfig.fromEnv(System.getenv());
        Db db = new Db(config);
        HttpApp app = new HttpApp(buildRouter(db), db);
        int bound = app.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            db.close();
        }));
        System.out.println("Expense Tracker backend listening on http://0.0.0.0:" + bound + "  (db: " + config.jdbcUrl().replaceFirst("\\?.*", "") + ")");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
