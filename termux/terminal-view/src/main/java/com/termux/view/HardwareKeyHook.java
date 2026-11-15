package com.termux.view;

import android.view.KeyEvent;

/**
 * Handles physical keyboard events before the input method sees them, so input (Japanese
 * composition, shortcuts, layout translation) does not depend on the system IME.
 */
public interface HardwareKeyHook {

    /** Called from {@link TerminalView#onKeyPreIme}. Return true if the event was consumed. */
    boolean onKeyPreIme(TerminalView view, KeyEvent event);

    /** Uncommitted text to draw at the cursor, or null/empty. */
    String getPreedit();

    /** ARGB cursor colour overriding the terminal's own, or 0 for none. */
    int getCursorColor();
}
