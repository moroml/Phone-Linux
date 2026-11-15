package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public class KeysConfigTest {

    private static final int NONE = KeyLayout.UNMAPPED;

    @Test
    public void defaultsAreAllValid() {
        KeysConfig c = KeysConfig.defaults();
        assertTrue(c.jis);
        assertTrue(c.jpModeSwitch);
        for (String a : KeysConfig.ACTIONS) assertNotNull(a, KeysConfig.Binding.parse(c.shortcuts.get(a)));
    }

    @Test
    public void defaultShortcutsMatch() {
        KeysConfig c = KeysConfig.defaults();
        assertEquals(KeysConfig.COPY, c.actionFor(KeyEvent.KEYCODE_C, 'C', true, true, false));
        assertEquals(KeysConfig.PASTE, c.actionFor(KeyEvent.KEYCODE_V, 'V', true, true, false));
        assertEquals(KeysConfig.NEW_SESSION, c.actionFor(KeyEvent.KEYCODE_T, 'T', true, true, false));
        assertEquals(KeysConfig.NEXT_SESSION, c.actionFor(KeyEvent.KEYCODE_TAB, NONE, true, false, false));
        assertEquals(KeysConfig.PREV_SESSION, c.actionFor(KeyEvent.KEYCODE_TAB, NONE, true, true, false));
        assertEquals(KeysConfig.INSERT_IMAGE, c.actionFor(KeyEvent.KEYCODE_I, 'I', true, true, false));
        assertEquals(KeysConfig.FONT_DOWN, c.actionFor(KeyEvent.KEYCODE_MINUS, '-', true, false, false));
        assertEquals(KeysConfig.NEWLINE, c.actionFor(KeyEvent.KEYCODE_ENTER, NONE, false, true, false));
        assertNull(c.actionFor(KeyEvent.KEYCODE_ENTER, NONE, false, false, false));
    }

    @Test
    public void plainCtrlCAndTerminalKeysAreNotShortcuts() {
        KeysConfig c = KeysConfig.defaults();
        assertNull(c.actionFor(KeyEvent.KEYCODE_C, 'c', true, false, false));
        assertNull(c.actionFor(KeyEvent.KEYCODE_V, 'v', true, false, false));
        assertNull(c.actionFor(KeyEvent.KEYCODE_C, 'c', false, true, false));
        assertNull(c.actionFor(KeyEvent.KEYCODE_C, 'C', true, true, true));
    }

    @Test
    public void ctrlEqualsWorksOnBothLayouts() {
        KeysConfig c = KeysConfig.defaults();
        // US: the "=" key
        assertEquals(KeysConfig.FONT_UP, c.actionFor(KeyEvent.KEYCODE_EQUALS, '=', true, false, false));
        // JIS: Shift+- produces "="
        assertEquals(KeysConfig.FONT_UP, c.actionFor(KeyEvent.KEYCODE_MINUS, '=', true, true, false));
        // JIS: the "-" key itself is "-", not "="
        assertEquals(KeysConfig.FONT_DOWN, c.actionFor(KeyEvent.KEYCODE_MINUS, '-', true, false, false));
    }

    @Test
    public void parseOverridesAndIgnoresInvalidEntries() {
        KeysConfig c = KeysConfig.parse("{\"layout\":\"us\",\"jpModeSwitch\":false,\"shortcuts\":{"
            + "\"copy\":\"alt+y\",\"paste\":\"bogus+key+\",\"nope\":\"ctrl+x\",\"new_session\":\"\",\"font_up\":5}}");
        assertFalse(c.jis);
        assertFalse(c.jpModeSwitch);
        assertEquals("alt+y", c.shortcuts.get(KeysConfig.COPY));
        assertEquals("ctrl+shift+v", c.shortcuts.get(KeysConfig.PASTE));
        assertEquals("ctrl+=", c.shortcuts.get(KeysConfig.FONT_UP));
        assertEquals("", c.shortcuts.get(KeysConfig.NEW_SESSION));
        assertEquals(3, c.warnings.size());
        assertNull(c.actionFor(KeyEvent.KEYCODE_T, 'T', true, true, false));
    }

    @Test
    public void brokenJsonFallsBackToDefaults() {
        KeysConfig c = KeysConfig.parse("{not json");
        assertEquals(1, c.warnings.size());
        assertTrue(c.jis);
        assertEquals("ctrl+shift+c", c.shortcuts.get(KeysConfig.COPY));
    }

    @Test
    public void bindingParsing() {
        assertNotNull(KeysConfig.Binding.parse("Ctrl+Shift+F5"));
        assertNotNull(KeysConfig.Binding.parse("ctrl++"));
        assertNotNull(KeysConfig.Binding.parse("ctrl+-"));
        assertNull(KeysConfig.Binding.parse("ctrl+"));
        assertNull(KeysConfig.Binding.parse("ctrl+a+b"));
        assertNull(KeysConfig.Binding.parse("hyper+a"));
        assertNull(KeysConfig.Binding.parse("f13"));
        assertNull(KeysConfig.Binding.parse(null));
    }

    @Test
    public void jsonRoundTrip() {
        KeysConfig c = KeysConfig.defaults();
        c.jis = false;
        c.shortcuts.put(KeysConfig.COPY, "alt+y");
        KeysConfig d = KeysConfig.parse(c.toJson());
        assertFalse(d.jis);
        assertEquals("alt+y", d.shortcuts.get(KeysConfig.COPY));
        assertTrue(d.warnings.isEmpty());
    }
}
