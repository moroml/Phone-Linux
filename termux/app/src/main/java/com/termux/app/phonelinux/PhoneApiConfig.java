package com.termux.app.phonelinux;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Paths, port and shared key store of the phone-control API. */
public final class PhoneApiConfig {

    public static final String DIR = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.phone-linux";
    public static final File LOG_FILE = new File(DIR, "phone-api.log");
    public static final File KEY_FILE = new File(DIR, "phone-api-key");
    public static final File PORT_FILE = new File(DIR, "phone-api-port");
    public static final String CLAUDE_KEY_NAME = "Claude Code";

    private static final String PREFS = "phone_linux_phone_api";
    private static PhoneApiKeys keys;

    private PhoneApiConfig() {}

    public static synchronized PhoneApiKeys keys(Context context) {
        if (keys == null) keys = new PhoneApiKeys(new File(context.getFilesDir(), "phone-api-keys.json"));
        return keys;
    }

    public static int port(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("port", PhoneApiServer.DEFAULT_PORT);
    }

    /** Saves the port and publishes it to $HOME/.phone-linux/phone-api-port for phone-mcp. */
    public static void setPort(Context context, int port) throws IOException {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("port", port).apply();
        writePrivate(PORT_FILE, String.valueOf(port));
    }

    /** Replaces the "Claude Code" key with a fresh read+act+open key (no confirmation) and writes its token to the key file. */
    public static void issueClaudeKey(Context context) throws IOException {
        PhoneApiKeys store = keys(context);
        for (PhoneApiKeys.Key k : store.list()) if (k.name.equals(CLAUDE_KEY_NAME)) store.revoke(k.id);
        PhoneApiKeys.Issued issued = store.issue(CLAUDE_KEY_NAME, PhoneApiKeys.ALL_SCOPES, 0, false, System.currentTimeMillis());
        writePrivate(KEY_FILE, issued.token + "\n");
        writePrivate(PORT_FILE, String.valueOf(port(context)));
    }

    private static void writePrivate(File file, String content) throws IOException {
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        try {
            Os.chmod(file.getPath(), 0600);
        } catch (ErrnoException e) {
            throw new IOException(e);
        }
    }
}
