package com.termux.app.phonelinux;

import android.view.KeyEvent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hardware keyboard settings stored in $HOME/.phone-linux/keys.json:
 * {"layout":"jis","jpModeSwitch":true,"shortcuts":{"copy":"ctrl+shift+c", ...}}
 * Invalid entries are ignored (the default stays) and reported in {@link #warnings}.
 */
public final class KeysConfig {

    public static final String COPY = "copy";
    public static final String PASTE = "paste";
    public static final String NEW_SESSION = "new_session";
    public static final String NEXT_SESSION = "next_session";
    public static final String PREV_SESSION = "prev_session";
    public static final String FONT_UP = "font_up";
    public static final String FONT_DOWN = "font_down";
    public static final String INSERT_IMAGE = "insert_image";
    public static final String NEWLINE = "newline";

    public static final String[] ACTIONS = {COPY, PASTE, NEW_SESSION, NEXT_SESSION, PREV_SESSION, FONT_UP, FONT_DOWN, INSERT_IMAGE, NEWLINE};

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(COPY, "ctrl+shift+c");
        DEFAULTS.put(PASTE, "ctrl+shift+v");
        DEFAULTS.put(NEW_SESSION, "ctrl+shift+t");
        DEFAULTS.put(NEXT_SESSION, "ctrl+tab");
        DEFAULTS.put(PREV_SESSION, "ctrl+shift+tab");
        DEFAULTS.put(FONT_UP, "ctrl+=");
        DEFAULTS.put(FONT_DOWN, "ctrl+-");
        DEFAULTS.put(INSERT_IMAGE, "ctrl+shift+i");
        DEFAULTS.put(NEWLINE, "shift+enter");
    }

    public boolean jis = true;
    public boolean jpModeSwitch = true;
    /** action to binding text; empty text disables the action. */
    public final Map<String, String> shortcuts = new LinkedHashMap<>(DEFAULTS);
    public final List<String> warnings = new ArrayList<>();

    /** A parsed shortcut such as "ctrl+shift+c". */
    public static final class Binding {
        final boolean ctrl, shift, alt;
        /** Android keycode, or 0 when the token is a symbol matched by the produced character. */
        final int keyCode;
        final char symbol;

        private Binding(boolean ctrl, boolean shift, boolean alt, int keyCode, char symbol) {
            this.ctrl = ctrl;
            this.shift = shift;
            this.alt = alt;
            this.keyCode = keyCode;
            this.symbol = symbol;
        }

        /** Returns null when the text is not a valid binding. */
        public static Binding parse(String text) {
            if (text == null) return null;
            String t = text.trim().toLowerCase(Locale.ROOT);
            String token, mods;
            if (t.equals("+")) {
                token = "+";
                mods = "";
            } else if (t.endsWith("++")) {
                token = "+";
                mods = t.substring(0, t.length() - 2);
            } else {
                int i = t.lastIndexOf('+');
                token = i < 0 ? t : t.substring(i + 1);
                mods = i < 0 ? "" : t.substring(0, i);
            }
            if (token.isEmpty()) return null;
            boolean ctrl = false, shift = false, alt = false;
            if (!mods.isEmpty()) {
                for (String p : mods.split("\\+", -1)) {
                    p = p.trim();
                    if (p.equals("ctrl") || p.equals("control")) ctrl = true;
                    else if (p.equals("shift")) shift = true;
                    else if (p.equals("alt")) alt = true;
                    else return null;
                }
            }
            if (token.length() == 1) {
                char c = token.charAt(0);
                if (c >= 'a' && c <= 'z') return new Binding(ctrl, shift, alt, KeyEvent.KEYCODE_A + c - 'a', (char) 0);
                if (c >= '0' && c <= '9') return new Binding(ctrl, shift, alt, KeyEvent.KEYCODE_0 + c - '0', (char) 0);
                if (c > ' ' && c < 127) return new Binding(ctrl, shift, alt, 0, c);
                return null;
            }
            int code = namedKey(token);
            return code == 0 ? null : new Binding(ctrl, shift, alt, code, (char) 0);
        }

        private static int namedKey(String name) {
            switch (name) {
                case "tab": return KeyEvent.KEYCODE_TAB;
                case "enter": return KeyEvent.KEYCODE_ENTER;
                case "esc": case "escape": return KeyEvent.KEYCODE_ESCAPE;
                case "space": return KeyEvent.KEYCODE_SPACE;
                case "backspace": return KeyEvent.KEYCODE_DEL;
                case "up": return KeyEvent.KEYCODE_DPAD_UP;
                case "down": return KeyEvent.KEYCODE_DPAD_DOWN;
                case "left": return KeyEvent.KEYCODE_DPAD_LEFT;
                case "right": return KeyEvent.KEYCODE_DPAD_RIGHT;
                case "pageup": return KeyEvent.KEYCODE_PAGE_UP;
                case "pagedown": return KeyEvent.KEYCODE_PAGE_DOWN;
                default:
                    if (name.length() >= 2 && name.length() <= 3 && name.charAt(0) == 'f') {
                        try {
                            int n = Integer.parseInt(name.substring(1));
                            if (n >= 1 && n <= 12) return KeyEvent.KEYCODE_F1 + n - 1;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    return 0;
            }
        }

        /**
         * @param effectiveChar the character the key produces in the active layout, or
         *                      {@link KeyLayout#UNMAPPED}. Symbol tokens match it and ignore Shift
         *                      (so Ctrl+= also fires for Ctrl+Shift+- on a JIS keyboard).
         */
        public boolean matches(int keyCode, int effectiveChar, boolean ctrl, boolean shift, boolean alt) {
            if (ctrl != this.ctrl || alt != this.alt) return false;
            if (this.keyCode == 0) return effectiveChar == symbol;
            return keyCode == this.keyCode && shift == this.shift;
        }
    }

    public static KeysConfig defaults() {
        return new KeysConfig();
    }

    public static KeysConfig parse(String json) {
        KeysConfig c = new KeysConfig();
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException | NullPointerException e) {
            c.warnings.add("keys.json is not valid JSON, using defaults");
            return c;
        }
        String layout = root.optString("layout", "jis");
        if (layout.equalsIgnoreCase("jis")) c.jis = true;
        else if (layout.equalsIgnoreCase("us")) c.jis = false;
        else c.warnings.add("unknown layout '" + layout + "', using jis");
        if (root.has("jpModeSwitch")) {
            Object v = root.opt("jpModeSwitch");
            if (v instanceof Boolean) c.jpModeSwitch = (Boolean) v;
            else c.warnings.add("jpModeSwitch must be true or false");
        }
        JSONObject sc = root.optJSONObject("shortcuts");
        if (sc != null) {
            Iterator<String> it = sc.keys();
            while (it.hasNext()) {
                String action = it.next();
                if (!DEFAULTS.containsKey(action)) {
                    c.warnings.add("unknown action '" + action + "'");
                    continue;
                }
                Object v = sc.opt(action);
                String text = v instanceof String ? (String) v : null;
                if (text != null && (text.trim().isEmpty() || Binding.parse(text) != null)) {
                    c.shortcuts.put(action, text.trim());
                } else {
                    c.warnings.add("invalid shortcut for '" + action + "': " + v);
                }
            }
        }
        return c;
    }

    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("layout", jis ? "jis" : "us");
            root.put("jpModeSwitch", jpModeSwitch);
            JSONObject sc = new JSONObject();
            for (Map.Entry<String, String> e : shortcuts.entrySet()) sc.put(e.getKey(), e.getValue());
            root.put("shortcuts", sc);
            return root.toString(2) + "\n";
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Action bound to the key combination, or null. */
    public String actionFor(int keyCode, int effectiveChar, boolean ctrl, boolean shift, boolean alt) {
        for (Map.Entry<String, String> e : shortcuts.entrySet()) {
            Binding b = Binding.parse(e.getValue());
            if (b != null && b.matches(keyCode, effectiveChar, ctrl, shift, alt)) return e.getKey();
        }
        return null;
    }
}
