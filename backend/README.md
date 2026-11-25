# Expense Tracker backend (plain Java `HttpServer`)

No Spring, no framework: `com.sun.net.httpserver.HttpServer` + JDBC + HikariCP + Gson. Implements all 23
endpoints the Android app's `ApiService` calls, with the same JSON shapes.

## Stack
Java 25 · Apache Maven (wrapper included) · JDK `HttpServer` · Gson 2.11.0 · PostgreSQL JDBC 42.7.4 · HikariCP 6.2.1.
Database: PostgreSQL on [Neon](https://neon.tech) when `DATABASE_URL` is set, otherwise a local H2 file. Tests use in-memory H2 in PostgreSQL mode, so they need no server.

## Run
    ./mvnw verify                      # compile + 14 tests + fat jar in target/
    export DATABASE_URL='postgresql://USER:PASSWORD@HOST/DB?sslmode=require'   # Neon "connection string"
    java -jar target/expense-tracker-server.jar        # listens on $PORT (default 8080)

With no `DATABASE_URL` / `DB_URL` set, it uses a local H2 file database: `backend/data/expense-tracker.mv.db` (delete the folder to reset). Set `DATABASE_URL` to use Neon instead.

Tables are created on start-up (`CREATE TABLE IF NOT EXISTS`). Never commit the connection string.
Other settings: `PORT`, `DB_POOL_SIZE` (default 5). Alternative to `DATABASE_URL`: `DB_URL` (a JDBC URL) with `DB_USER` / `DB_PASSWORD`.

## Set up Neon
1. Sign in at neon.tech, create a project, open **Connect**, copy the connection string.
2. Export it as `DATABASE_URL` locally, or set it as an environment variable on Render.
3. Start the server; check `GET /health`, then register a user from the app.

## Point the app at it
Emulator: set `BASE_URL = "http://10.0.2.2:8080/"` in `ApiClient.java` (cleartext is already allowed for 10.0.2.2).
Real phone: use your computer's LAN IP, and add that host to `network_security_config.xml`.

## Layout (`src/main/java/com/expensetracker/server`)
| File | Job |
|---|---|
| `Main`, `HttpApp`, `Router` | start-up, parsing, bearer auth, JSON errors, route matching |
| `Auth`, `Expenses`, `Categories`, `Budgets`, `Charts`, `Subscriptions`, `Pet` | one file per feature |
| `Db`, `Security`, `Dto`, `Req`, `Result`, `ApiException` | schema/transactions, PBKDF2 + tokens, JSON shapes, helpers |

## Behaviour the app doesn't dictate (my choices; change freely)
- Built-in categories per user: Food, Transport, Entertainment, Bills, Other. Their names are locked; colour, icon, limit are editable; purge skips them.
- Budget status: `EXCEEDED` if spent > limit, `WARNING` from the alert threshold (default 80%), else `OK`. Budgets are monthly.
- Pet: 10 XP per expense; level n needs 50*n XP to reach n+1; streak = consecutive logging days; hunger +25 per idle day.
- Passwords: PBKDF2-HMAC-SHA256, 210k iterations. Tokens: 256 random bits, only the SHA-256 is stored, no expiry.
- Subscriptions: `process-due` creates one expense per missed cycle (capped at 120) and advances the due date.
