package com.termux.app.phonelinux;

import android.content.Context;
import android.view.View;

import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Entry points called from the Termux classes, plus the colour preset / font file handling. */
public final class PhoneLinuxAppearance {

    private static final String LOG_TAG = "PhoneLinuxAppearance";
    private static final String FONT_ASSET = "phone-linux-fonts/UDEVGothic-Regular.ttf";
    private static final File FONT_RECORD = new File(AppearanceSettings.DIR_PATH, "font.sha256");
    private static final File COLORS_BACKUP = new File(AppearanceSettings.DIR_PATH, "colors.properties.user");

    private static volatile int sRevision = 1;
    private static int sAppliedRevision;

    private PhoneLinuxAppearance() {}

    /** Called by the settings screen after any change; the terminal picks it up on the next resume. */
    public static void markChanged() {
        sRevision++;
    }

    /** Called from PhoneLinuxSetup.install(): puts the bundled font in place and reloads if it changed. */
    public static void onSetupInstalled(Context context) {
        if (!syncFont(context) || !(context instanceof TermuxActivity)) return;
        TermuxTerminalSessionActivityClient client = ((TermuxActivity) context).getTermuxTerminalSessionClient();
        if (client != null) client.checkForFontAndColors();
    }

    /** Called from TermuxActivity.onResume(). */
    public static void onResume(TermuxActivity activity) {
        activity.getTerminalToolbarViewPager().setVisibility(
            activity.getPreferences().shouldShowTerminalToolbar() ? View.VISIBLE : View.GONE);

        TermuxTerminalSessionActivityClient client = activity.getTermuxTerminalSessionClient();
        if (client == null) return;
        if (sAppliedRevision != sRevision) {
            sAppliedRevision = sRevision;
            syncFont(activity);
            activity.getTerminalView().setTextSize(activity.getPreferences().getFontSize());
            client.checkForFontAndColors();
        } else {
            client.updateBackgroundColor();
        }
    }

    /** Called from TermuxTerminalSessionActivityClient.updateBackgroundColor() in place of setting a plain colour. */
    public static void applyBackground(TermuxActivity activity, int terminalBg) {
        PhoneLinuxBackground.apply(activity, terminalBg);
    }

    /** Installs or removes ~/.termux/font.ttf according to {@link FontPolicy}. Returns true if the file changed. */
    public static boolean syncFont(Context context) {
        try {
            File font = TermuxConstants.TERMUX_FONT_FILE;
            String current = font.isFile() ? sha256(font) : null;
            String recorded = FONT_RECORD.isFile()
                ? readText(FONT_RECORD).trim() : null;
            switch (FontPolicy.decide(AppearanceSettings.load().bundledFont, current, recorded)) {
                case INSTALL:
                    installFont(context, font);
                    return true;
                case REMOVE:
                    FONT_RECORD.delete();
                    return font.delete();
                default:
                    return false;
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to sync font", e);
            return false;
        }
    }

    private static void installFont(Context context, File font) throws Exception {
        File dir = font.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File tmp = new File(dir, "font.ttf.tmp");
        try (InputStream in = context.getAssets().open(FONT_ASSET); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        }
        if (!FontPolicy.BUNDLED_SHA256.equals(sha256(tmp))) {
            tmp.delete();
            throw new IOException("Bundled font is corrupt");
        }
        font.delete();
        if (!tmp.renameTo(font)) throw new IOException("Cannot move font into place");
        writeText(FONT_RECORD, FontPolicy.BUNDLED_SHA256);
    }

    /** Writes (or removes) ~/.termux/colors.properties for a preset, keeping a one-time backup of a user's own file. */
    public static void applyColorPreset(String name) throws IOException {
        File colors = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        String existing = colors.isFile() ? readText(colors) : null;
        boolean userFile = existing != null && !ColorPresets.isOurs(existing);
        String generated = ColorPresets.generate(name);
        if (generated != null) {
            if (userFile && !COLORS_BACKUP.exists()) {
                File parent = COLORS_BACKUP.getParentFile();
                if (parent != null) parent.mkdirs();
                writeText(COLORS_BACKUP, existing);
            }
            File dir = colors.getParentFile();
            if (dir != null) dir.mkdirs();
            writeText(colors, generated);
        } else if (!userFile) {
            colors.delete();
            if (COLORS_BACKUP.isFile() && COLORS_BACKUP.renameTo(colors)) markChanged();
        }
        markChanged();
    }

    private static void writeText(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readText(File file) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
