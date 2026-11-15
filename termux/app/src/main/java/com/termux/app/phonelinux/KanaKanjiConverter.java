package com.termux.app.phonelinux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hiragana to mixed kanji text by minimum-cost segmentation over dictionary words, SKK okuri-ari
 * entries (`かk` + く = 書く) and plain kana fallback.
 */
public final class KanaKanjiConverter {

    static final int MAX_READING = 16;
    private static final double WORD_COST = 1.0, SINGLE_PENALTY = 0.3, UNKNOWN_COST = 1.5;
    /** Okurigana that end verbs: an okuri-ari reading is listed before the plain word for these. */
    private static final String VERB_ENDINGS = "うくぐすつぬぶむる";
    private static final String[] OKURI_ROWS = {
        "ぁあ:a", "ぃい:i", "ぅう:u", "ぇえ:e", "ぉお:o", "かきくけこ:k", "がぎぐげご:g", "さしすせそ:s",
        "ざじずぜぞ:z", "たちつてと:t", "だぢづでど:d", "なにぬねの:n", "はひふへほ:h", "ばびぶべぼ:b",
        "ぱぴぷぺぽ:p", "まみむめも:m", "やゆよ:y", "らりるれろ:r", "わ:w", "っ:t", "ん:n",
    };

    /** One choice for a segment; key/base are what learning stores (null for plain kana). */
    public static final class Candidate {
        public final String text, key, base;

        Candidate(String text, String key, String base) {
            this.text = text;
            this.key = key;
            this.base = base;
        }
    }

    /** reading[start, end) with its candidates and the chosen one. */
    public static final class Segment {
        public final int start, end;
        public final List<Candidate> candidates;
        public int index;

        Segment(int start, int end, List<Candidate> candidates) {
            this.start = start;
            this.end = end;
            this.candidates = candidates;
        }

        public String text() {
            return candidates.get(index).text;
        }
    }

    private final KanaDictionary mBase;
    private final UserDictionary mUser;

    public KanaKanjiConverter(KanaDictionary base, UserDictionary user) {
        mBase = base;
        mUser = user;
    }

    public Conversion convert(String reading) {
        return new Conversion(this, reading, segment(reading, 0));
    }

    /** Best segmentation of reading[from..]. */
    List<Segment> segment(String r, int from) {
        int n = r.length();
        double[] best = new double[n + 1];
        int[] next = new int[n + 1];
        List<List<Candidate>> chosen = new ArrayList<>();
        for (int i = 0; i <= n; i++) chosen.add(null);
        for (int i = n - 1; i >= from; i--) {
            best[i] = UNKNOWN_COST + best[i + 1];
            next[i] = i + 1;
            for (int j = Math.min(n, i + MAX_READING); j > i; j--) {
                double cost = WORD_COST + (j - i == 1 ? SINGLE_PENALTY : 0) + best[j];
                if (cost >= best[i]) continue;
                List<Candidate> words = wordCandidates(r, i, j);
                if (words.isEmpty()) continue;
                best[i] = cost;
                next[i] = j;
                chosen.set(i, words);
            }
        }
        List<Segment> out = new ArrayList<>();
        for (int i = from; i < n; i = next[i]) out.add(build(r, i, next[i], chosen.get(i)));
        return out;
    }

    /** A segment forced to cover reading[i, j). */
    Segment span(String r, int i, int j) {
        return build(r, i, j, wordCandidates(r, i, j));
    }

    private Segment build(String r, int i, int j, List<Candidate> words) {
        String hira = r.substring(i, j);
        Candidate kana = new Candidate(hira, null, null);
        Map<String, Candidate> out = new LinkedHashMap<>();
        if (words == null) words = new ArrayList<>();
        if (j - i == 1) {
            // particles stay kana unless the user has picked a word for this kana before
            for (Candidate c : words) if (mUser.lookup(c.key).contains(c.base)) out.putIfAbsent(c.text, c);
            out.putIfAbsent(hira, kana);
        }
        for (Candidate c : words) out.putIfAbsent(c.text, c);
        out.putIfAbsent(hira, kana);
        String kata = katakana(hira);
        out.putIfAbsent(kata, new Candidate(kata, null, null));
        return new Segment(i, j, new ArrayList<>(out.values()));
    }

    /** Dictionary candidates for reading[i, j) as a word and as stem + okurigana. */
    private List<Candidate> wordCandidates(String r, int i, int j) {
        String reading = r.substring(i, j);
        List<Candidate> plain = new ArrayList<>(), okuri = new ArrayList<>();
        for (String w : words(reading)) plain.add(new Candidate(w, reading, w));
        char last = r.charAt(j - 1);
        char letter = j - i >= 2 ? okuriLetter(last) : 0;
        if (letter != 0) {
            String key = r.substring(i, j - 1) + letter;
            for (String w : words(key)) okuri.add(new Candidate(w + last, key, w));
        }
        List<Candidate> out = new ArrayList<>();
        boolean verbLike = VERB_ENDINGS.indexOf(last) >= 0;
        out.addAll(verbLike ? okuri : plain);
        out.addAll(verbLike ? plain : okuri);
        return out;
    }

    private List<String> words(String key) {
        List<String> out = mUser.lookup(key);
        for (String w : mBase.lookup(key)) if (!out.contains(w)) out.add(w);
        return out;
    }

    /** Moves every chosen non-default word candidate to the front for next time. */
    void learn(List<Segment> segments) {
        boolean any = false;
        for (Segment s : segments) {
            Candidate c = s.candidates.get(s.index);
            if (s.index == 0 || c.key == null) continue;
            mUser.promote(c.key, c.base);
            any = true;
        }
        if (any) mUser.save();
    }

    /** SKK okuri letter for an okurigana, or 0 when it cannot be one. */
    static char okuriLetter(char c) {
        for (String row : OKURI_ROWS) {
            int colon = row.indexOf(':');
            if (row.indexOf(c) >= 0 && row.indexOf(c) < colon) return row.charAt(colon + 1);
        }
        return 0;
    }

    static String katakana(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) b.append(c >= 'ぁ' && c <= 'ゖ' ? (char) (c + 0x60) : c);
        return b.toString();
    }
}
