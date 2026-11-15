package com.termux.app.phonelinux;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;
import com.termux.view.HardwareKeyHook;
import com.termux.view.TerminalView;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Physical keyboard input independent of the system IME: JP/EN toggle, romaji to hiragana,
 * JIS symbol translation and PC-like shortcuts. Events from the soft keyboard are never touched.
 */
public final class HardwareKeyboard implements HardwareKeyHook {

    private static final int JP_CURSOR_COLOR = 0xFF00C853;

    private final TermuxActivity mActivity;
    private final ToggleKeyDebouncer mToggle = new ToggleKeyDebouncer();
    private final JapaneseComposition mComposition = new JapaneseComposition();
    /** Keys whose down event was consumed; their repeats are consumed too and the up must not leak. */
    private final Set<Integer> mConsumed = new HashSet<>();
    /** Keys passed to the terminal directly (bypassing the IME); their up goes the same way. */
    private final Set<Integer> mDirect = new HashSet<>();
    private KeysConfig mConfig = KeysConfig.defaults();
    private boolean mJapanese;

    public HardwareKeyboard(TermuxActivity activity) {
        mActivity = activity;
        reload();
        loadDictionary(activity.getApplicationContext());
    }

    /** Opens the dictionary off the UI thread; until it is ready Space/Enter commit plain hiragana. */
    private void loadDictionary(final android.content.Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    UserDictionary user = new UserDictionary(new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.phone-linux/user-dict.tsv"));
                    mComposition.setConverter(new KanaKanjiConverter(SkkDictionary.fromAsset(context), user));
                } catch (Throwable ignored) {
                    // no dictionary: conversion stays off
                }
            }
        }, "phone-linux-dict").start();
    }

    /** Re-reads keys.json (called when the activity resumes, e.g. after editing the settings). */
    public void reload() {
        mConfig = KeysStore.load();
        if (!mConfig.jpModeSwitch) mJapanese = false;
        mComposition.cancel();
    }

    @Override
    public String getPreedit() {
        return mComposition.preedit();
    }

    @Override
    public int[] getPreeditFocus() {
        return mComposition.focus();
    }

    @Override
    public List<String> getCandidates() {
        return mComposition.candidates();
    }

    @Override
    public int getCandidateIndex() {
        return mComposition.candidateIndex();
    }

    @Override
    public boolean isJapaneseMode() {
        return mJapanese;
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
        if (!down) {
            if (mDirect.remove(keyCode)) {
                view.onKeyUp(keyCode, e);
                return true;
            }
            return mConsumed.remove(keyCode);
        }

        // Keep a text selection alive while a modifier is pressed for a copy shortcut.
        if (KeyEvent.isModifierKey(keyCode)) return view.isSelectingText();

        if (handleDown(view, e, keyCode)) {
            mConsumed.add(keyCode);
            return true;
        }
        // Send the rest straight to the terminal so an IME (e.g. Simeji) cannot turn them into kana.
        if (e.isSystem() || !view.onKeyDown(keyCode, e)) return false;
        mDirect.add(keyCode);
        return true;
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
        if (mJapanese && !bypass && compose(view, keyCode, ch, shift)) return true;
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

    private boolean compose(TerminalView view, int keyCode, int ch, boolean shift) {
        return mComposition.onKey(keyCode, ch, shift, output(view));
    }

    private static JapaneseComposition.Output output(TerminalView view) {
        final TerminalSession session = view.getCurrentSession();
        return new JapaneseComposition.Output() {
            @Override
            public void write(String text) {
                if (session != null) session.write(text);
            }
        };
    }

    private void commit(TerminalView view) {
        mComposition.commit(output(view));
    }
}
