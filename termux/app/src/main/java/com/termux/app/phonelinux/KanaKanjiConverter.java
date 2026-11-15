package com.termux.app.phonelinux;

import com.termux.app.phonelinux.KanaDictionary.Word;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hiragana to mixed kanji text: Viterbi search over the lattice of dictionary words (Mozc model:
 * word cost + part-of-speech connection cost), with a costly one-kana fallback so a path always exists.
 */
public final class KanaKanjiConverter {

    static final int MAX_READING = 16;
    /** Higher than any dictionary word, so unknown kana only appear where nothing else fits. */
    private static final int UNKNOWN_COST = 12000;
    /** A learned surface is made this much cheaper than the cheapest word of its reading. */
    private static final int LEARNED_BONUS = 1000;
    private static final int LEARNED_UNKNOWN_COST = 4000;

    /** One choice for a segment; key is the reading that learning stores it under. */
    public static final class Candidate {
        public final String text, key;
        final int lid, rid, cost;

        Candidate(String text, String key, int lid, int rid, int cost) {
            this.text = text;
            this.key = key;
            this.lid = lid;
            this.rid = rid;
            this.cost = cost;
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

        Candidate chosen() {
            return candidates.get(index);
        }
    }

    private static final class Node {
        final int start, end;
        final Word word;
        Node prev;
        long total;

        Node(int start, int end, Word word) {
            this.start = start;
            this.end = end;
            this.word = word;
        }
    }

    private final KanaDictionary mDict;
    private final UserDictionary mUser;

    public KanaKanjiConverter(KanaDictionary dict, UserDictionary user) {
        mDict = dict;
        mUser = user;
    }

    public Conversion convert(String reading) {
        return new Conversion(this, reading, segment(reading, 0, 0, 0));
    }

    /**
     * Best segmentation of reading[from..].
     *
     * @param forcedEnd if positive, the first segment must be exactly reading[from, forcedEnd)
     * @param prevRid right-edge POS id of the segment before {@code from} (0 at the start of the text)
     */
    List<Segment> segment(String r, int from, int forcedEnd, int prevRid) {
        int n = r.length();
        // ends[e]: best node per right-edge POS id among those ending at e (later nodes only see rid)
        List<Map<Integer, Node>> ends = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) ends.add(new HashMap<Integer, Node>());
        Node bos = new Node(from, from, new Word("", 0, prevRid, 0));
        ends.get(from).put(prevRid, bos);
        for (int i = from; i < n; i++) {
            Collection<Node> prevs = ends.get(i).values();
            if (prevs.isEmpty()) continue;
            for (Node node : nodesAt(r, i, i == from ? forcedEnd : 0)) {
                Node best = null;
                long bestTotal = Long.MAX_VALUE;
                for (Node p : prevs) {
                    long t = p.total + mDict.connectionCost(p.word.rid, node.word.lid);
                    if (t < bestTotal) {
                        bestTotal = t;
                        best = p;
                    }
                }
                node.prev = best;
                node.total = bestTotal + node.word.cost;
                Node old = ends.get(node.end).get(node.word.rid);
                if (old == null || node.total < old.total) ends.get(node.end).put(node.word.rid, node);
            }
        }
        Node last = null;
        long lastTotal = Long.MAX_VALUE;
        for (Node p : ends.get(n).values()) {
            long t = p.total + mDict.connectionCost(p.word.rid, 0);
            if (t < lastTotal) {
                lastTotal = t;
                last = p;
            }
        }
        List<Node> path = new ArrayList<>();
        for (Node p = last; p != null && p != bos; p = p.prev) path.add(p);
        Collections.reverse(path);
        List<Segment> out = new ArrayList<>();
        for (int k = 0; k < path.size(); k++) {
            Node node = path.get(k);
            int left = k == 0 ? prevRid : path.get(k - 1).word.rid;
            int right = k + 1 < path.size() ? path.get(k + 1).word.lid : 0;
            out.add(build(r, node, left, right));
        }
        return out;
    }

    /** Words that start at i (and end at forcedEnd if positive), with the unknown-kana fallback. */
    private List<Node> nodesAt(String r, int i, int forcedEnd) {
        int n = r.length();
        List<Node> out = new ArrayList<>();
        // A forced span may be longer than MAX_READING (Shift+Right); it still needs a node, or the text is lost.
        int last = forcedEnd > 0 ? forcedEnd : Math.min(n, i + MAX_READING);
        for (int j = forcedEnd > 0 ? forcedEnd : i + 1; j <= last; j++) {
            List<Word> words = words(r, i, j);
            for (Word w : words) out.add(new Node(i, j, w));
            if (words.isEmpty() && (j == i + 1 || j == forcedEnd)) out.add(new Node(i, j, unknown(r.substring(i, j))));
        }
        return out;
    }

    private Word unknown(String hira) {
        return new Word(hira, mDict.unknownId(), mDict.unknownId(), UNKNOWN_COST);
    }

    /** Dictionary entries for r[i, j) with learned surfaces first and made cheaper than the rest. */
    private List<Word> words(String r, int i, int j) {
        List<Word> base = mDict.lookup(r, i, j);
        List<String> learned = mUser.lookup(r.substring(i, j));
        if (learned.isEmpty()) return base;
        int min = base.isEmpty() ? LEARNED_UNKNOWN_COST + LEARNED_BONUS : base.get(0).cost;
        int unknownId = mDict.unknownId();
        List<Word> out = new ArrayList<>();
        for (int k = 0; k < learned.size(); k++) {
            String s = learned.get(k);
            Word like = null;
            for (Word w : base) {
                if (w.surface.equals(s)) {
                    like = w;
                    break;
                }
            }
            int lid = like != null ? like.lid : base.isEmpty() ? unknownId : base.get(0).lid;
            int rid = like != null ? like.rid : base.isEmpty() ? unknownId : base.get(0).rid;
            out.add(new Word(s, lid, rid, min - LEARNED_BONUS + k)); // most recent (k = 0) is cheapest
        }
        for (Word w : base) if (!learned.contains(w.surface)) out.add(w);
        return out;
    }

    /** Candidates of the node's span ranked by cost with the chosen neighbours, then kana. */
    private Segment build(String r, Node node, int leftRid, int rightLid) {
        final String hira = r.substring(node.start, node.end);
        List<Word> words = words(r, node.start, node.end);
        final int[] score = new int[words.size()];
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < words.size(); k++) {
            Word w = words.get(k);
            score[k] = w.cost + mDict.connectionCost(leftRid, w.lid) + mDict.connectionCost(w.rid, rightLid);
            order.add(k);
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return score[a] - score[b];
            }
        });
        Map<String, Candidate> out = new LinkedHashMap<>();
        // the Viterbi choice leads (it is missing from the words for the unknown fallback)
        out.put(node.word.surface, new Candidate(node.word.surface, hira, node.word.lid, node.word.rid, node.word.cost));
        for (int k : order) {
            Word w = words.get(k);
            if (!out.containsKey(w.surface)) out.put(w.surface, new Candidate(w.surface, hira, w.lid, w.rid, w.cost));
        }
        int u = mDict.unknownId();
        if (!out.containsKey(hira)) out.put(hira, new Candidate(hira, hira, u, u, UNKNOWN_COST));
        String kata = katakana(hira);
        if (!out.containsKey(kata)) out.put(kata, new Candidate(kata, hira, u, u, UNKNOWN_COST));
        return new Segment(node.start, node.end, new ArrayList<>(out.values()));
    }

    /** Moves every chosen non-default candidate to the front for next time. */
    void learn(List<Segment> segments) {
        boolean any = false;
        for (Segment s : segments) {
            if (s.index == 0) continue;
            Candidate c = s.chosen();
            mUser.promote(c.key, c.text);
            any = true;
        }
        if (any) mUser.save();
    }

    static String katakana(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) b.append(c >= 'ぁ' && c <= 'ゖ' ? (char) (c + 0x60) : c);
        return b.toString();
    }
}
