package com.termux.app.phonelinux;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reads and writes $HOME/.phone-linux/keys.json. */
public final class KeysStore {

    private static final String LOG_TAG = "PhoneLinuxKeys";

    private KeysStore() {}

    private static File file() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.phone-linux", "keys.json");
    }

    /** Loads the config, writing the defaults first when the file does not exist. */
    public static KeysConfig load() {
        File f = file();
        KeysConfig config;
        if (!f.isFile()) {
            config = KeysConfig.defaults();
            save(config);
            return config;
        }
        try {
            try (InputStream in = new FileInputStream(f)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                config = KeysConfig.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read " + f, e);
            config = KeysConfig.defaults();
        }
        for (String w : config.warnings) Logger.logWarn(LOG_TAG, w);
        return config;
    }

    public static boolean save(KeysConfig config) {
        File f = file();
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            try (OutputStream out = new FileOutputStream(f)) {
                out.write(config.toJson().getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to write " + f, e);
            return false;
        }
    }
}
