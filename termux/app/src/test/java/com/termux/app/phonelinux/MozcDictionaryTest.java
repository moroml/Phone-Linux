package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class MozcDictionaryTest {

    private static final int BLOCK = 4, COST_STEP = 50, N = 3;

    private static class Entry {
        final String surface;
        final int lid, rid, cost;

        Entry(String surface, int lid, int rid, int cost) {
            this.surface = surface;
            this.lid = lid;
            this.rid = rid;
            this.cost = cost;
        }
    }

    private static void u8(ByteArrayOutputStream o, int v) {
        o.write(v);
    }

    private static void u16(ByteArrayOutputStream o, int v) {
        o.write(v);
        o.write(v >> 8);
    }

    private static void u32(ByteArrayOutputStream o, int v) {
        u16(o, v);
        u16(o, v >> 16);
    }

    private static void chars(ByteArrayOutputStream o, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_16LE);
        o.write(b, 0, b.length);
    }

    /** Writes the format of tools/build_mozc_dict.py; the map must be sorted by reading (UTF-16 order). */
    private static ByteBuffer dict(TreeMap<String, List<Entry>> words) {
        List<String> readings = new ArrayList<>(words.keySet());
        List<byte[]> records = new ArrayList<>();
        for (int i = 0; i < readings.size(); i++) {
            String r = readings.get(i);
            int shared = 0;
            if (i % BLOCK != 0) {
                String prev = readings.get(i - 1);
                while (shared < Math.min(prev.length(), r.length()) && prev.charAt(shared) == r.charAt(shared)) shared++;
            }
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            u8(o, shared);
            u8(o, r.length() - shared);
            chars(o, r.substring(shared));
            u8(o, words.get(r).size());
            for (Entry e : words.get(r)) {
                int kind = e.surface.equals(r) ? 1 : e.surface.equals(KanaKanjiConverter.katakana(r)) ? 2 : 0;
                u8(o, e.cost / COST_STEP);
                u16(o, e.lid | kind << 13 | (e.rid == e.lid ? 1 : 0) << 15);
                if (e.rid != e.lid) u16(o, e.rid);
                if (kind == 0) {
                    u8(o, e.surface.length());
                    chars(o, e.surface);
                }
            }
            records.add(o.toByteArray());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {'P', 'L', 'M', 'D'}, 0, 4);
        u32(out, 1);
        u32(out, readings.size());
        u32(out, 7); // unknown id
        u32(out, BLOCK);
        u32(out, COST_STEP);
        out.write(new byte[8], 0, 8);
        int off = 32 + 4 * ((readings.size() + BLOCK - 1) / BLOCK);
        for (int i = 0; i < records.size(); i++) {
            if (i % BLOCK == 0) u32(out, off);
            off += records.get(i).length;
        }
        for (byte[] rec : records) out.write(rec, 0, rec.length);
        return ByteBuffer.wrap(out.toByteArray());
    }

    private static ByteBuffer conn(int width, int step) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(new byte[] {'P', 'L', 'M', 'C'}, 0, 4);
        u32(o, 1);
        u32(o, N);
        u32(o, step);
        u32(o, width);
        o.write(new byte[12], 0, 12);
        for (int i = 0; i < N * N; i++) {
            if (width == 2) u16(o, i * 1000); else u8(o, i);
        }
        return ByteBuffer.wrap(o.toByteArray());
    }

    private static TreeMap<String, List<Entry>> sample() {
        // String's natural order is UTF-16 code unit order, which is what the format needs
        TreeMap<String, List<Entry>> m = new TreeMap<>();
        m.put("あ", list(new Entry("亜", 1, 1, 3000)));
        m.put("あい", list(new Entry("愛", 1, 2, 3500), new Entry("藍", 1, 1, 4000), new Entry("あい", 2, 2, 5000)));
        m.put("あいう", list(new Entry("アイウ", 1, 1, 4500)));
        m.put("かく", list(new Entry("書く", 2, 2, 3000)));
        m.put("かんじ", list(new Entry("漢字", 1, 1, 4000)));
        m.put("かんじょう", list(new Entry("感情", 1, 1, 3000)));
        m.put("きょう", list(new Entry("今日", 1, 1, 3000)));
        m.put("わたし", list(new Entry("私", 1, 1, 3000), new Entry("渡し", 1, 1, 4000)));
        for (int i = 0; i < 20; i++) m.put("ぬ" + (char) ('a' + i), list(new Entry("奴" + i, 1, 1, 3000)));
        return m;
    }

    private static List<Entry> list(Entry... e) {
        List<Entry> l = new ArrayList<>();
        for (Entry x : e) l.add(x);
        return l;
    }

    private static String texts(List<KanaDictionary.Word> ws) {
        StringBuilder b = new StringBuilder();
        for (KanaDictionary.Word w : ws) b.append(w.surface).append('/');
        return b.toString();
    }

    @Test
    public void lookupFindsEveryReadingAcrossBlocks() {
        MozcDictionary d = new MozcDictionary(dict(sample()), conn(2, 1));
        TreeMap<String, List<Entry>> m = sample();
        for (String r : m.keySet()) {
            List<KanaDictionary.Word> ws = d.lookup(r, 0, r.length());
            assertEquals(r, m.get(r).size(), ws.size());
            for (int i = 0; i < ws.size(); i++) assertEquals(m.get(r).get(i).surface, ws.get(i).surface);
        }
    }

    @Test
    public void entryFieldsAndKinds() {
        MozcDictionary d = new MozcDictionary(dict(sample()), conn(2, 1));
        KanaDictionary.Word ai = d.lookup("あい", 0, 2).get(0);
        assertEquals("愛", ai.surface);
        assertEquals(1, ai.lid);
        assertEquals(2, ai.rid);
        assertEquals(3500, ai.cost);
        assertEquals("あい", d.lookup("あい", 0, 2).get(2).surface); // surface == reading
        assertEquals("アイウ", d.lookup("あいう", 0, 3).get(0).surface); // katakana of reading
        assertEquals(7, d.unknownId());
    }

    @Test
    public void lookupUsesTheSubstringOnly() {
        MozcDictionary d = new MozcDictionary(dict(sample()), conn(2, 1));
        String s = "はわたしかんじょう";
        assertEquals("私/渡し/", texts(d.lookup(s, 1, 4)));
        assertEquals("感情/", texts(d.lookup(s, 4, 9)));
        assertEquals("漢字/", texts(d.lookup(s, 4, 7)));
        assertTrue(d.lookup(s, 0, 1).isEmpty());
    }

    @Test
    public void missingReadingsAreEmpty() {
        MozcDictionary d = new MozcDictionary(dict(sample()), conn(2, 1));
        for (String r : new String[] {"", "ああ", "あいうえ", "か", "かんじょ", "ん", "ぬ", "ぬz", "\u0000"}) {
            assertTrue(r, d.lookup(r, 0, r.length()).isEmpty());
        }
    }

    @Test
    public void connectionCostsIndexedByRidThenLid() {
        MozcDictionary wide = new MozcDictionary(dict(sample()), conn(2, 1));
        assertEquals(0, wide.connectionCost(0, 0));
        assertEquals(1000, wide.connectionCost(0, 1));
        assertEquals(3000, wide.connectionCost(1, 0)); // [rid=1][lid=0]
        assertEquals(8000, wide.connectionCost(2, 2));
        MozcDictionary narrow = new MozcDictionary(dict(sample()), conn(1, 64));
        assertEquals(64, narrow.connectionCost(0, 1));
        assertEquals(3 * 64, narrow.connectionCost(1, 0));
        assertEquals(8 * 64, narrow.connectionCost(2, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOtherFiles() {
        new MozcDictionary(ByteBuffer.wrap(new byte[64]).order(ByteOrder.LITTLE_ENDIAN), conn(2, 1));
    }
}
