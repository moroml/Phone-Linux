package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

public class KanaKanjiConverterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static KanaKanjiConverter converter(UserDictionary user) {
        return new KanaKanjiConverter(new FixtureDictionary(), user);
    }

    private static KanaKanjiConverter converter() {
        return converter(new UserDictionary(null));
    }

    @Test
    public void segmentsSentence() {
        Conversion c = converter().convert("わたしはがくせい");
        assertEquals("私は学生", c.text());
        assertEquals(2, c.segmentCount()); // [私は][学生]
        assertEquals("わたしは", c.focusReading());
    }

    @Test
    public void particlesStayKana() {
        // は has dictionary words (葉, 歯) but the particle stays attached to its bunsetsu in kana
        Conversion c = converter().convert("きょうはてんき");
        assertEquals("今日は天気", c.text());
        assertEquals(2, c.segmentCount());
        assertEquals("今日は", c.pageCandidates().get(0));
        c.prev(); // wraps to the last candidate: katakana of the whole bunsetsu
        assertEquals("キョウハ天気", c.text());
    }

    @Test
    public void candidateOrderAndKatakana() {
        Conversion c = converter().convert("わたし");
        assertEquals(Arrays.asList("私", "渡し", "わたし", "ワタシ"), c.pageCandidates());
        c.next();
        assertEquals("渡し", c.text());
        c.prev();
        c.prev(); // wraps to the last candidate: katakana
        assertEquals("ワタシ", c.text());
    }

    @Test
    public void inflectedFormsComeFromTheDictionary() {
        Conversion c = converter().convert("かく");
        assertEquals("書く", c.text());
        assertEquals(Arrays.asList("書く", "欠く", "かく", "カク"), c.pageCandidates());
        Conversion k = converter().convert("かった");
        assertEquals("買った", k.text());
        assertEquals(1, k.segmentCount()); // 買っ + た are one bunsetsu
        assertEquals("買った", k.pageCandidates().get(0));
        assertEquals("勝った", k.pageCandidates().get(1));
    }

    @Test
    public void connectionCostBreaksTies() {
        // 橋 is cheaper as a word, but 箸 connects better after ご飯 (one noun-compound bunsetsu)
        Conversion c = converter().convert("ごはんはし");
        assertEquals("ご飯箸", c.text());
        assertEquals(1, c.segmentCount());
        assertTrue(c.pageCandidates().contains("ご飯橋"));
        assertEquals("橋", converter().convert("はし").text());
        // the left context of a bunsetsu decides its ranking: after ご飯 (a separate bunsetsu) 箸 leads
        assertTrue(c.resize(-2));
        assertEquals("ごはん", c.focusReading());
        c.moveFocus(1);
        assertEquals(Arrays.asList("箸", "橋", "はし"), c.pageCandidates().subList(0, 3));
    }

    @Test
    public void functionWordsStayKana() {
        // です beats で + 巣 on connection cost, and it stays inside the bunsetsu
        Conversion c = converter().convert("がくせいです");
        assertEquals("学生です", c.text());
        assertEquals(1, c.segmentCount());
        assertEquals(Arrays.asList("学生です", "学制です"), c.pageCandidates().subList(0, 2));
        assertTrue(c.pageCandidates().contains("学生で巣") || c.pageCandidates().size() == Conversion.PAGE);
    }

    @Test
    public void unknownKanaFallback() {
        Conversion c = converter().convert("ぬぬ");
        assertEquals("ぬぬ", c.text());
        assertEquals(1, c.segmentCount()); // a run of unknown kana stays one bunsetsu
        assertEquals(Arrays.asList("ぬぬ", "ヌヌ"), c.pageCandidates());
    }

    @Test
    public void paging() {
        Conversion c = converter().convert("ぬる");
        assertEquals(9, c.pageCandidates().size());
        for (int i = 0; i < 9; i++) c.next();
        assertEquals("候補9", c.text());
        assertEquals(Arrays.asList("候補9", "候補10", "候補11", "ぬる", "ヌル"), c.pageCandidates());
        assertEquals(0, c.pageIndex());
    }

    @Test
    public void selectMovesFocus() {
        Conversion c = converter().convert("わたしはがくせい");
        assertTrue(c.select(2));
        assertEquals(1, c.focus());
        assertEquals("渡しは学生", c.text());
        assertFalse(c.select(9));
    }

    @Test
    public void resizeResegmentsRemainder() {
        Conversion c = converter().convert("わたしはがくせい");
        assertEquals("私は学生", c.text());
        assertTrue(c.resize(-1)); // わたし + は + がくせい
        assertEquals("わたし", c.focusReading());
        assertEquals(3, c.segmentCount());
        assertEquals("私は学生", c.text());
        assertTrue(c.resize(1)); // back to わたしは
        assertEquals("わたしは", c.focusReading());
        assertEquals("私は学生", c.text());
        assertEquals(2, c.segmentCount());
        assertFalse(c.resize(10));
    }

    @Test
    public void compoundNounsAndPrefixesFormOneBunsetsu() {
        assertEquals(1, converter().convert("きょうてんき").segmentCount());
        Conversion c = converter().convert("おちゃをかく");
        assertEquals("お茶を書く", c.text());
        assertEquals(2, c.segmentCount()); // [お茶を][書く]: the prefix and the particle attach
        assertEquals("おちゃを", c.focusReading());
    }

    @Test
    public void suruAttachesToAPrecedingNoun() {
        Conversion c = converter().convert("がくせいをへんかんする");
        assertEquals("学生を変換する", c.text());
        assertEquals(2, c.segmentCount()); // [学生を][変換する]
        c.moveFocus(1);
        assertEquals("へんかんする", c.focusReading());
    }

    @Test
    public void learningMovesChoiceToFront() throws Exception {
        File f = new File(tmp.getRoot(), "sub/user-dict.tsv");
        UserDictionary user = new UserDictionary(f);
        Conversion c = converter(user).convert("わたし");
        c.next();
        assertEquals("渡し", c.commit());
        assertEquals("渡し", converter(user).convert("わたし").text());
        // persisted
        assertEquals("渡し", converter(new UserDictionary(f)).convert("わたし").text());
        // learning a verb choice
        Conversion k = converter(user).convert("かく");
        k.next();
        k.commit();
        assertEquals("欠く", converter(new UserDictionary(f)).convert("かく").text());
    }

    @Test
    public void choosingABunsetsuLearnsItsContentWordsOnly() {
        UserDictionary user = new UserDictionary(null);
        Conversion c = converter(user).convert("わたしはがくせい");
        assertTrue(c.select(2)); // 渡しは
        assertEquals("渡しは学生", c.commit());
        assertEquals(Arrays.asList("渡し"), user.lookup("わたし"));
        assertTrue(user.lookup("は").isEmpty()); // particles are not learned
        assertTrue(user.lookup("がくせい").isEmpty()); // the untouched bunsetsu is not learned
        assertEquals("渡しは学生", converter(user).convert("わたしはがくせい").text());
    }

    @Test
    public void untouchedChoiceIsNotLearned() {
        UserDictionary user = new UserDictionary(null);
        converter(user).convert("わたし").commit();
        assertTrue(user.lookup("わたし").isEmpty());
    }

    @Test
    public void learnedWordsAreCheaperInSegmentation() {
        UserDictionary user = new UserDictionary(null);
        user.promote("ぬぬ", "奴々");
        assertEquals("奴々", converter(user).convert("ぬぬ").text());
        user.promote("がくせい", "学制");
        assertEquals("学制", converter(user).convert("がくせい").text());
    }

    @Test
    public void mostRecentlyLearnedChoiceWins() {
        UserDictionary user = new UserDictionary(null);
        user.promote("がくせい", "学制");
        user.promote("がくせい", "楽聖");
        assertEquals("楽聖", converter(user).convert("がくせい").text());
    }

    @Test
    public void growingASegmentPastTheReadingLimitKeepsAllText() {
        StringBuilder ks = new StringBuilder();
        for (int i = 0; i < KanaKanjiConverter.MAX_READING; i++) ks.append('ぬ');
        Conversion c = converter().convert(ks + "わたし");
        while (c.focusReading().length() < KanaKanjiConverter.MAX_READING) assertTrue(c.resize(1));
        assertTrue(c.resize(1)); // one kana past the limit
        String span = ks + "わ";
        assertEquals(span, c.focusReading());
        assertTrue(c.text(), c.text().startsWith(span) && c.text().length() > span.length());
    }

    @Test
    public void userDictionaryKeepsAtMost2000Readings() {
        UserDictionary user = new UserDictionary(null);
        for (int i = 0; i < UserDictionary.MAX_READINGS + 5; i++) user.promote("よみ" + i, "字");
        assertTrue(user.lookup("よみ0").isEmpty());
        assertFalse(user.lookup("よみ" + (UserDictionary.MAX_READINGS + 4)).isEmpty());
    }
}
