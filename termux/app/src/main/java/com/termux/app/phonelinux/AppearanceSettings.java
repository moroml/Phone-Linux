package com.termux.app.phonelinux;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Properties;

/** Phone-Linux appearance settings, stored in $HOME/.phone-linux/appearance.properties. */
public final class AppearanceSettings {

    public static final String MODE_NONE = "none";
    public static final String MODE_IMAGE = "image";
    public static final String MODE_COLOR = "color";
    public static final int DIM_MAX = 90;
    public static final int DIM_DEFAULT = 60;

    public static final String DIR_PATH = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.phone-linux";
    public static final File FILE = new File(DIR_PATH, "appearance.properties");

    public String bgMode = MODE_NONE;
    public int bgColor = 0xFF000000;
    /** How strongly the background image is covered by the terminal background colour, 0..{@link #DIM_MAX} percent. */
    public int dim = DIM_DEFAULT;
    public String bgExt = "jpg";
    public String preset = ColorPresets.DEFAULT;
    public boolean bundledFont = true;

    public static AppearanceSettings load() {
        return load(FILE);
    }

    public static AppearanceSettings load(File file) {
        Properties p = new Properties();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (IOException ignored) {
            }
        }
        return fromProperties(p);
    }

    public void save() throws IOException {
        save(FILE);
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        try (OutputStream out = new FileOutputStream(file)) {
            toProperties().store(out, "phone-linux appearance");
        }
    }

    public static AppearanceSettings fromProperties(Properties p) {
        AppearanceSettings s = new AppearanceSettings();
        String mode = p.getProperty("bg_mode", MODE_NONE);
        if (MODE_IMAGE.equals(mode) || MODE_COLOR.equals(mode)) s.bgMode = mode;
        Integer color = parseColor(p.getProperty("bg_color"));
        if (color != null) s.bgColor = color;
        try {
            s.dim = clampDim(Integer.parseInt(p.getProperty("bg_dim", String.valueOf(DIM_DEFAULT)).trim()));
        } catch (NumberFormatException ignored) {
        }
        String ext = p.getProperty("bg_ext", "jpg");
        if (ext.equals("jpg") || ext.equals("png") || ext.equals("webp")) s.bgExt = ext;
        String preset = p.getProperty("color_preset", ColorPresets.DEFAULT);
        for (String name : ColorPresets.NAMES) if (name.equals(preset)) s.preset = name;
        s.bundledFont = !"false".equals(p.getProperty("bundled_font", "true").trim());
        return s;
    }

    public Properties toProperties() {
        Properties p = new Properties();
        p.setProperty("bg_mode", bgMode);
        p.setProperty("bg_color", formatColor(bgColor));
        p.setProperty("bg_dim", String.valueOf(clampDim(dim)));
        p.setProperty("bg_ext", bgExt);
        p.setProperty("color_preset", preset);
        p.setProperty("bundled_font", String.valueOf(bundledFont));
        return p;
    }

    public File imageFile() {
        return new File(DIR_PATH, "background." + bgExt);
    }

    public static int clampDim(int v) {
        return Math.max(0, Math.min(DIM_MAX, v));
    }

    /** "#RRGGBB" (leading # optional) to opaque ARGB, or null if invalid. */
    public static Integer parseColor(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (!t.matches("[0-9a-fA-F]{6}")) return null;
        return 0xFF000000 | Integer.parseInt(t, 16);
    }

    public static String formatColor(int argb) {
        return String.format(Locale.ROOT, "#%06x", argb & 0xFFFFFF);
    }

    public static String extForMime(String mime) {
        if (mime == null) return "jpg";
        String m = mime.toLowerCase(Locale.ROOT);
        if (m.equals("image/png")) return "png";
        if (m.equals("image/webp")) return "webp";
        return "jpg";
    }

    /** Dim percent to the alpha (0..255) of the colour laid over the background image. */
    public static int overlayAlpha(int dimPercent) {
        return Math.round(clampDim(dimPercent) * 255f / 100f);
    }
}
