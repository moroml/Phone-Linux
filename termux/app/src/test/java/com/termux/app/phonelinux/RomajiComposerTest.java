package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RomajiComposerTest {

    private static String type(String keys) {
        RomajiComposer c = new RomajiComposer();
        for (char ch : keys.toCharArray()) assertTrue("feed " + ch, c.feed(ch));
        return c.commit();
    }

    @Test
    public void basics() {
        assertEquals("あいうえお", type("aiueo"));
        assertEquals("こんにちは", type("konnnichiha"));
        assertEquals("しゃしんをとる", type("shashinwotoru"));
        assertEquals("つ", type("tsu"));
    }

    @Test
    public void nHandling() {
        assertEquals("かんじ", type("kannji"));
        assertEquals("かんじ", type("kanji"));
        assertEquals("ん", type("n'"));
        assertEquals("ん", type("nn"));
        assertEquals("にゃ", type("nya"));
        assertEquals("なん", type("nan"));
        assertEquals("ほんや", type("honnya"));
        assertEquals("ん", type("n")); // pending n is committed as ん
    }

    @Test
    public void doubleConsonant() {
        assertEquals("っか", type("kka"));
        assertEquals("きって", type("kitte"));
        assertEquals("まっちゃ", type("mattya"));
        assertEquals("ざっし", type("zasshi"));
    }

    @Test
    public void smallKana() {
        assertEquals("っ", type("xtu"));
        assertEquals("っ", type("ltsu"));
        assertEquals("ぁ", type("xa"));
        assertEquals("ゃ", type("lya"));
        assertEquals("ふぁいる", type("fairu"));
    }

    @Test
    public void youon() {
        assertEquals("きょう", type("kyou"));
        assertEquals("じゃ", type("ja"));
        assertEquals("ちゃ", type("cha"));
        assertEquals("ちゃ", type("tya"));
        assertEquals("じゅ", type("zyu"));
    }

    @Test
    public void punctuation() {
        assertEquals("ー", type("-"));
        assertEquals("あ、い。", type("a,i."));
        assertEquals("「あ」", type("[a]"));
        assertEquals("かー", type("ka-"));
    }

    @Test
    public void nothingIsComposedWhenEmptyAndNotAStarter() {
        RomajiComposer c = new RomajiComposer();
        assertFalse(c.feed('1'));
        assertFalse(c.feed(' '));
        assertFalse(c.feed('!'));
        assertTrue(c.isEmpty());
    }

    @Test
    public void nonStartersAppendWhileComposing() {
        RomajiComposer c = new RomajiComposer();
        c.feed('n');
        assertTrue(c.feed('1'));
        assertEquals("ん1", c.preedit());
        assertEquals("ん1", c.commit());
    }

    @Test
    public void uppercaseIsLiteral() {
        RomajiComposer c = new RomajiComposer();
        c.feed('k');
        c.feed('A');
        assertEquals("kA", c.preedit());
    }

    @Test
    public void invalidRomajiStaysLiteral() {
        assertEquals("kば", type("kba"));
    }

    @Test
    public void preeditShowsPendingRomaji() {
        RomajiComposer c = new RomajiComposer();
        c.feed('k');
        c.feed('a');
        c.feed('k');
        assertEquals("かk", c.preedit());
    }

    @Test
    public void backspaceEditsPendingThenKana() {
        RomajiComposer c = new RomajiComposer();
        for (char ch : "kak".toCharArray()) c.feed(ch);
        assertTrue(c.backspace());
        assertEquals("か", c.preedit());
        assertTrue(c.backspace());
        assertTrue(c.isEmpty());
        assertFalse(c.backspace());
    }

    @Test
    public void cancelClears() {
        RomajiComposer c = new RomajiComposer();
        c.feed('a');
        c.cancel();
        assertTrue(c.isEmpty());
        assertEquals("", c.commit());
    }
}
