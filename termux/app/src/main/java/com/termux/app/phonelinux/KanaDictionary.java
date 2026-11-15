package com.termux.app.phonelinux;

import java.util.List;

/** Words by hiragana reading plus the part-of-speech connection costs between them (Mozc's model). */
public interface KanaDictionary {

    /** One dictionary entry: lid/rid are the part-of-speech ids at its left/right edge. */
    final class Word {
        public final String surface;
        public final int lid, rid, cost;

        public Word(String surface, int lid, int rid, int cost) {
            this.surface = surface;
            this.lid = lid;
            this.rid = rid;
            this.cost = cost;
        }
    }

    /** Entries for reading.substring(from, to), cheapest first, or an empty list. Never null. */
    List<Word> lookup(String reading, int from, int to);

    /** Cost of a word ending in {@code rid} followed by one starting with {@code lid}; id 0 is BOS/EOS. */
    int connectionCost(int rid, int lid);

    /** Part-of-speech id given to words that are not in the dictionary. */
    int unknownId();
}
