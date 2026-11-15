package com.termux.app.phonelinux;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** API keys of the phone-control API. Only SHA-256 hashes are stored; the token is shown once at issue time. */
public final class PhoneApiKeys {

    public static final String SCOPE_READ = "read";
    public static final String SCOPE_ACT = "act";
    public static final String SCOPE_OPEN = "open";
    public static final Set<String> ALL_SCOPES = Collections.unmodifiableSet(new TreeSet<>(Arrays.asList(SCOPE_READ, SCOPE_ACT, SCOPE_OPEN)));

    public static final class Key {
        public final String id;
        public final String name;
        final String hash;
        public final Set<String> scopes;
        /** Epoch millis; 0 = never expires. */
        public final long expiresAt;
        public final boolean confirm;
        public final long createdAt;

        Key(String id, String name, String hash, Set<String> scopes, long expiresAt, boolean confirm, long createdAt) {
            this.id = id;
            this.name = name;
            this.hash = hash;
            this.scopes = scopes;
            this.expiresAt = expiresAt;
            this.confirm = confirm;
            this.createdAt = createdAt;
        }

        public boolean isExpired(long now) {
            return expiresAt != 0 && now >= expiresAt;
        }
    }

    /** A token plus the stored record, returned once by {@link #issue}. */
    public static final class Issued {
        public final String token;
        public final Key key;

        Issued(String token, Key key) {
            this.token = token;
            this.key = key;
        }
    }

    private final File file;
    private final SecureRandom random = new SecureRandom();
    private final List<Key> keys = new ArrayList<>();

    public PhoneApiKeys(File file) {
        this.file = file;
        load();
    }

    public static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized Issued issue(String name, Set<String> scopes, long expiresAt, boolean confirm, long now) throws IOException {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        StringBuilder sb = new StringBuilder("plk_");
        for (byte b : raw) sb.append(String.format("%02x", b));
        String token = sb.toString();
        String h = hash(token);
        Key key = new Key(h.substring(0, 8), name, h, Collections.unmodifiableSet(new TreeSet<>(scopes)), expiresAt, confirm, now);
        keys.add(key);
        save();
        return new Issued(token, key);
    }

    public synchronized boolean revoke(String id) throws IOException {
        boolean removed = false;
        for (java.util.Iterator<Key> it = keys.iterator(); it.hasNext(); ) {
            if (it.next().id.equals(id)) {
                it.remove();
                removed = true;
            }
        }
        if (removed) save();
        return removed;
    }

    public synchronized List<Key> list() {
        return new ArrayList<>(keys);
    }

    /** Finds the key for a presented token (constant-time hash comparison, no early exit). Null if unknown. */
    public synchronized Key find(String token) {
        byte[] presented = hash(token).getBytes(StandardCharsets.UTF_8);
        Key found = null;
        for (Key k : keys) {
            if (MessageDigest.isEqual(presented, k.hash.getBytes(StandardCharsets.UTF_8))) found = k;
        }
        return found;
    }

    private void load() {
        if (!file.isFile()) return;
        try {
            JSONArray arr = new JSONArray(readAll());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Set<String> scopes = new TreeSet<>();
                JSONArray s = o.getJSONArray("scopes");
                for (int j = 0; j < s.length(); j++) scopes.add(s.getString(j));
                String h = o.getString("hash");
                keys.add(new Key(h.substring(0, 8), o.getString("name"), h, Collections.unmodifiableSet(scopes),
                    o.optLong("expiresAt", 0), o.optBoolean("confirm", false), o.optLong("createdAt", 0)));
            }
        } catch (IOException | JSONException e) {
            keys.clear();
        }
    }

    private String readAll() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void save() throws IOException {
        try {
            JSONArray arr = new JSONArray();
            for (Key k : keys) {
                arr.put(new JSONObject().put("name", k.name).put("hash", k.hash).put("scopes", new JSONArray(k.scopes))
                    .put("expiresAt", k.expiresAt).put("confirm", k.confirm).put("createdAt", k.createdAt));
            }
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(file) && !(file.delete() && tmp.renameTo(file))) throw new IOException("rename failed");
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
