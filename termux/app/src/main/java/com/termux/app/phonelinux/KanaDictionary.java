package com.termux.app.phonelinux;

import java.util.List;

/** Kana to kanji candidates. Keys follow SKK: okuri-nasi `わたし`, okuri-ari `かk`. */
public interface KanaDictionary {

    /** Candidates in preference order, or an empty list. Never null. */
    List<String> lookup(String key);
}
