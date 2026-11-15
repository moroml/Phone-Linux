package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JapaneseCompositionTest {

    private final JapaneseComposition mComp = new JapaneseComposition();
    private final StringBuilder mOut = new StringBuilder();
    private final JapaneseComposition.Output mSink = new JapaneseComposition.Output() {
        @Override
        public void write(String text) {
            mOut.append(text);
        }
    };

    private static final KanaDictionary DICT = new KanaDictionary() {
        @Override
        public List<String> lookup(String key) {
            switch (key) {
                case "わたし": return new ArrayList<>(Arrays.asList("私", "渡し"));
                case "かk": return new ArrayList<>(Arrays.asList("書"));
                default: return new ArrayList<>();
            }
        }
    };

    @Before
    public void setUp() {
        mComp.setConverter(new KanaKanjiConverter(DICT, new UserDictionary(null)));
    }

    private boolean key(int keyCode) {
        return mComp.onKey(keyCode, 0, false, mSink);
    }

    private void type(String romaji) {
        for (char c : romaji.toCharArray()) assertTrue(mComp.onKey(KeyEvent.KEYCODE_A, c, false, mSink));
    }

    @Test
    public void enterStartsConversionLikeSpace() {
        type("watashi");
        assertTrue(key(KeyEvent.KEYCODE_ENTER));
        assertTrue(mComp.isConverting());
        assertEquals("私", mComp.preedit());
        assertEquals("", mOut.toString());
        assertEquals(Arrays.asList("私", "渡し", "わたし", "ワタシ"), mComp.candidates());
        // Enter while converting commits and is consumed
        assertTrue(key(KeyEvent.KEYCODE_ENTER));
        assertEquals("私", mOut.toString());
        assertTrue(mComp.isEmpty());
    }

    @Test
    public void spaceStartsThenCycles() {
        type("watashi");
        assertTrue(key(KeyEvent.KEYCODE_SPACE));
        assertTrue(key(KeyEvent.KEYCODE_SPACE));
        assertEquals("渡し", mComp.preedit());
        assertEquals(1, mComp.candidateIndex());
        assertTrue(mComp.onKey(KeyEvent.KEYCODE_SPACE, 0, true, mSink));
        assertEquals("私", mComp.preedit());
        assertTrue(mComp.onKey(KeyEvent.KEYCODE_3, '3', false, mSink));
        assertEquals("わたし", mComp.preedit());
    }

    @Test
    public void notReadyCommitsHiragana() {
        mComp.setConverter(null);
        type("watashi");
        assertTrue(key(KeyEvent.KEYCODE_ENTER));
        assertEquals("わたし", mOut.toString());
        assertTrue(mComp.isEmpty());
        type("watashi");
        assertTrue(key(KeyEvent.KEYCODE_SPACE));
        assertEquals("わたしわたし", mOut.toString());
    }

    @Test
    public void emptyCompositionPassesKeysThrough() {
        assertFalse(key(KeyEvent.KEYCODE_ENTER));
        assertFalse(key(KeyEvent.KEYCODE_SPACE));
    }

    @Test
    public void pendingNIsSettledBeforeConversion() {
        type("kan");
        assertTrue(key(KeyEvent.KEYCODE_ENTER));
        assertEquals("かん", mComp.preedit());
    }

    @Test
    public void escapeAndBackspaceReturnToHiragana() {
        type("watashi");
        key(KeyEvent.KEYCODE_ENTER);
        assertTrue(key(KeyEvent.KEYCODE_ESCAPE));
        assertFalse(mComp.isConverting());
        assertEquals("わたし", mComp.preedit());
        key(KeyEvent.KEYCODE_SPACE);
        assertTrue(key(KeyEvent.KEYCODE_DEL));
        assertEquals("わたし", mComp.preedit());
        assertNull(mComp.focus());
        assertNull(mComp.candidates());
    }

    @Test
    public void printableKeyCommitsAndStartsNewComposition() {
        type("watashi");
        key(KeyEvent.KEYCODE_ENTER);
        assertTrue(mComp.onKey(KeyEvent.KEYCODE_K, 'k', false, mSink));
        assertEquals("私", mOut.toString());
        assertFalse(mComp.isConverting());
        assertEquals("k", mComp.preedit());
    }

    @Test
    public void focusRangeAndSegmentMoves() {
        type("watashikaku");
        key(KeyEvent.KEYCODE_SPACE);
        assertEquals("私書く", mComp.preedit());
        assertEquals(1, mComp.focus()[1]);
        assertTrue(key(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(1, mComp.focus()[0]);
        assertEquals(3, mComp.focus()[1]);
        mComp.commit(mSink);
        assertEquals("私書く", mOut.toString());
    }

    @Test
    public void shortcutCommitWritesConvertedText() {
        type("watashi");
        key(KeyEvent.KEYCODE_SPACE);
        mComp.commit(mSink); // what Ctrl/Alt shortcuts and the JP toggle do
        assertEquals("私", mOut.toString());
    }
}
