package com.termux.app.phonelinux;

import android.view.KeyEvent;

/**
 * Key to character tables for hardware keyboards.
 *
 * The phone's keyboard layout is US, so every key arrives with its US keycode. In JIS mode the
 * characters printed on a JIS 109 keyboard are produced instead. Source of truth: the JIS X 6004
 * (109 key) legend, with scancodes mapped to Android keycodes the way the Linux "jp106" keyboard
 * does (e.g. scancode 27 "]" is the key that prints "[" on JIS).
 */
public final class KeyLayout {

    /** The key has no character in this layout; let the default handling run. */
    public static final int UNMAPPED = -1;
    /** The key produces nothing (Shift+0 on JIS). */
    public static final int NOTHING = -2;

    private KeyLayout() {}

    // index: digit 0..9
    private static final String US_DIGIT_SHIFT = ")!@#$%^&*(";
    private static final String JIS_DIGIT_SHIFT = " !\"#$%&'()"; // index 0 (Shift+0) is NOTHING

    /** US legend of the symbol keys: {keycode, plain, shifted}. */
    private static final int[][] US = {
        {KeyEvent.KEYCODE_GRAVE, '`', '~'},
        {KeyEvent.KEYCODE_MINUS, '-', '_'},
        {KeyEvent.KEYCODE_EQUALS, '=', '+'},
        {KeyEvent.KEYCODE_LEFT_BRACKET, '[', '{'},
        {KeyEvent.KEYCODE_RIGHT_BRACKET, ']', '}'},
        {KeyEvent.KEYCODE_BACKSLASH, '\\', '|'},
        {KeyEvent.KEYCODE_SEMICOLON, ';', ':'},
        {KeyEvent.KEYCODE_APOSTROPHE, '\'', '"'},
        {KeyEvent.KEYCODE_COMMA, ',', '<'},
        {KeyEvent.KEYCODE_PERIOD, '.', '>'},
        {KeyEvent.KEYCODE_SLASH, '/', '?'},
    };

    /** JIS legend of the same physical keys (those that differ), plus the JIS-only keys. */
    private static final int[][] JIS = {
        {KeyEvent.KEYCODE_MINUS, '-', '='},
        {KeyEvent.KEYCODE_EQUALS, '^', '~'},
        {KeyEvent.KEYCODE_LEFT_BRACKET, '@', '`'},
        {KeyEvent.KEYCODE_RIGHT_BRACKET, '[', '{'},
        {KeyEvent.KEYCODE_BACKSLASH, ']', '}'},
        {KeyEvent.KEYCODE_SEMICOLON, ';', '+'},
        {KeyEvent.KEYCODE_APOSTROPHE, ':', '*'},
        {KeyEvent.KEYCODE_YEN, '\\', '|'},
        {KeyEvent.KEYCODE_RO, '\\', '_'},
    };

    /** Character for the key, or {@link #UNMAPPED} / {@link #NOTHING}. Letters honour only Shift. */
    public static int charFor(int keyCode, boolean shift, boolean jis) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            char c = (char) ('a' + keyCode - KeyEvent.KEYCODE_A);
            return shift ? Character.toUpperCase(c) : c;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            int d = keyCode - KeyEvent.KEYCODE_0;
            if (!shift) return '0' + d;
            if (!jis) return US_DIGIT_SHIFT.charAt(d);
            return d == 0 ? NOTHING : JIS_DIGIT_SHIFT.charAt(d);
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE) return ' ';
        if (jis) {
            for (int[] k : JIS) if (k[0] == keyCode) return shift ? k[2] : k[1];
        }
        for (int[] k : US) if (k[0] == keyCode) return shift ? k[2] : k[1];
        return UNMAPPED;
    }
}
