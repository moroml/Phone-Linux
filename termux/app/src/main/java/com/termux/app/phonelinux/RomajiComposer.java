package com.termux.app.phonelinux;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Romaji to hiragana composer used for hardware keys in JP mode. No kanji conversion. */
public final class RomajiComposer {

    private static final Map<String, String> TABLE = new HashMap<>();
    private static final Set<String> PREFIXES = new HashSet<>();
    private static final String SMALL_YA = "ゃゅょ";

    static {
        String[][] rows = {
            {"", "あいうえお"}, {"k", "かきくけこ"}, {"g", "がぎぐげご"}, {"s", "さしすせそ"},
            {"z", "ざじずぜぞ"}, {"t", "たちつてと"}, {"d", "だぢづでど"}, {"n", "なにぬねの"},
            {"h", "はひふへほ"}, {"b", "ばびぶべぼ"}, {"p", "ぱぴぷぺぽ"}, {"m", "まみむめも"},
            {"r", "らりるれろ"},
        };
        for (String[] row : rows) {
            for (int i = 0; i < 5; i++) add(row[0] + "aiueo".charAt(i), row[1].substring(i, i + 1));
            // youon: kya, gyu, ... = i-row kana + small ya/yu/yo
            if (!row[0].isEmpty()) {
                for (int i = 0; i < 3; i++) add(row[0] + "y" + "auo".charAt(i), row[1].charAt(1) + SMALL_YA.substring(i, i + 1));
            }
        }
        add("ya", "や"); add("yu", "ゆ"); add("yo", "よ");
        add("wa", "わ"); add("wi", "うぃ"); add("wu", "う"); add("we", "うぇ"); add("wo", "を");
        add("si", "し"); add("ti", "ち"); add("tu", "つ"); add("hu", "ふ"); add("zi", "じ");
        add("shi", "し"); add("chi", "ち"); add("tsu", "つ"); add("fu", "ふ"); add("ji", "じ");
        for (String[] e : new String[][] {{"sh", "し"}, {"ch", "ち"}, {"j", "じ"}}) {
            add(e[0] + "a", e[1] + "ゃ"); add(e[0] + "u", e[1] + "ゅ");
            add(e[0] + "e", e[1] + "ぇ"); add(e[0] + "o", e[1] + "ょ");
        }
        add("fa", "ふぁ"); add("fi", "ふぃ"); add("fe", "ふぇ"); add("fo", "ふぉ");
        add("tsa", "つぁ"); add("tsi", "つぃ"); add("tse", "つぇ"); add("tso", "つぉ");
        add("thi", "てぃ"); add("dhi", "でぃ"); add("thu", "てゅ"); add("dhu", "でゅ");
        add("nn", "ん"); add("n'", "ん");
        for (String x : new String[] {"x", "l"}) {
            for (int i = 0; i < 5; i++) add(x + "aiueo".charAt(i), "ぁぃぅぇぉ".substring(i, i + 1));
            for (int i = 0; i < 3; i++) add(x + "y" + "auo".charAt(i), SMALL_YA.substring(i, i + 1));
            add(x + "tu", "っ"); add(x + "tsu", "っ"); add(x + "wa", "ゎ");
        }
    }

    private static void add(String romaji, String kana) {
        TABLE.put(romaji, kana);
        for (int i = 1; i < romaji.length(); i++) PREFIXES.add(romaji.substring(0, i));
    }

    private final StringBuilder mDone = new StringBuilder();
    private final StringBuilder mPending = new StringBuilder();

    public boolean isEmpty() {
        return mDone.length() == 0 && mPending.length() == 0;
    }

    /** Uncommitted text: converted kana followed by the romaji still waiting for more keys. */
    public String preedit() {
        return mDone.toString() + mPending;
    }

    private static boolean isStarter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-' || c == ',' || c == '.' || c == '[' || c == ']';
    }

    /** Returns false (and does nothing) when the preedit is empty and the character does not start a composition. */
    public boolean feed(char c) {
        if (isEmpty() && !isStarter(c)) return false;
        if ((c >= 'a' && c <= 'z') || (c == '\'' && mPending.length() == 1 && mPending.charAt(0) == 'n')) {
            mPending.append(c);
            resolve();
            return true;
        }
        flush();
        switch (c) {
            case '-': mDone.append('ー'); break;
            case ',': mDone.append('、'); break;
            case '.': mDone.append('。'); break;
            case '[': mDone.append('「'); break;
            case ']': mDone.append('」'); break;
            default: mDone.append(c);
        }
        return true;
    }

    private void resolve() {
        while (mPending.length() > 0) {
            String p = mPending.toString();
            String kana = TABLE.get(p);
            if (kana != null) {
                mDone.append(kana);
                mPending.setLength(0);
                return;
            }
            char first = p.charAt(0);
            if (p.length() >= 2 && first == p.charAt(1) && "aiueon".indexOf(first) < 0) {
                mDone.append('っ');
                mPending.deleteCharAt(0);
                continue;
            }
            if (PREFIXES.contains(p)) return;
            // "n" before a consonant is ん; any other dead end stays literal
            mDone.append(first == 'n' ? 'ん' : first);
            mPending.deleteCharAt(0);
        }
    }

    private void flush() {
        if (mPending.length() == 1 && mPending.charAt(0) == 'n') mDone.append('ん');
        else mDone.append(mPending);
        mPending.setLength(0);
    }

    /** Removes the last pending romaji character, or else the last converted character. */
    public boolean backspace() {
        if (mPending.length() > 0) {
            mPending.deleteCharAt(mPending.length() - 1);
            return true;
        }
        if (mDone.length() > 0) {
            mDone.deleteCharAt(mDone.length() - 1);
            return true;
        }
        return false;
    }

    /** Turns pending romaji into text (a lone n becomes ん) and returns the whole preedit, keeping it editable. */
    public String settle() {
        flush();
        return mDone.toString();
    }

    /** Returns the text to write to the terminal and clears the composer. */
    public String commit() {
        flush();
        String s = mDone.toString();
        mDone.setLength(0);
        return s;
    }

    public void cancel() {
        mDone.setLength(0);
        mPending.setLength(0);
    }
}
