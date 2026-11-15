package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KanaKanjiConverterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Map<String, List<String>> FIXTURE = new HashMap<>();

    static {
        put("わたし", "私", "渡し");
        put("がくせい", "学生");
        put("かk", "書", "欠");
        put("きょう", "今日");
        put("てんき", "天気");
        put("は", "葉", "歯");
        put("おもu", "思", "想");
        put("かt", "勝", "買");
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) many.add("候補" + i);
        FIXTURE.put("ぬる", many);
    }

    private static void put(String k, String... v) {
        FIXTURE.put(k, Arrays.asList(v));
    }

    private static final KanaDictionary DICT = new KanaDictionary() {
        @Override
        public List<String> lookup(String key) {
            List<String> v = FIXTURE.get(key);
            return v == null ? new ArrayList<String>() : new ArrayList<>(v);
        }
    };

    private static KanaKanjiConverter converter(UserDictionary user) {
        return new KanaKanjiConverter(DICT, user);
    }

    private static KanaKanjiConverter converter() {
        return converter(new UserDictionary(null));
    }

    @Test
    public void segmentsSentence() {
        Conversion c = converter().convert("わたしはがくせい");
        assertEquals("私は学生", c.text());
        assertEquals(3, c.segmentCount());
    }

    @Test
    public void particlesStayKana() {
        // は has dictionary words (葉, 歯) but the kana itself comes first
        Conversion c = converter().convert("きょうはてんき");
        assertEquals("今日は天気", c.text());
        c.moveFocus(1);
        assertEquals(Arrays.asList("は", "葉", "歯", "ハ"), c.pageCandidates());
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
    public void okuriAri() {
        Conversion c = converter().convert("かく");
        assertEquals("書く", c.text());
        assertEquals(Arrays.asList("書く", "欠く", "かく", "カク"), c.pageCandidates());
        assertEquals("思う", converter().convert("おもう").text());
        assertEquals("勝った", converter().convert("かった").text());
    }

    @Test
    public void unknownKanaFallback() {
        Conversion c = converter().convert("ぬぬ");
        assertEquals("ぬぬ", c.text());
        assertEquals(2, c.segmentCount()); // each unknown kana is its own segment
        assertEquals(Arrays.asList("ぬ", "ヌ"), c.pageCandidates());
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
        Conversion c = converter().convert("わたしがくせい");
        assertEquals("私学生", c.text());
        assertTrue(c.resize(-1)); // わた + しがくせい
        assertEquals("わた", c.focusReading());
        assertTrue(c.text().startsWith("わた"));
        assertTrue(c.resize(1)); // back to わたし
        assertEquals("私学生", c.text());
        assertEquals(2, c.segmentCount());
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
        // learning an okuri-ari choice
        Conversion k = converter(user).convert("かく");
        k.next();
        k.commit();
        assertEquals("欠く", converter(new UserDictionary(f)).convert("かく").text());
    }

    @Test
    public void untouchedChoiceIsNotLearned() {
        UserDictionary user = new UserDictionary(null);
        converter(user).convert("わたし").commit();
        assertTrue(user.lookup("わたし").isEmpty());
    }

    @Test
    public void userEntriesUsedForSegmentation() {
        UserDictionary user = new UserDictionary(null);
        user.promote("ぬぬ", "奴々");
        assertEquals("奴々", converter(user).convert("ぬぬ").text());
    }

    @Test
    public void userDictionaryKeepsAtMost2000Readings() {
        UserDictionary user = new UserDictionary(null);
        for (int i = 0; i < UserDictionary.MAX_READINGS + 5; i++) user.promote("よみ" + i, "字");
        assertTrue(user.lookup("よみ0").isEmpty());
        assertFalse(user.lookup("よみ" + (UserDictionary.MAX_READINGS + 4)).isEmpty());
    }
}
