package com.termux.app.phonelinux;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalSession;
import com.termux.view.HardwareKeyHook;
import com.termux.view.TerminalView;

import java.util.HashSet;
import java.util.Set;

/**
 * Physical keyboard input independent of the system IME: JP/EN toggle, romaji to hiragana,
 * JIS symbol translation and PC-like shortcuts. Events from the soft keyboard are never touched.
 */
public final class HardwareKeyboard implements HardwareKeyHook {

    private static final int JP_CURSOR_COLOR = 0xFF00C853;

    private final TermuxActivity mActivity;
    private final ToggleKeyDebouncer mToggle = new ToggleKeyDebouncer();
    private final RomajiComposer mComposer = new RomajiComposer();
    /** Keys whose down event was consumed; their repeats are consumed too and the up must not leak. */
    private final Set<Integer> mConsumed = new HashSet<>();
    private KeysConfig mConfig = KeysConfig.defaults();
    private boolean mJapanese;

    public HardwareKeyboard(TermuxActivity activity) {
        mActivity = activity;
        reload();
    }

    /** Re-reads keys.json (called when the activity resumes, e.g. after editing the settings). */
    public void reload() {
        mConfig = KeysStore.load();
        if (!mConfig.jpModeSwitch) mJapanese = false;
        mComposer.cancel();
    }

    @Override
    public String getPreedit() {
        return mComposer.isEmpty() ? null : mComposer.preedit();
    }

    @Override
    public int getCursorColor() {
        return mJapanese ? JP_CURSOR_COLOR : 0;
    }

    @Override
    public boolean onKeyPreIme(TerminalView view, KeyEvent e) {
        if (e.getDeviceId() <= KeyCharacterMap.VIRTUAL_KEYBOARD) return false;
        int action = e.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false;
        boolean down = action == KeyEvent.ACTION_DOWN;
        int keyCode = e.getKeyCode();

        if (mConfig.jpModeSwitch && ToggleKeyDebouncer.isToggleKey(keyCode)) {
            if (mToggle.onKey(keyCode, down, e.getRepeatCount(), e.getEventTime()) == ToggleKeyDebouncer.Result.TOGGLE) {
                commit(view);
                mJapanese = !mJapanese;
            }
            return true;
        }
        if (!down) return mConsumed.remove(keyCode);

        // Keep a text selection alive while a modifier is pressed for a copy shortcut.
        if (KeyEvent.isModifierKey(keyCode)) return view.isSelectingText();

        boolean consumed = handleDown(view, e, keyCode);
        if (consumed) mConsumed.add(keyCode);
        return consumed;
    }

    private boolean handleDown(TerminalView view, KeyEvent e, int keyCode) {
        boolean ctrl = e.isCtrlPressed(), alt = e.isAltPressed(), shift = e.isShiftPressed();
        int repeat = e.getRepeatCount();
        int ch = KeyLayout.charFor(keyCode, shift, mConfig.jis);

        String action = mConfig.actionFor(keyCode, ch, ctrl, shift, alt);
        if (action != null) {
            if (repeat == 0 || ShortcutActions.isRepeatable(action)) {
                commit(view);
                ShortcutActions.run(action, mActivity);
            }
            return true;
        }

        boolean bypass = ctrl || alt || e.isMetaPressed();
        if (mJapanese && !bypass && compose(view, keyCode, ch)) return true;
        // Any other key ends a composition in progress: commit it, then let the key through.
        commit(view);

        if (mConfig.jis && isTranslatedKey(keyCode, ch, shift)) {
            if (ch != KeyLayout.NOTHING) {
                boolean leftAlt = (e.getMetaState() & KeyEvent.META_ALT_LEFT_ON) != 0;
                view.inputCodePoint(e.getDeviceId(), ch, ctrl, leftAlt);
            }
            return true;
        }
        return false;
    }

    /** Keys whose character differs from what the US keymap would produce. */
    private static boolean isTranslatedKey(int keyCode, int ch, boolean shift) {
        if (ch == KeyLayout.UNMAPPED) return false;
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) return false;
        if (keyCode == KeyEvent.KEYCODE_SPACE) return false;
        return !(keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9 && !shift);
    }

    private boolean compose(TerminalView view, int keyCode, int ch) {
        boolean empty = mComposer.isEmpty();
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (empty) return false;
                commit(view);
                return true;
            case KeyEvent.KEYCODE_DEL:
                return !empty && mComposer.backspace();
            case KeyEvent.KEYCODE_ESCAPE:
                if (empty) return false;
                mComposer.cancel();
                return true;
            default:
                return ch > 0 && mComposer.feed((char) ch);
        }
    }

    private void commit(TerminalView view) {
        if (mComposer.isEmpty()) return;
        String text = mComposer.commit();
        TerminalSession session = view.getCurrentSession();
        if (session != null && !text.isEmpty()) session.write(text);
    }
}
