package com.termux.app.phonelinux;

import java.util.List;

/** Words by hiragana reading plus the part-of-speech connection costs between them (Mozc's model). */
public interface KanaDictionary {

    /** Bunsetsu classes of a part-of-speech id (see posClass). */
    int CONTENT = 0, ATTACH_PREV = 1, ATTACH_NEXT = 2, NOUN = 3, SURU = 4;

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

    /**
     * How words with this part-of-speech id group into bunsetsu: CONTENT starts one, ATTACH_PREV (particles,
     * auxiliaries, suffixes) joins the word before it, ATTACH_NEXT (prefixes) joins the word after it, NOUN
     * starts one unless it continues a noun compound, SURU (する) joins a noun before it. BOS/EOS/unknown are CONTENT.
     */
    int posClass(int id);
}
