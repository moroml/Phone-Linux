package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

public class ToggleKeyDebouncerTest {

    private static final int G = KeyEvent.KEYCODE_GRAVE;

    /** getevent measurement: down, auto-repeat downs, then up immediately followed by the next down. */
    @Test
    public void measuredSequenceTogglesOncePerPhysicalPress() {
        ToggleKeyDebouncer d = new ToggleKeyDebouncer();
        int toggles = 0;
        long[][] events = {
            // {down?, repeatCount, time}
            {1, 0, 1000}, {1, 1, 1500}, {1, 2, 1550}, {1, 3, 1600}, {1, 4, 1650},
            {0, 0, 4000}, {1, 0, 4001}, // second press: up then down
            {1, 1, 4500}, {1, 2, 4550},
            {0, 0, 9000}, {1, 0, 9001}, // third press
        };
        for (long[] e : events) {
            if (d.onKey(G, e[0] == 1, (int) e[1], e[2]) == ToggleKeyDebouncer.Result.TOGGLE) toggles++;
        }
        assertEquals(3, toggles);
    }

    @Test
    public void repeatsAndUpsAreSwallowed() {
        ToggleKeyDebouncer d = new ToggleKeyDebouncer();
        assertEquals(ToggleKeyDebouncer.Result.TOGGLE, d.onKey(G, true, 0, 100));
        assertEquals(ToggleKeyDebouncer.Result.SWALLOW, d.onKey(G, true, 3, 700));
        assertEquals(ToggleKeyDebouncer.Result.SWALLOW, d.onKey(G, false, 0, 800));
    }

    @Test
    public void duplicateDownWithinIntervalIsSwallowed() {
        ToggleKeyDebouncer d = new ToggleKeyDebouncer();
        assertEquals(ToggleKeyDebouncer.Result.TOGGLE, d.onKey(G, true, 0, 100));
        assertEquals(ToggleKeyDebouncer.Result.SWALLOW, d.onKey(G, true, 0, 110));
        assertEquals(ToggleKeyDebouncer.Result.TOGGLE, d.onKey(G, true, 0, 100 + ToggleKeyDebouncer.MIN_INTERVAL_MS));
    }

    @Test
    public void zenkakuHankakuKeyAlsoToggles() {
        ToggleKeyDebouncer d = new ToggleKeyDebouncer();
        assertEquals(ToggleKeyDebouncer.Result.TOGGLE, d.onKey(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, true, 0, 5));
    }

    @Test
    public void otherKeysAreIgnored() {
        assertEquals(ToggleKeyDebouncer.Result.IGNORE, new ToggleKeyDebouncer().onKey(KeyEvent.KEYCODE_A, true, 0, 5));
    }
}
