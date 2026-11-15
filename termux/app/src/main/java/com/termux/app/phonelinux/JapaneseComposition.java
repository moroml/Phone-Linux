package com.termux.app.phonelinux;

import android.view.KeyEvent;

import java.util.List;

/** Hardware-key composition: romaji to hiragana, then kana-kanji conversion while Space/Enter/arrows drive it. */
public final class JapaneseComposition {

    /** Receives text to be written to the terminal. */
    public interface Output {
        void write(String text);
    }

    private final RomajiComposer mComposer = new RomajiComposer();
    private volatile KanaKanjiConverter mConverter;
    private Conversion mConversion;

    /** Conversion needs the dictionary; until it is set, Space/Enter just commit the hiragana. */
    public void setConverter(KanaKanjiConverter converter) {
        mConverter = converter;
    }

    public boolean isEmpty() {
        return mConversion == null && mComposer.isEmpty();
    }

    public boolean isConverting() {
        return mConversion != null;
    }

    /** Text to show at the cursor, or null. */
    public String preedit() {
        if (mConversion != null) return mConversion.text();
        return mComposer.isEmpty() ? null : mComposer.preedit();
    }

    /** Start/end offsets of the focused segment in {@link #preedit()}, or null when not converting. */
    public int[] focus() {
        return mConversion == null ? null : mConversion.focusRange();
    }

    public List<String> candidates() {
        return mConversion == null ? null : mConversion.pageCandidates();
    }

    public int candidateIndex() {
        return mConversion == null ? -1 : mConversion.pageIndex();
    }

    /**
     * Handles a key while the JP mode is on.
     *
     * @param ch the character the key produces (0 if none)
     * @return true if the key was consumed; false means the caller commits and lets the key through
     */
    public boolean onKey(int keyCode, int ch, boolean shift, Output out) {
        return mConversion != null ? convertingKey(keyCode, ch, shift, out) : composingKey(keyCode, ch, out);
    }

    private boolean composingKey(int keyCode, int ch, Output out) {
        boolean empty = mComposer.isEmpty();
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (empty) return false;
                KanaKanjiConverter converter = mConverter;
                if (converter == null) {
                    commit(out);
                } else {
                    mConversion = converter.convert(mComposer.settle());
                }
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

    private boolean convertingKey(int keyCode, int ch, boolean shift, Output out) {
        Conversion c = mConversion;
        switch (keyCode) {
            case KeyEvent.KEYCODE_SPACE:
                if (shift) c.prev(); else c.next();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                c.next();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                c.prev();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (shift) c.resize(-1); else c.moveFocus(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (shift) c.resize(1); else c.moveFocus(1);
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                commit(out);
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_DEL:
                mConversion = null; // back to editable hiragana
                return true;
            default:
        }
        int digit = digitOf(keyCode);
        if (digit > 0 && !shift) {
            c.select(digit);
            return true;
        }
        if (ch <= 0) return false;
        // Any other printable key commits the conversion and starts the next composition.
        commit(out);
        return mComposer.feed((char) ch);
    }

    private static int digitOf(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) return keyCode - KeyEvent.KEYCODE_0;
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_1 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        return 0;
    }

    /** Writes the converted text (or the hiragana when not converting) and clears the composition. */
    public void commit(Output out) {
        if (isEmpty()) return;
        String text = mConversion != null ? mConversion.commit() : mComposer.commit();
        mConversion = null;
        mComposer.cancel();
        if (!text.isEmpty()) out.write(text);
    }

    public void cancel() {
        mConversion = null;
        mComposer.cancel();
    }
}
