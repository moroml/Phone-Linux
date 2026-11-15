package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SkkDictionaryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // Sorted by reading in String order, as tools/build_skk_dict.py writes it.
    private static final String DATA = "かk\t書/欠\nきょう\t今日/京\nてんき\t天気\nわたし\t私/渡し\n";

    @Test
    public void lookupFromFile() throws Exception {
        File f = tmp.newFile("d.tsv");
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(DATA.getBytes(StandardCharsets.UTF_8));
        }
        SkkDictionary d = new SkkDictionary(f);
        assertEquals(Arrays.asList("私", "渡し"), d.lookup("わたし"));
        assertEquals(Arrays.asList("書", "欠"), d.lookup("かk"));
        assertEquals(Arrays.asList("天気"), d.lookup("てんき"));
        assertEquals(Arrays.asList("今日", "京"), d.lookup("きょう"));
    }

    @Test
    public void missesAndEdges() {
        SkkDictionary d = new SkkDictionary(DATA.getBytes(StandardCharsets.UTF_8));
        assertTrue(d.lookup("あ").isEmpty());
        assertTrue(d.lookup("わたしたち").isEmpty());
        assertTrue(d.lookup("ん").isEmpty());
        assertTrue(new SkkDictionary(new byte[0]).lookup("あ").isEmpty());
    }

    @Test
    public void lastLineWithoutNewline() {
        SkkDictionary d = new SkkDictionary("あ\t亜\nい\t伊".getBytes(StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("伊"), d.lookup("い"));
    }
}
