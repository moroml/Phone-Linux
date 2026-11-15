package com.termux.app.phonelinux;

import androidx.annotation.NonNull;

/** Reads the outcome of the last phone-linux-update run from its log text. */
final class UpdateLog {

    private UpdateLog() {}

    /** Returns "yyyy-MM-dd HH:mm:ss ok|failed" for the newest RESULT line, or "" if there is none. */
    @NonNull
    static String lastResult(String log) {
        if (log == null) return "";
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            int at = line.indexOf("] RESULT ");
            if (line.startsWith("[") && at > 0)
                return line.substring(1, at) + " " + line.substring(at + "] RESULT ".length());
        }
        return "";
    }
}
