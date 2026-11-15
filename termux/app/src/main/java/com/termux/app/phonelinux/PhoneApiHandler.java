package com.termux.app.phonelinux;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/** HTTP parsing, authentication, routing and logging of the phone-control API. No Android dependencies. */
public final class PhoneApiHandler {

    public static final int MAX_HEADER = 16 * 1024;
    public static final int MAX_BODY = 64 * 1024;

    public interface Confirmer {
        /** Blocks until the user answers; false = denied or timed out. */
        boolean confirm(String keyName, String description);
    }

    public interface Log {
        void line(String line);
    }

    public static final class Request {
        public final String method;
        public final String path;
        public final Map<String, String> headers;
        public final byte[] body;

        public Request(String method, String path, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }
    }

    public static final class Response {
        public final int status;
        public final String contentType;
        public final byte[] body;

        Response(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public byte[] toBytes() {
            String head = "HTTP/1.1 " + status + " " + reason(status) + "\r\nContent-Type: " + contentType
                + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] h = head.getBytes(StandardCharsets.ISO_8859_1);
            out.write(h, 0, h.length);
            out.write(body, 0, body.length);
            return out.toByteArray();
        }
    }

    /** Malformed request; carries the status to answer with. */
    public static final class BadRequest extends IOException {
        public final int status;

        BadRequest(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final PhoneApiKeys keys;
    private final PhoneActions actions;
    private final Confirmer confirmer;
    private final Log log;
    private final LongSupplier clock;
    private final PhoneRateLimiter perKey = new PhoneRateLimiter(20, 1000);
    private final PhoneRateLimiter failures = new PhoneRateLimiter(10, 60_000);
    private final Object actionLock = new Object();

    public PhoneApiHandler(PhoneApiKeys keys, PhoneActions actions, Confirmer confirmer, Log log, LongSupplier clock) {
        this.keys = keys;
        this.actions = actions;
        this.confirmer = confirmer;
        this.log = log;
        this.clock = clock;
    }

    public static Request parse(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0; // number of bytes of \r\n\r\n matched so far
        while (state < 4) {
            int b = in.read();
            if (b < 0) throw new BadRequest(400, "unexpected end of request");
            head.write(b);
            if (head.size() > MAX_HEADER) throw new BadRequest(431, "headers too large");
            state = b == (state % 2 == 0 ? '\r' : '\n') ? state + 1 : (b == '\r' ? 1 : 0);
        }
        String[] lines = new String(head.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
        String[] rl = lines[0].split(" ");
        if (rl.length != 3 || !rl[2].startsWith("HTTP/1.")) throw new BadRequest(400, "bad request line");
        String path;
        try {
            path = new URI(rl[1]).getPath();
        } catch (Exception e) {
            throw new BadRequest(400, "bad path");
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
        }
        if (headers.containsKey("transfer-encoding")) throw new BadRequest(501, "chunked bodies are not supported");
        int len = 0;
        if (headers.containsKey("content-length")) {
            try {
                len = Integer.parseInt(headers.get("content-length"));
            } catch (NumberFormatException e) {
                throw new BadRequest(400, "bad content-length");
            }
            if (len < 0) throw new BadRequest(400, "bad content-length");
            if (len > MAX_BODY) throw new BadRequest(413, "body too large");
        }
        byte[] body = new byte[len];
        for (int off = 0; off < len; ) {
            int n = in.read(body, off, len - off);
            if (n < 0) throw new BadRequest(400, "unexpected end of body");
            off += n;
        }
        return new Request(rl[0], path == null ? "" : path, headers, body);
    }

    public Response handle(Request req) {
        long now = clock.getAsLong();
        if (req.path.equals("/v1/health")) {
            return req.method.equals("GET") ? json(200, kv("ok", true)) : error(405, "method not allowed");
        }
        if (failures.isFull("fail", now)) return done(null, req, error(429, "too many failed attempts"), "");
        String auth = req.headers.get("authorization");
        PhoneApiKeys.Key key = null;
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) key = keys.find(auth.substring(7).trim());
        if (key == null || key.isExpired(now)) {
            failures.tryAcquire("fail", now);
            return done(null, req, error(401, key == null ? "missing or unknown key" : "key expired"), "");
        }
        if (!perKey.tryAcquire(key.id, now)) return done(key, req, error(429, "rate limit exceeded"), "");

        String scope;
        String method;
        switch (req.path) {
            case "/v1/screenshot":
            case "/v1/ui":
                scope = PhoneApiKeys.SCOPE_READ;
                method = "GET";
                break;
            case "/v1/tap":
            case "/v1/swipe":
            case "/v1/type":
            case "/v1/key":
                scope = PhoneApiKeys.SCOPE_ACT;
                method = "POST";
                break;
            case "/v1/open":
                scope = PhoneApiKeys.SCOPE_OPEN;
                method = "POST";
                break;
            default:
                return done(key, req, error(404, "not found"), "");
        }
        if (!req.method.equals(method)) return done(key, req, error(405, "method not allowed"), "");
        if (!key.scopes.contains(scope)) return done(key, req, error(403, "key lacks scope: " + scope), "");

        JSONObject body = new JSONObject();
        if (method.equals("POST")) {
            try {
                body = new JSONObject(req.body.length == 0 ? "{}" : new String(req.body, StandardCharsets.UTF_8));
            } catch (JSONException e) {
                return done(key, req, error(400, "body must be a JSON object"), "");
            }
        }
        Response r;
        String[] detail = {""};
        try {
            r = run(key, req.path, body, detail);
        } catch (BadArg e) {
            r = error(400, e.getMessage());
        } catch (PhoneActions.ActionException e) {
            r = error(e.status, e.getMessage());
        } catch (RuntimeException e) {
            r = error(500, "internal error: " + e.getClass().getSimpleName());
        }
        return done(key, req, r, detail[0]);
    }

    private Response run(PhoneApiKeys.Key key, String path, JSONObject body, String[] detail) throws PhoneActions.ActionException {
        switch (path) {
            case "/v1/screenshot":
                synchronized (actionLock) {
                    return new Response(200, "image/png", actions.screenshotPng());
                }
            case "/v1/ui":
                synchronized (actionLock) {
                    return new Response(200, "application/json; charset=utf-8", actions.uiTree().getBytes(StandardCharsets.UTF_8));
                }
            case "/v1/tap": {
                int x = num(body, "x"), y = num(body, "y");
                detail[0] = "x=" + x + " y=" + y;
                return act(key, "タップ (" + x + ", " + y + ")", () -> actions.tap(x, y));
            }
            case "/v1/swipe": {
                int x1 = num(body, "x1"), y1 = num(body, "y1"), x2 = num(body, "x2"), y2 = num(body, "y2");
                int ms = body.has("ms") ? Math.max(1, Math.min(10_000, num(body, "ms"))) : 300;
                detail[0] = "x1=" + x1 + " y1=" + y1 + " x2=" + x2 + " y2=" + y2 + " ms=" + ms;
                return act(key, "スワイプ (" + x1 + ", " + y1 + ") → (" + x2 + ", " + y2 + ")", () -> actions.swipe(x1, y1, x2, y2, ms));
            }
            case "/v1/type": {
                if (!(body.opt("text") instanceof String)) throw new BadArg("text must be a string");
                String text = body.optString("text");
                boolean append = body.optBoolean("append", false);
                detail[0] = "len=" + text.length();
                return act(key, "文字入力 (" + text.length() + " 文字)", () -> actions.type(text, append));
            }
            case "/v1/key": {
                String name = body.optString("name", "");
                if (!name.matches("back|home|recents|notifications")) throw new BadArg("name must be back|home|recents|notifications");
                detail[0] = "name=" + name;
                return act(key, "キー操作: " + name, () -> actions.key(name));
            }
            default: {
                String pkg = body.opt("package") instanceof String ? body.optString("package") : null;
                String url = body.opt("url") instanceof String ? body.optString("url") : null;
                if ((pkg == null) == (url == null)) throw new BadArg("give exactly one of package or url");
                if (url != null && !url.matches("(?is)(https?|mailto|geo|tel):.+")) throw new BadArg("url scheme not allowed");
                detail[0] = pkg != null ? "package=" + pkg : "url-scheme=" + url.substring(0, url.indexOf(':')).toLowerCase(Locale.ROOT);
                return act(key, "開く: " + (pkg != null ? pkg : url.split("[?#]")[0]), () -> actions.open(pkg, url));
            }
        }
    }

    private interface Op {
        void run() throws PhoneActions.ActionException;
    }

    private static final class BadArg extends RuntimeException {
        BadArg(String m) {
            super(m);
        }
    }

    private Response act(PhoneApiKeys.Key key, String description, Op op) throws PhoneActions.ActionException {
        if (key.confirm && !confirmer.confirm(key.name, description)) return error(403, "denied by user");
        synchronized (actionLock) {
            op.run();
        }
        return json(200, kv("ok", true));
    }

    private static int num(JSONObject o, String name) {
        Object v = o.opt(name);
        if (!(v instanceof Number)) throw new BadArg(name + " must be a number");
        return ((Number) v).intValue();
    }

    private Response done(PhoneApiKeys.Key key, Request req, Response r, String detail) {
        log.line(String.format(Locale.ROOT, "%tFT%<tT%<tz key=%s %s %s -> %d %s", clock.getAsLong(),
            key == null ? "-" : key.name.replaceAll("\\s", "_"), req.method.replaceAll("[^A-Z]", "?"),
            req.path.replaceAll("[^\\x21-\\x7e]", "?"), r.status, detail).trim());
        return r;
    }

    private static JSONObject kv(String k, Object v) {
        try {
            return new JSONObject().put(k, v);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Response json(int status, JSONObject o) {
        return new Response(status, "application/json; charset=utf-8", o.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Response error(int status, String message) {
        return json(status, kv("error", message));
    }

    static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 413: return "Payload Too Large";
            case 429: return "Too Many Requests";
            case 431: return "Request Header Fields Too Large";
            case 501: return "Not Implemented";
            default: return status >= 500 ? "Server Error" : "Error";
        }
    }
}
