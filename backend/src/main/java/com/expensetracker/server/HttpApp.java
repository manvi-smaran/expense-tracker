package com.expensetracker.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The HttpServer wrapper: parsing, auth, JSON out, and one place that turns errors into JSON. */
public final class HttpApp {
    private static final Logger LOG = Logger.getLogger(HttpApp.class.getName());
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final String BEARER = "Bearer ";

    private final Router router;
    private final Db db;
    private HttpServer server;

    public HttpApp(Router router, Db db) {
        this.router = router;
        this.db = db;
    }

    /** Binds to the port (0 = any free port) and returns the port actually used. */
    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::serve);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void serve(HttpExchange ex) throws IOException {
        try {
            Result result = dispatch(ex);
            write(ex, result.status(), result.body());
        } catch (ApiException e) {
            write(ex, e.status(), Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unhandled error on " + ex.getRequestMethod() + " " + ex.getRequestURI().getPath(), e);
            write(ex, 500, Map.of("error", "Internal server error"));
        } finally {
            ex.close();
        }
    }

    private Result dispatch(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        Router.Match match = router.find(method, path);
        int userId = match.authRequired() ? authenticate(ex) : 0;
        Req req = new Req(method, path, parseQuery(ex.getRequestURI().getRawQuery()),
                match.pathVars(), readBody(ex), userId);
        return match.handler().handle(req);
    }

    private int authenticate(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw ApiException.unauthorized("Log in required");
        }
        String hash = Security.sha256Hex(header.substring(BEARER.length()).trim());
        Integer userId = db.tx(conn -> {
            try (var ps = conn.prepareStatement("SELECT user_id FROM tokens WHERE token_hash = ?")) {
                ps.setString(1, hash);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : null;
                }
            }
        });
        if (userId == null) {
            throw ApiException.unauthorized("Log in required");
        }
        return userId;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new ApiException(413, "Request too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            out.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return out;
    }

    private static void write(HttpExchange ex, int status, Object body) throws IOException {
        if (body == null || status == 204) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = Json.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}
