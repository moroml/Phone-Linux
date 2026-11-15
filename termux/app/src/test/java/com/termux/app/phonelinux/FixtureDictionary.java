package com.termux.app.phonelinux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A tiny Mozc-style dictionary with a hand-made POS connection table, for converter tests. */
final class FixtureDictionary implements KanaDictionary {

    static final int NOUN = 1, PARTICLE = 2, VERB = 3, AUX = 4, PLACE = 5, FOOD = 6;

    private final Map<String, List<Word>> mWords = new HashMap<>();

    FixtureDictionary() {
        add("わたし", "私", NOUN, 3000);
        add("わたし", "渡し", NOUN, 4000);
        add("は", "は", PARTICLE, 500);
        add("は", "葉", NOUN, 3500);
        add("は", "歯", NOUN, 3600);
        add("が", "が", PARTICLE, 500);
        add("がくせい", "学生", NOUN, 3000);
        add("がくせい", "学制", NOUN, 4000);
        add("がく", "額", NOUN, 3000);
        add("せい", "所為", NOUN, 3000);
        add("です", "です", AUX, 500);
        add("で", "で", PARTICLE, 500);
        add("す", "巣", NOUN, 3000);
        add("きょう", "今日", NOUN, 3000);
        add("てんき", "天気", NOUN, 3000);
        add("かく", "書く", VERB, 3000);
        add("かく", "欠く", VERB, 3500);
        add("かっ", "買っ", VERB, 3000);
        add("かっ", "勝っ", VERB, 3200);
        add("た", "た", AUX, 300);
        add("ごはん", "ご飯", FOOD, 3000);
        add("はし", "橋", PLACE, 3000);
        add("はし", "箸", FOOD, 3500);
        for (int i = 0; i < 12; i++) add("ぬる", "候補" + i, NOUN, 3000 + i);
    }

    private void add(String reading, String surface, int pos, int cost) {
        List<Word> l = mWords.get(reading);
        if (l == null) mWords.put(reading, l = new ArrayList<>());
        l.add(new Word(surface, pos, pos, cost));
    }

    @Override
    public List<Word> lookup(String reading, int from, int to) {
        List<Word> l = mWords.get(reading.substring(from, to));
        return l == null ? new ArrayList<Word>() : new ArrayList<>(l);
    }

    @Override
    public int connectionCost(int rid, int lid) {
        if (rid == 0) return lid == NOUN || lid == PLACE || lid == FOOD ? 500 : 800; // BOS
        if (lid == 0) return rid == AUX ? 300 : 800; // EOS
        if (rid == FOOD && lid == FOOD) return 300;
        if (rid == FOOD && lid == PLACE) return 4000;
        if ((rid == NOUN || rid == PLACE || rid == FOOD) && (lid == PARTICLE || lid == AUX)) return 500;
        if (rid == PARTICLE && (lid == NOUN || lid == VERB)) return 500;
        if (rid == VERB && lid == AUX) return 400;
        return 2500;
    }

    @Override
    public int unknownId() {
        return NOUN;
    }
}
