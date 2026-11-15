package com.termux.app.phonelinux;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class PhoneApiTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private PhoneApiKeys keys;
    private final List<String> calls = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private boolean confirmAnswer = true;
    private int confirmCount;
    private long now = 1_000_000;
    private PhoneApiHandler handler;

    private final PhoneActions actions = new PhoneActions() {
        public byte[] screenshotPng() {
            calls.add("screenshot");
            return new byte[]{1, 2, 3};
        }

        public String uiTree() {
            calls.add("ui");
            return "{\"root\":{}}";
        }

        public void tap(int x, int y) {
            calls.add("tap " + x + "," + y);
        }

        public void swipe(int x1, int y1, int x2, int y2, int ms) {
            calls.add("swipe " + x1 + "," + y1 + "," + x2 + "," + y2 + "," + ms);
        }

        public void type(String text, boolean append) {
            calls.add("type " + text + " " + append);
        }

        public void key(String name) {
            calls.add("key " + name);
        }

        public void open(String pkg, String url) {
            calls.add("open " + pkg + " " + url);
        }
    };

    @Before
    public void setUp() throws Exception {
        keys = new PhoneApiKeys(new File(tmp.getRoot(), "keys.json"));
        handler = new PhoneApiHandler(keys, actions, (name, desc) -> {
            confirmCount++;
            return confirmAnswer;
        }, logs::add, () -> now);
    }

    private PhoneApiKeys.Issued issue(String scopes, long expiresAt, boolean confirm) throws Exception {
        TreeSet<String> s = new TreeSet<>();
        Collections.addAll(s, scopes.split(","));
        return keys.issue("test key", s, expiresAt, confirm, now);
    }

    private PhoneApiHandler.Response call(String method, String path, String token, String body) {
        Map<String, String> h = new HashMap<>();
        if (token != null) h.put("authorization", "Bearer " + token);
        return handler.handle(new PhoneApiHandler.Request(method, path, h, body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8)));
    }

    // --- parsing ---

    private static PhoneApiHandler.Request parse(String raw) throws Exception {
        return PhoneApiHandler.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void parsesRequestWithBodyAndQuery() throws Exception {
        PhoneApiHandler.Request r = parse("POST /v1/tap?x=1 HTTP/1.1\r\nHost: a\r\nAuthorization: Bearer abc\r\nContent-Length: 13\r\n\r\n{\"x\":1,\"y\":2}");
        assertEquals("POST", r.method);
        assertEquals("/v1/tap", r.path);
        assertEquals("Bearer abc", r.headers.get("authorization"));
        assertEquals("{\"x\":1,\"y\":2}", new String(r.body, StandardCharsets.UTF_8));
    }

    @Test
    public void parseRejectsMalformedInput() throws Exception {
        assertParseStatus("GARBAGE\r\n\r\n", 400);
        assertParseStatus("GET / HTTP/1.1\r\n", 400);
        assertParseStatus("POST /v1/tap HTTP/1.1\r\nContent-Length: 999999\r\n\r\n", 413);
        assertParseStatus("POST /v1/tap HTTP/1.1\r\nContent-Length: x\r\n\r\n", 400);
        assertParseStatus("POST /v1/tap HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n", 501);
        assertParseStatus("POST /v1/tap HTTP/1.1\r\nContent-Length: 5\r\n\r\nab", 400);
        StringBuilder big = new StringBuilder("GET / HTTP/1.1\r\n");
        for (int i = 0; i < 2000; i++) big.append("X-A: bbbbbbbbbb\r\n");
        assertParseStatus(big.toString(), 431);
    }

    private void assertParseStatus(String raw, int status) throws Exception {
        try {
            parse(raw);
            fail("expected " + status);
        } catch (PhoneApiHandler.BadRequest e) {
            assertEquals(status, e.status);
        }
    }

    @Test
    public void responseSerializes() {
        String wire = new String(call("GET", "/v1/health", null, null).toBytes(), StandardCharsets.ISO_8859_1);
        assertTrue(wire.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue(wire.contains("Content-Length: 11\r\n"));
        assertTrue(wire.endsWith("{\"ok\":true}"));
    }

    // --- keys ---

    @Test
    public void keyHashingAndStorage() throws Exception {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PhoneApiKeys.hash("abc"));
        PhoneApiKeys.Issued k = issue("read", 0, false);
        assertTrue(k.token.matches("plk_[0-9a-f]{64}"));
        String stored = new String(java.nio.file.Files.readAllBytes(new File(tmp.getRoot(), "keys.json").toPath()), StandardCharsets.UTF_8);
        assertFalse("token must not be stored", stored.contains(k.token));
        assertTrue(stored.contains(PhoneApiKeys.hash(k.token)));
        PhoneApiKeys reloaded = new PhoneApiKeys(new File(tmp.getRoot(), "keys.json"));
        assertEquals(1, reloaded.list().size());
        assertNotNull(reloaded.find(k.token));
        assertNull(reloaded.find(k.token + "0"));
        assertEquals(k.key.id, reloaded.list().get(0).id);
    }

    @Test
    public void tokensAreUnique() throws Exception {
        assertTrue(!issue("read", 0, false).token.equals(issue("read", 0, false).token));
    }

    @Test
    public void revokeRemovesKey() throws Exception {
        PhoneApiKeys.Issued k = issue("read", 0, false);
        assertEquals(200, call("GET", "/v1/ui", k.token, null).status);
        assertTrue(keys.revoke(k.key.id));
        assertFalse(keys.revoke(k.key.id));
        assertEquals(401, call("GET", "/v1/ui", k.token, null).status);
    }

    // --- auth / scope / expiry ---

    @Test
    public void rejectsMissingAndUnknownKeys() throws Exception {
        issue("read", 0, false);
        assertEquals(401, call("GET", "/v1/ui", null, null).status);
        assertEquals(401, call("GET", "/v1/ui", "plk_wrong", null).status);
        Map<String, String> h = new HashMap<>();
        h.put("authorization", "Basic abc");
        assertEquals(401, handler.handle(new PhoneApiHandler.Request("GET", "/v1/ui", h, new byte[0])).status);
        assertTrue(calls.isEmpty());
    }

    @Test
    public void expiredKeyIsRejected() throws Exception {
        PhoneApiKeys.Issued k = issue("read", now + 1000, false);
        assertEquals(200, call("GET", "/v1/ui", k.token, null).status);
        now += 1000;
        assertEquals(401, call("GET", "/v1/ui", k.token, null).status);
    }

    @Test
    public void scopesAreEnforced() throws Exception {
        String read = issue("read", 0, false).token;
        assertEquals(200, call("GET", "/v1/screenshot", read, null).status);
        assertEquals(403, call("POST", "/v1/tap", read, "{\"x\":1,\"y\":2}").status);
        assertEquals(403, call("POST", "/v1/open", read, "{\"package\":\"a.b\"}").status);
        String act = issue("act", 0, false).token;
        assertEquals(403, call("GET", "/v1/ui", act, null).status);
        assertEquals(200, call("POST", "/v1/key", act, "{\"name\":\"back\"}").status);
        assertEquals(403, call("POST", "/v1/open", act, "{\"package\":\"a.b\"}").status);
        String open = issue("open", 0, false).token;
        assertEquals(200, call("POST", "/v1/open", open, "{\"url\":\"https://example.com/x\"}").status);
        assertEquals(403, call("POST", "/v1/type", open, "{\"text\":\"a\"}").status);
        assertFalse(calls.contains("tap 1,2"));
    }

    @Test
    public void routing() throws Exception {
        String all = issue("read,act,open", 0, false).token;
        assertEquals(404, call("GET", "/v1/nope", all, null).status);
        assertEquals(405, call("POST", "/v1/ui", all, null).status);
        assertEquals(405, call("GET", "/v1/tap", all, null).status);
        assertEquals(405, call("POST", "/v1/health", null, null).status);
        assertEquals(200, call("GET", "/v1/health", null, null).status);
        PhoneApiHandler.Response png = call("GET", "/v1/screenshot", all, null);
        assertEquals("image/png", png.contentType);
        assertArrayEquals(new byte[]{1, 2, 3}, png.body);
        assertEquals("{\"root\":{}}", call("GET", "/v1/ui", all, null).text());
    }

    @Test
    public void actionsReceiveParsedArguments() throws Exception {
        String all = issue("read,act,open", 0, false).token;
        assertEquals(200, call("POST", "/v1/tap", all, "{\"x\":10,\"y\":20}").status);
        assertEquals(200, call("POST", "/v1/swipe", all, "{\"x1\":1,\"y1\":2,\"x2\":3,\"y2\":4}").status);
        assertEquals(200, call("POST", "/v1/swipe", all, "{\"x1\":1,\"y1\":2,\"x2\":3,\"y2\":4,\"ms\":99999}").status);
        assertEquals(200, call("POST", "/v1/type", all, "{\"text\":\"hi\",\"append\":true}").status);
        assertEquals(200, call("POST", "/v1/open", all, "{\"package\":\"com.x\"}").status);
        assertEquals("[tap 10,20, swipe 1,2,3,4,300, swipe 1,2,3,4,10000, type hi true, open com.x null]", calls.toString());
    }

    @Test
    public void badArgumentsAre400() throws Exception {
        String all = issue("read,act,open", 0, false).token;
        assertEquals(400, call("POST", "/v1/tap", all, "{\"x\":1}").status);
        assertEquals(400, call("POST", "/v1/tap", all, "{\"x\":\"1\",\"y\":2}").status);
        assertEquals(400, call("POST", "/v1/tap", all, "not json").status);
        assertEquals(400, call("POST", "/v1/key", all, "{\"name\":\"power\"}").status);
        assertEquals(400, call("POST", "/v1/type", all, "{}").status);
        assertEquals(400, call("POST", "/v1/open", all, "{}").status);
        assertEquals(400, call("POST", "/v1/open", all, "{\"package\":\"a\",\"url\":\"https://x\"}").status);
        assertEquals(400, call("POST", "/v1/open", all, "{\"url\":\"file:///etc/passwd\"}").status);
        assertEquals(400, call("POST", "/v1/open", all, "{\"url\":\"intent:#Intent;end\"}").status);
        assertTrue(calls.isEmpty());
    }

    @Test
    public void actionErrorsKeepTheirStatus() throws Exception {
        PhoneActions failing = new PhoneActions() {
            public byte[] screenshotPng() throws ActionException {
                throw new ActionException(501, "needs Android 11");
            }

            public String uiTree() {
                return "{}";
            }

            public void tap(int x, int y) {
            }

            public void swipe(int x1, int y1, int x2, int y2, int ms) {
            }

            public void type(String text, boolean append) {
            }

            public void key(String name) {
            }

            public void open(String pkg, String url) {
            }
        };
        handler = new PhoneApiHandler(keys, failing, (n, d) -> true, logs::add, () -> now);
        PhoneApiHandler.Response r = call("GET", "/v1/screenshot", issue("read", 0, false).token, null);
        assertEquals(501, r.status);
        assertTrue(r.text().contains("needs Android 11"));
    }

    // --- confirmation ---

    @Test
    public void confirmKeyAsksBeforeActingAndOnlyForActAndOpen() throws Exception {
        String token = issue("read,act,open", 0, true).token;
        assertEquals(200, call("GET", "/v1/ui", token, null).status);
        assertEquals(0, confirmCount);
        confirmAnswer = false;
        assertEquals(403, call("POST", "/v1/tap", token, "{\"x\":1,\"y\":2}").status);
        assertEquals(403, call("POST", "/v1/open", token, "{\"package\":\"a\"}").status);
        assertEquals(2, confirmCount);
        assertFalse(calls.toString().contains("tap"));
        confirmAnswer = true;
        assertEquals(200, call("POST", "/v1/tap", token, "{\"x\":1,\"y\":2}").status);
        assertTrue(calls.contains("tap 1,2"));
    }

    @Test
    public void nonConfirmKeyNeverAsks() throws Exception {
        assertEquals(200, call("POST", "/v1/key", issue("act", 0, false).token, "{\"name\":\"home\"}").status);
        assertEquals(0, confirmCount);
    }

    // --- rate limit / log ---

    @Test
    public void perKeyRateLimit() throws Exception {
        String token = issue("read", 0, false).token;
        for (int i = 0; i < 20; i++) assertEquals(200, call("GET", "/v1/ui", token, null).status);
        assertEquals(429, call("GET", "/v1/ui", token, null).status);
        now += 1000;
        assertEquals(200, call("GET", "/v1/ui", token, null).status);
    }

    @Test
    public void repeatedAuthFailuresAreThrottledEvenForValidKeys() throws Exception {
        String token = issue("read", 0, false).token;
        for (int i = 0; i < 10; i++) assertEquals(401, call("GET", "/v1/ui", "bad" + i, null).status);
        assertEquals(429, call("GET", "/v1/ui", token, null).status);
        now += 60_000;
        assertEquals(200, call("GET", "/v1/ui", token, null).status);
    }

    @Test
    public void logHasNoSecretsAndNoTypedText() throws Exception {
        String token = issue("act", 0, false).token;
        call("POST", "/v1/type", token, "{\"text\":\"my-secret-password\"}");
        call("GET", "/v1/ui", "plk_guess", null);
        String all = String.join("\n", logs);
        assertTrue(all.contains("len=18"));
        assertTrue(all.contains("/v1/type -> 200"));
        assertTrue(all.contains("-> 401"));
        assertFalse(all.contains("my-secret-password"));
        assertFalse(all.contains(token));
        assertFalse(all.contains("plk_guess"));
    }

    @Test
    public void limiterWindowSlides() {
        PhoneRateLimiter l = new PhoneRateLimiter(2, 100);
        assertTrue(l.tryAcquire("a", 0));
        assertTrue(l.tryAcquire("a", 10));
        assertFalse(l.tryAcquire("a", 20));
        assertTrue(l.tryAcquire("b", 20));
        assertTrue(l.tryAcquire("a", 100));
    }
}
