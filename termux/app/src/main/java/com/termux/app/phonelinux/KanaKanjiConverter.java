package com.termux.app.phonelinux;

import com.termux.app.phonelinux.KanaDictionary.Word;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Hiragana to mixed kanji text in bunsetsu units. A Viterbi search over the lattice of dictionary words
 * (Mozc model: word cost + part-of-speech connection cost) gives the best word path, which is grouped into
 * bunsetsu by the words' classes; each bunsetsu then gets its own k-best candidate list.
 */
public final class KanaKanjiConverter {

    static final int MAX_READING = 16;
    /** Higher than any dictionary word, so unknown kana only appear where nothing else fits. */
    private static final int UNKNOWN_COST = 12000;
    /** A learned surface is made this much cheaper than the cheapest word of its reading. */
    private static final int LEARNED_BONUS = 1000;
    private static final int LEARNED_UNKNOWN_COST = 4000;
    /** Distinct surfaces kept per bunsetsu, and a cap on the search effort for them. */
    private static final int KBEST = 30;
    private static final int MAX_POPS = 5000;

    /** One choice for a segment; learn holds reading, surface pairs of its content words. */
    public static final class Candidate {
        public final String text;
        final String[] learn;
        final int lid, rid, cost;

        Candidate(String text, String[] learn, int lid, int rid, int cost) {
            this.text = text;
            this.learn = learn;
            this.lid = lid;
            this.rid = rid;
            this.cost = cost;
        }
    }

    /** reading[start, end) (one bunsetsu) with its candidates and the chosen one. */
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
        final boolean unknown;
        Node prev;
        long fwd;

        Node(int start, int end, Word word, boolean unknown) {
            this.start = start;
            this.end = end;
            this.word = word;
            this.unknown = unknown;
        }
    }

    /** A partial path read backwards from the end of the span; f is its exact total cost bound. */
    private static final class State {
        final Node node;
        final State next;
        final long suffix, f;

        State(Node node, State next, long suffix) {
            this.node = node;
            this.next = next;
            this.suffix = suffix;
            this.f = node.fwd + suffix;
        }
    }

    /** Words of reading[from, to) with their best forward costs, built once and searched both ways. */
    private final class Lattice {
        final String r;
        final int from, to;
        final List<List<Node>> ends = new ArrayList<>();
        final List<Map<Integer, Node>> best = new ArrayList<>(); // per end: cheapest node by right-edge id
        final Node bos;

        /**
         * @param boundary if positive, no word crosses it (nodes starting before it end by it)
         * @param leftRid right-edge POS id of what precedes {@code from} (0 at the start of the text)
         */
        Lattice(String r, int from, int to, int boundary, int leftRid) {
            this.r = r;
            this.from = from;
            this.to = to;
            for (int i = 0; i <= to; i++) {
                ends.add(new ArrayList<Node>());
                best.add(new HashMap<Integer, Node>());
            }
            bos = new Node(from, from, new Word("", 0, leftRid, 0), false);
            best.get(from).put(leftRid, bos);
            for (int i = from; i < to; i++) {
                if (best.get(i).isEmpty()) continue;
                for (Node node : nodesAt(i, boundary > i ? boundary : to)) {
                    Node prev = null;
                    long bestTotal = Long.MAX_VALUE;
                    for (Node p : best.get(i).values()) {
                        long t = p.fwd + mDict.connectionCost(p.word.rid, node.word.lid);
                        if (t < bestTotal) {
                            bestTotal = t;
                            prev = p;
                        }
                    }
                    node.prev = prev;
                    node.fwd = bestTotal + node.word.cost;
                    ends.get(node.end).add(node);
                    Node old = best.get(node.end).get(node.word.rid);
                    if (old == null || node.fwd < old.fwd) best.get(node.end).put(node.word.rid, node);
                }
            }
        }

        /** Words that start at i and end by limit, with the unknown-kana fallback (so a path always exists). */
        private List<Node> nodesAt(int i, int limit) {
            List<Node> out = new ArrayList<>();
            for (int j = i + 1; j <= Math.min(limit, i + MAX_READING); j++) {
                List<Word> words = words(r, i, j);
                for (Word w : words) out.add(new Node(i, j, w, false));
                if (words.isEmpty() && j == i + 1) out.add(new Node(i, j, unknown(r.substring(i, j)), true));
            }
            return out;
        }

        /** The cheapest path over the whole span. */
        List<Node> bestPath(int rightLid) {
            Node last = null;
            long lastTotal = Long.MAX_VALUE;
            for (Node p : best.get(to).values()) {
                long t = p.fwd + mDict.connectionCost(p.word.rid, rightLid);
                if (t < lastTotal) {
                    lastTotal = t;
                    last = p;
                }
            }
            List<Node> path = new ArrayList<>();
            for (Node p = last; p != null && p != bos; p = p.prev) path.add(p);
            Collections.reverse(path);
            return path;
        }

        /** Exact k-best by surface: backward A* from the end, with the forward costs as the heuristic. */
        List<Candidate> kBest(int rightLid, int k) {
            PriorityQueue<State> queue = new PriorityQueue<>(64, (a, b) -> Long.compare(a.f, b.f));
            for (Node n : ends.get(to)) queue.add(new State(n, null, mDict.connectionCost(n.word.rid, rightLid)));
            Map<String, Candidate> out = new LinkedHashMap<>();
            for (int pops = 0; !queue.isEmpty() && pops < MAX_POPS && out.size() < k; pops++) {
                State s = queue.poll();
                if (s.node.start == from) {
                    List<Node> nodes = new ArrayList<>();
                    for (State t = s; t != null; t = t.next) nodes.add(t.node);
                    Candidate c = candidate(r, nodes, (int) Math.min(Integer.MAX_VALUE, s.f));
                    if (!out.containsKey(c.text)) out.put(c.text, c);
                    continue;
                }
                for (Node p : ends.get(s.node.start)) {
                    queue.add(new State(p, s, mDict.connectionCost(p.word.rid, s.node.word.lid) + s.node.word.cost + s.suffix));
                }
            }
            return new ArrayList<>(out.values());
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
     * Best bunsetsu segmentation of reading[from..].
     *
     * @param forcedEnd if positive, the first segment must be exactly reading[from, forcedEnd)
     * @param prevRid right-edge POS id of the segment before {@code from} (0 at the start of the text)
     */
    List<Segment> segment(String r, int from, int forcedEnd, int prevRid) {
        List<Node> path = new Lattice(r, from, r.length(), forcedEnd, prevRid).bestPath(0);
        List<int[]> groups = group(path, forcedEnd);
        List<Segment> out = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++) {
            int[] span = groups.get(g);
            int left = g == 0 ? prevRid : path.get(span[0] - 1).word.rid;
            int right = g + 1 < groups.size() ? path.get(span[1]).word.lid : 0;
            out.add(build(r, path.subList(span[0], span[1]), left, right));
        }
        return out;
    }

    /**
     * Bunsetsu as index ranges [from, to) over the path. A word starts a new one unless it is a suffix or
     * particle (joins the word before), follows a prefix, continues a noun compound, or is する after a noun.
     * Runs of unknown kana stay together.
     */
    private List<int[]> group(List<Node> path, int forcedEnd) {
        List<int[]> out = new ArrayList<>();
        int start = 0;
        boolean nounOnly = false; // the bunsetsu so far is nouns (and prefixes), so another noun extends it
        for (int k = 0; k < path.size(); k++) {
            Node n = path.get(k);
            int cls = classOf(n);
            boolean join = false;
            if (k > 0) {
                Node p = path.get(k - 1);
                int pc = classOf(p);
                if (forcedEnd > 0 && n.start <= forcedEnd) {
                    join = n.start < forcedEnd; // the forced first bunsetsu, then a break
                } else {
                    join = pc == KanaDictionary.ATTACH_NEXT || cls == KanaDictionary.ATTACH_PREV
                            || (cls == KanaDictionary.NOUN && nounOnly)
                            || (cls == KanaDictionary.SURU && pc == KanaDictionary.NOUN)
                            || (n.unknown && p.unknown);
                }
            }
            if (!join && k > 0) {
                out.add(new int[] {start, k});
                start = k;
            }
            boolean nounLike = cls == KanaDictionary.NOUN || cls == KanaDictionary.ATTACH_NEXT;
            nounOnly = join ? nounOnly && nounLike : nounLike;
        }
        if (!path.isEmpty()) out.add(new int[] {start, path.size()});
        return out;
    }

    private int classOf(Node n) {
        return n.unknown ? KanaDictionary.CONTENT : mDict.posClass(n.word.lid);
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

    /** A candidate made of consecutive path nodes; only its content words are kept for learning. */
    private Candidate candidate(String r, List<Node> nodes, int cost) {
        StringBuilder text = new StringBuilder();
        List<String> learn = new ArrayList<>();
        for (Node n : nodes) {
            text.append(n.word.surface);
            int cls = classOf(n);
            if (!n.unknown && cls != KanaDictionary.ATTACH_PREV && cls != KanaDictionary.ATTACH_NEXT) {
                learn.add(r.substring(n.start, n.end));
                learn.add(n.word.surface);
            }
        }
        return new Candidate(text.toString(), learn.toArray(new String[0]), nodes.get(0).word.lid,
                nodes.get(nodes.size() - 1).word.rid, cost);
    }

    /** Candidates of one bunsetsu: the Viterbi choice, then k-best for the span in its context, then kana. */
    private Segment build(String r, List<Node> nodes, int leftRid, int rightLid) {
        int start = nodes.get(0).start, end = nodes.get(nodes.size() - 1).end;
        String hira = r.substring(start, end);
        Map<String, Candidate> out = new LinkedHashMap<>();
        Candidate chosen = candidate(r, nodes, 0);
        out.put(chosen.text, chosen);
        for (Candidate c : new Lattice(r, start, end, 0, leftRid).kBest(rightLid, KBEST)) {
            if (!out.containsKey(c.text)) out.put(c.text, c);
        }
        int u = mDict.unknownId();
        for (String kana : new String[] {hira, katakana(hira)}) {
            if (!out.containsKey(kana)) out.put(kana, new Candidate(kana, new String[] {hira, kana}, u, u, UNKNOWN_COST));
        }
        return new Segment(start, end, new ArrayList<>(out.values()));
    }

    /** Moves the words of every chosen non-default candidate to the front for next time. */
    void learn(List<Segment> segments) {
        boolean any = false;
        for (Segment s : segments) {
            if (s.index == 0) continue;
            String[] pairs = s.chosen().learn;
            for (int i = 0; i + 1 < pairs.length; i += 2) mUser.promote(pairs[i], pairs[i + 1]);
            any |= pairs.length > 0;
        }
        if (any) mUser.save();
    }

    static String katakana(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) b.append(c >= 'ぁ' && c <= 'ゖ' ? (char) (c + 0x60) : c);
        return b.toString();
    }
}
