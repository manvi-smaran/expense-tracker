package com.expensetracker.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Boots the real server on a free port against an in-memory H2 and drives it over HTTP. */
class ApiTest {
    private static HttpApp app;
    private static String base;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        Db db = new Db(new DbConfig("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "", 4));
        app = new HttpApp(Main.buildRouter(db), db);
        base = "http://localhost:" + app.start(0);
    }

    @AfterAll
    static void stop() {
        app.stop();
    }

    private record Resp(int status, String body) {
        JsonObject obj() { return JsonParser.parseString(body).getAsJsonObject(); }
        JsonArray arr() { return JsonParser.parseString(body).getAsJsonArray(); }
    }

    private static Resp call(String method, String path, String token, String json) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) b.header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        var r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private static String newUser() throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        Resp r = call("POST", "/api/auth/register", null,
                "{\"name\":\"Tester\",\"email\":\"" + email + "\",\"password\":\"secret1\"}");
        assertEquals(201, r.status());
        return r.obj().get("token").getAsString();
    }

    @Test
    @DisplayName("protected routes reject missing or bad tokens with the exact message")
    void unauthenticated() throws Exception {
        Resp r = call("GET", "/api/me", null, null);
        assertEquals(401, r.status());
        assertEquals("Log in required", r.obj().get("error").getAsString());
        assertEquals(401, call("GET", "/api/me", "bogus", null).status());
    }

    @Test
    @DisplayName("register validates input, rejects duplicates, login works")
    void authFlow() throws Exception {
        assertEquals(400, call("POST", "/api/auth/register", null,
                "{\"name\":\"\",\"email\":\"a@b.co\",\"password\":\"secret1\"}").status());
        assertEquals(400, call("POST", "/api/auth/register", null,
                "{\"name\":\"A\",\"email\":\"not-an-email\",\"password\":\"secret1\"}").status());
        assertEquals(400, call("POST", "/api/auth/register", null,
                "{\"name\":\"A\",\"email\":\"a@b.co\",\"password\":\"123\"}").status());
        String email = UUID.randomUUID() + "@test.com";
        String body = "{\"name\":\"A\",\"email\":\"" + email + "\",\"password\":\"secret1\"}";
        assertEquals(201, call("POST", "/api/auth/register", null, body).status());
        assertEquals(409, call("POST", "/api/auth/register", null, body).status());
        assertEquals(200, call("POST", "/api/auth/login", null,
                "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"secret1\"}").status());
        assertEquals(401, call("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"wrong\"}").status());
    }

    @Test
    @DisplayName("saving past the budget threshold returns a WARNING, then EXCEEDED alert")
    void budgetAlerts() throws Exception {
        String t = newUser();
        int food = call("GET", "/api/categories", t, null).arr().get(0).getAsJsonObject().get("id").getAsInt();
        assertEquals(204, call("PUT", "/api/budgets/" + food, t, "{\"limit\":100,\"alertThresholdPercent\":80}").status());
        String date = LocalDate.now().toString();
        String first = "{\"categoryId\":" + food + ",\"amount\":50,\"description\":\"a\",\"date\":\"" + date + "\"}";
        assertTrue(call("POST", "/api/expenses", t, first).obj().get("alert") == null);
        String second = "{\"categoryId\":" + food + ",\"amount\":35,\"description\":\"b\",\"date\":\"" + date + "\"}";
        assertEquals("WARNING", call("POST", "/api/expenses", t, second).obj().getAsJsonObject("alert").get("status").getAsString());
        String third = "{\"categoryId\":" + food + ",\"amount\":30,\"description\":\"c\",\"date\":\"" + date + "\"}";
        JsonObject alert = call("POST", "/api/expenses", t, third).obj().getAsJsonObject("alert");
        assertEquals("EXCEEDED", alert.get("status").getAsString());
        assertEquals(115, alert.get("percentUsed").getAsInt());
    }

    @Test
    @DisplayName("expenses are validated, paginated with clamping, and private per user")
    void expensesPagingAndIsolation() throws Exception {
        String t = newUser();
        String date = LocalDate.now().toString();
        assertEquals(400, call("POST", "/api/expenses", t,
                "{\"amount\":0,\"description\":\"x\",\"date\":\"" + date + "\"}").status());
        assertEquals(400, call("POST", "/api/expenses", t,
                "{\"amount\":5,\"description\":\"\",\"date\":\"" + date + "\"}").status());
        assertEquals(400, call("POST", "/api/expenses", t,
                "{\"amount\":5,\"description\":\"x\",\"date\":\"31-12-2026\"}").status());
        for (int i = 0; i < 12; i++) {
            assertEquals(201, call("POST", "/api/expenses", t,
                    "{\"amount\":1.005,\"description\":\"e" + i + "\",\"date\":\"" + date + "\"}").status());
        }
        JsonObject page = call("GET", "/api/expenses?page=99&pageSize=10", t, null).obj();
        assertEquals(2, page.get("page").getAsInt());
        assertEquals(2, page.get("totalPages").getAsInt());
        assertEquals(12, page.get("totalItems").getAsInt());
        assertEquals(2, page.getAsJsonArray("rows").size());
        assertEquals("1.01", page.getAsJsonArray("rows").get(0).getAsJsonObject()
                .getAsJsonObject("expense").get("amount").getAsString());   // HALF_UP, 2 decimals

        int id = page.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonObject("expense").get("id").getAsInt();
        String other = newUser();
        assertEquals(404, call("DELETE", "/api/expenses/" + id, other, null).status());
        assertEquals(204, call("DELETE", "/api/expenses/" + id, t, null).status());
    }

    @Test
    @DisplayName("custom categories: built-ins are protected, purge unlinks expenses")
    void categories() throws Exception {
        String t = newUser();
        String date = LocalDate.now().toString();
        assertEquals(201, call("POST", "/api/categories", t, "{\"name\":\"Gym\",\"color\":\"#112233\",\"icon\":\"bolt\",\"limit\":40}").status());
        assertEquals(409, call("POST", "/api/categories", t, "{\"name\":\"gym\",\"color\":\"#112233\",\"icon\":\"bolt\"}").status());
        assertEquals(400, call("POST", "/api/categories", t, "{\"name\":\"X\",\"color\":\"red\",\"icon\":\"bolt\"}").status());
        JsonArray cats = call("GET", "/api/categories", t, null).arr();
        int gym = -1, builtIn = -1;
        for (var e : cats) {
            JsonObject c = e.getAsJsonObject();
            if (c.get("name").getAsString().equals("Gym")) gym = c.get("id").getAsInt();
            if (c.get("builtIn").getAsBoolean()) builtIn = c.get("id").getAsInt();
        }
        assertEquals(40, call("GET", "/api/categories", t, null).arr().asList().stream()
                .map(x -> x.getAsJsonObject()).filter(c -> c.get("name").getAsString().equals("Gym"))
                .findFirst().get().get("monthlyLimit").getAsInt());
        call("POST", "/api/expenses", t, "{\"categoryId\":" + gym + ",\"amount\":9,\"description\":\"p\",\"date\":\"" + date + "\"}");
        assertEquals(1, call("GET", "/api/categories/" + gym + "/unlink-count", t, null).obj().get("count").getAsInt());
        assertEquals(1, call("POST", "/api/categories/purge", t, "{\"ids\":[" + gym + "," + builtIn + "]}")
                .obj().get("deleted").getAsInt());   // the built-in is skipped
        JsonArray rows = call("GET", "/api/expenses", t, null).obj().getAsJsonArray("rows");
        assertTrue(rows.get(0).getAsJsonObject().getAsJsonObject("expense").get("categoryId") == null);
    }

    @Test
    @DisplayName("process-due turns due subscriptions into expenses and advances the date")
    void subscriptions() throws Exception {
        String t = newUser();
        assertEquals(400, call("POST", "/api/subscriptions", t,
                "{\"name\":\"X\",\"amount\":5,\"frequency\":\"DAILY\",\"nextDueDate\":\"2026-01-01\"}").status());
        assertEquals(201, call("POST", "/api/subscriptions", t,
                "{\"name\":\"Netflix\",\"amount\":12,\"frequency\":\"MONTHLY\",\"nextDueDate\":\"2026-09-10\"}").status());
        JsonObject ov = call("GET", "/api/subscriptions?date=2026-11-25", t, null).obj();
        assertEquals(1, ov.get("activeCount").getAsInt());
        assertEquals(12, ov.get("dueTotal").getAsInt());
        assertEquals(-76, ov.getAsJsonArray("items").get(0).getAsJsonObject().get("daysUntilDue").getAsInt());
        // Sept 10, Oct 10, Nov 10 are all due by Nov 25 -> 3 expenses
        assertEquals(3, call("POST", "/api/subscriptions/process-due?date=2026-11-25", t, null).obj().get("created").getAsInt());
        assertEquals(0, call("POST", "/api/subscriptions/process-due?date=2026-11-25", t, null).obj().get("created").getAsInt());
        assertEquals("2026-12-10", call("GET", "/api/subscriptions?date=2026-11-25", t, null).obj()
                .getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("subscription").get("nextDueDate").getAsString());
    }

    @Test
    @DisplayName("charts group by category with Uncategorized, and the pet derives XP and streak")
    void chartsAndPet() throws Exception {
        String t = newUser();
        LocalDate today = LocalDate.of(2026, 11, 25);
        for (int d = 0; d < 3; d++) {
            call("POST", "/api/expenses", t, "{\"amount\":10,\"description\":\"x\",\"date\":\"" + today.minusDays(d) + "\"}");
        }
        JsonObject month = call("GET", "/api/charts/month?month=2026-11", t, null).obj();
        assertEquals(30, month.get("total").getAsInt());
        assertEquals("Uncategorized", month.getAsJsonArray("slices").get(0).getAsJsonObject().getAsJsonObject("spend").get("name").getAsString());
        assertEquals(3, call("GET", "/api/charts/category-expenses?month=2026-11&limit=5", t, null).arr().size());
        JsonObject pet = call("GET", "/api/pet?date=2026-11-25", t, null).obj();
        assertEquals(30, pet.get("xp").getAsInt());
        assertEquals(3, pet.get("streakDays").getAsInt());
        assertEquals(1, pet.get("level").getAsInt());   // 30 XP < 50 needed for level 2
    }

    @Test
    @DisplayName("unknown paths and wrong methods return JSON errors")
    void routing() throws Exception {
        assertEquals("Not found", call("GET", "/nope", null, null).obj().get("error").getAsString());
        assertEquals(405, call("DELETE", "/api/me", newUser(), null).status());
        assertEquals(400, call("POST", "/api/auth/login", null, "{bad").status());
    }
}
