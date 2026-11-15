package com.termux.app.phonelinux;

import android.view.KeyEvent;

/**
 * Turns the raw events of the JP/EN toggle key into exactly one toggle per physical press.
 *
 * Measured with Samsung "Multi Control": the PC sends only a key-down for the 半角/全角 key
 * (arrives as KEYCODE_GRAVE). Android then auto-repeats it (repeatCount &gt; 0) and the matching
 * key-up shows up only on the next press, right before the new down.
 */
public final class ToggleKeyDebouncer {

    public enum Result { IGNORE, SWALLOW, TOGGLE }

    /** Two real presses cannot be closer than this; guards against a repeat reported with repeatCount 0. */
    static final long MIN_INTERVAL_MS = 80;

    private long mLastToggleTime = Long.MIN_VALUE;

    public static boolean isToggleKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_GRAVE || keyCode == KeyEvent.KEYCODE_ZENKAKU_HANKAKU;
    }

    public Result onKey(int keyCode, boolean down, int repeatCount, long eventTime) {
        if (!isToggleKey(keyCode)) return Result.IGNORE;
        if (!down || repeatCount != 0) return Result.SWALLOW;
        if (mLastToggleTime != Long.MIN_VALUE && eventTime - mLastToggleTime < MIN_INTERVAL_MS) return Result.SWALLOW;
        mLastToggleTime = eventTime;
        return Result.TOGGLE;
    }
}
