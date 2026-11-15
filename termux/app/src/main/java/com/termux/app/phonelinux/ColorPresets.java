package com.termux.app.phonelinux;

import java.util.Locale;

/** Terminal colour presets written to ~/.termux/colors.properties. */
public final class ColorPresets {

    public static final String DEFAULT = "default";
    public static final String TEAL = "teal";
    public static final String LIGHT = "light";
    public static final String[] NAMES = {DEFAULT, TEAL, LIGHT};

    /** First line of every file we write, so we never delete or overwrite one the user wrote. */
    public static final String MARKER = "# phone-linux: colour preset ";

    private ColorPresets() {}

    private static final String[] TEAL_PALETTE = {
        "0b1418", "e06c75", "7fd962", "e5c07b", "61afef", "c678dd", "33d6d6", "c9d6d6",
        "3b4f55", "ff7b86", "98e57e", "f2d38f", "7cc4ff", "d68cf0", "66ecec", "f2fbfb"};
    private static final String[] LIGHT_PALETTE = {
        "24292f", "cf222e", "1a7f37", "9a6700", "0969da", "8250df", "1b7c83", "6e7781",
        "57606a", "a40e26", "116329", "4d2d00", "218bff", "a475f9", "3192aa", "32383f"};

    /** Returns the colors.properties text for a preset, or null for {@link #DEFAULT} (the built-in colours). */
    public static String generate(String name) {
        if (TEAL.equals(name)) return build(name, "33d6d6", "0b1418", "33d6d6", TEAL_PALETTE);
        if (LIGHT.equals(name)) return build(name, "1f2328", "fafafa", "1f2328", LIGHT_PALETTE);
        return null;
    }

    public static boolean isOurs(String fileText) {
        return fileText != null && fileText.startsWith(MARKER);
    }

    private static String build(String name, String fg, String bg, String cursor, String[] palette) {
        StringBuilder sb = new StringBuilder(MARKER).append(name.toLowerCase(Locale.ROOT)).append('\n');
        sb.append("foreground=#").append(fg).append('\n');
        sb.append("background=#").append(bg).append('\n');
        sb.append("cursor=#").append(cursor).append('\n');
        for (int i = 0; i < palette.length; i++) sb.append("color").append(i).append("=#").append(palette[i]).append('\n');
        return sb.toString();
    }
}
