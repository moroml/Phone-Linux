package com.termux.app;

import android.content.Context;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Copies the Phone-Linux scripts bundled in assets/phone-linux into $PREFIX on every start. */
final class PhoneLinuxSetup {

    private static final String LOG_TAG = "PhoneLinuxSetup";
    private static final String ASSET_ROOT = "phone-linux";
    /** Every bundled file starts a comment line with this, so a same-named file the user owns is never overwritten. */
    private static final String MARKER = "# phone-linux:";

    private PhoneLinuxSetup() {}

    static void install(Context context) {
        try {
            copyTree(context, ASSET_ROOT, new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH));
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to install Phone-Linux scripts", e);
        }
    }

    private static void copyTree(Context context, String assetPath, File dest) throws IOException {
        String[] children = context.getAssets().list(assetPath);
        if (children != null && children.length > 0) {
            for (String child : children) copyTree(context, assetPath + "/" + child, new File(dest, child));
            return;
        }

        byte[] data;
        try (InputStream in = context.getAssets().open(assetPath)) {
            data = readAll(in);
        }
        if (dest.isFile()) {
            byte[] current;
            try (InputStream in = new FileInputStream(dest)) {
                current = readAll(in);
            }
            if (Arrays.equals(current, data)) return;
            String text = new String(current, StandardCharsets.UTF_8);
            if (!text.startsWith(MARKER) && !text.contains("\n" + MARKER)) {
                Logger.logInfo(LOG_TAG, "Keeping user file " + dest);
                return;
            }
        }

        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        File tmp = new File(dest.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.setExecutable(true, false) || !tmp.renameTo(dest)) throw new IOException("Cannot install " + dest);
        Logger.logInfo(LOG_TAG, "Installed " + dest);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
