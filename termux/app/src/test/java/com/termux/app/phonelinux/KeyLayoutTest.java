package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

/**
 * Expected values are transcribed from the legends of a JIS 109 keyboard, row by row, and the
 * Linux jp106 scancode layout (the phone sees US keycodes for the same physical keys):
 *
 * number row : 1 2 3 4 5 6 7 8 9 0 - ^ yen    shifted: ! " # $ % & ' ( ) (none) = ~ |
 * Q row      : q w e r t y u i o p @ [        shifted: ... ` {
 * A row      : a s d f g h j k l ; : ]        shifted: ... + * }
 * Z row      : z x c v b n m , . / (RO)       shifted: ... &lt; &gt; ? _
 */
public class KeyLayoutTest {

    private static void check(int keyCode, char plain, char shifted) {
        assertEquals("plain " + keyCode, plain, KeyLayout.charFor(keyCode, false, true));
        assertEquals("shift " + keyCode, shifted, KeyLayout.charFor(keyCode, true, true));
    }

    @Test
    public void jisNumberRow() {
        String shifted = "!\"#$%&'()";
        for (int d = 1; d <= 9; d++) {
            check(KeyEvent.KEYCODE_0 + d, (char) ('0' + d), shifted.charAt(d - 1));
        }
        assertEquals('0', KeyLayout.charFor(KeyEvent.KEYCODE_0, false, true));
        assertEquals(KeyLayout.NOTHING, KeyLayout.charFor(KeyEvent.KEYCODE_0, true, true));
    }

    @Test
    public void jisSymbolKeys() {
        check(KeyEvent.KEYCODE_MINUS, '-', '=');
        check(KeyEvent.KEYCODE_EQUALS, '^', '~');
        check(KeyEvent.KEYCODE_YEN, '\\', '|');
        check(KeyEvent.KEYCODE_LEFT_BRACKET, '@', '`');
        check(KeyEvent.KEYCODE_RIGHT_BRACKET, '[', '{');
        check(KeyEvent.KEYCODE_SEMICOLON, ';', '+');
        check(KeyEvent.KEYCODE_APOSTROPHE, ':', '*');
        check(KeyEvent.KEYCODE_BACKSLASH, ']', '}');
        check(KeyEvent.KEYCODE_COMMA, ',', '<');
        check(KeyEvent.KEYCODE_PERIOD, '.', '>');
        check(KeyEvent.KEYCODE_SLASH, '/', '?');
        check(KeyEvent.KEYCODE_RO, '\\', '_');
    }

    @Test
    public void usLayoutKeepsUsLegends() {
        assertEquals('@', KeyLayout.charFor(KeyEvent.KEYCODE_2, true, false));
        assertEquals(')', KeyLayout.charFor(KeyEvent.KEYCODE_0, true, false));
        assertEquals('=', KeyLayout.charFor(KeyEvent.KEYCODE_EQUALS, false, false));
        assertEquals('[', KeyLayout.charFor(KeyEvent.KEYCODE_LEFT_BRACKET, false, false));
        assertEquals('"', KeyLayout.charFor(KeyEvent.KEYCODE_APOSTROPHE, true, false));
        assertEquals(KeyLayout.UNMAPPED, KeyLayout.charFor(KeyEvent.KEYCODE_YEN, false, false));
    }

    @Test
    public void lettersAndOthers() {
        assertEquals('a', KeyLayout.charFor(KeyEvent.KEYCODE_A, false, true));
        assertEquals('Z', KeyLayout.charFor(KeyEvent.KEYCODE_Z, true, true));
        assertEquals(' ', KeyLayout.charFor(KeyEvent.KEYCODE_SPACE, false, true));
        assertEquals(KeyLayout.UNMAPPED, KeyLayout.charFor(KeyEvent.KEYCODE_ENTER, false, true));
        assertEquals(KeyLayout.UNMAPPED, KeyLayout.charFor(KeyEvent.KEYCODE_F1, false, true));
    }
}
