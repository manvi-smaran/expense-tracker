package com.expensetracker.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maps "METHOD /path/{var}" patterns to handlers. */
public final class Router {
    @FunctionalInterface
    public interface Handler {
        Result handle(Req req);
    }

    private record Route(String method, String[] segments, boolean authRequired, Handler handler) {}

    public record Match(Handler handler, boolean authRequired, Map<String, String> pathVars) {}

    private final List<Route> routes = new ArrayList<>();

    public Router secured(String method, String pattern, Handler handler) {
        routes.add(new Route(method, split(pattern), true, handler));
        return this;
    }

    public Router open(String method, String pattern, Handler handler) {
        routes.add(new Route(method, split(pattern), false, handler));
        return this;
    }

    /** Throws 404 if no path matches, 405 if the path matches under other methods only. */
    public Match find(String method, String path) {
        String[] actual = split(path);
        boolean pathKnown = false;
        for (Route route : routes) {
            Map<String, String> vars = match(route.segments(), actual);
            if (vars == null) {
                continue;
            }
            pathKnown = true;
            if (route.method().equals(method)) {
                return new Match(route.handler(), route.authRequired(), vars);
            }
        }
        throw pathKnown ? new ApiException(405, "Method not allowed") : ApiException.notFound("Not found");
    }

    private static Map<String, String> match(String[] pattern, String[] actual) {
        if (pattern.length != actual.length) {
            return null;
        }
        Map<String, String> vars = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            String p = pattern[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                vars.put(p.substring(1, p.length() - 1), actual[i]);
            } else if (!p.equals(actual[i])) {
                return null;
            }
        }
        return vars;
    }

    private static String[] split(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }
}
