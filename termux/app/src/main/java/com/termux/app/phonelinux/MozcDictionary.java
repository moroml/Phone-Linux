package com.termux.app.phonelinux;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Read-only Mozc dictionary in the compact format written by tools/build_mozc_dict.py: front-coded
 * readings in blocks found by binary search, and the POS connection matrix. Both files are
 * memory-mapped, so they cost no Java heap.
 */
public final class MozcDictionary implements KanaDictionary {

    // Not ".gz": the Android build would decompress that and drop the extension.
    private static final String DICT_ASSET = "phone-linux-dict/mozc-dict.bin.gzip";
    private static final String CONN_ASSET = "phone-linux-dict/mozc-conn.bin.gzip";
    private static final int HEADER = 32;

    private final ByteBuffer mDict, mConn;
    private final int mReadings, mUnknownId, mBlock, mCostStep, mBlocks;
    private final int mN, mConnStep, mConnWidth;

    MozcDictionary(ByteBuffer dict, ByteBuffer conn) {
        mDict = dict.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        mConn = conn.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (mDict.getInt(0) != 0x444D4C50 || mConn.getInt(0) != 0x434D4C50) throw new IllegalArgumentException("bad magic");
        mReadings = mDict.getInt(8);
        mUnknownId = mDict.getInt(12);
        mBlock = mDict.getInt(16);
        mCostStep = mDict.getInt(20);
        mBlocks = (mReadings + mBlock - 1) / mBlock;
        mN = mConn.getInt(8);
        mConnStep = mConn.getInt(12);
        mConnWidth = mConn.getInt(16);
    }

    public static MozcDictionary open(File dict, File conn) throws IOException {
        return new MozcDictionary(map(dict), map(conn));
    }

    private static ByteBuffer map(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            return raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
        }
    }

    /** Extracts the bundled files once per app install/update, then opens them. */
    public static MozcDictionary fromAsset(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "phone-linux-dict");
        File dict = new File(dir, "mozc-dict.bin");
        File conn = new File(dir, "mozc-conn.bin");
        File ver = new File(dir, "mozc.ver");
        String version;
        try {
            version = String.valueOf(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime);
        } catch (Exception e) {
            version = "0";
        }
        if (!(dict.isFile() && conn.isFile() && ver.isFile() && version.equals(new String(readAll(ver), StandardCharsets.UTF_8)))) {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdir " + dir);
            for (String old : new String[] {"skk.tsv", "skk.tsv.tmp", "skk.ver"}) new File(dir, old).delete();
            ver.delete();
            extract(context, DICT_ASSET, dict);
            extract(context, CONN_ASSET, conn);
            try (OutputStream out = new FileOutputStream(ver)) {
                out.write(version.getBytes(StandardCharsets.UTF_8));
            }
        }
        return open(dict, conn);
    }

    private static void extract(Context context, String asset, File target) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(context.getAssets().open(asset), 65536), 65536);
             OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
        }
        if (!tmp.renameTo(target) && !(target.delete() && tmp.renameTo(target))) throw new IOException("rename " + tmp);
    }

    private static byte[] readAll(File f) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] b = new byte[(int) raf.length()];
            raf.readFully(b);
            return b;
        }
    }

    @Override
    public int unknownId() {
        return mUnknownId;
    }

    @Override
    public int connectionCost(int rid, int lid) {
        if (rid < 0 || lid < 0 || rid >= mN || lid >= mN) return 0;
        int idx = rid * mN + lid;
        return mConnWidth == 2 ? (mConn.getShort(HEADER + 2 * idx) & 0xFFFF) * mConnStep : (mConn.get(HEADER + idx) & 0xFF) * mConnStep;
    }

    @Override
    public List<Word> lookup(String r, int from, int to) {
        int len = to - from;
        if (len <= 0 || len > 255 || mReadings == 0) return Collections.emptyList();
        // last block whose first reading is <= the query
        int lo = 0, hi = mBlocks - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (compare(blockStart(mid), r, from, to) <= 0) lo = mid; else hi = mid - 1;
        }
        char[] cur = new char[256];
        int p = blockStart(lo);
        int count = Math.min(mBlock, mReadings - lo * mBlock);
        for (int k = 0; k < count; k++) {
            int shared = mDict.get(p) & 0xFF, suffix = mDict.get(p + 1) & 0xFF;
            p += 2;
            for (int i = 0; i < suffix; i++, p += 2) cur[shared + i] = mDict.getChar(p);
            int entries = mDict.get(p++) & 0xFF;
            if (shared + suffix == len && equal(cur, r, from, len)) return entries(p, entries, r.substring(from, to));
            p = skipEntries(p, entries);
        }
        return Collections.emptyList();
    }

    private int blockStart(int block) {
        return mDict.getInt(HEADER + 4 * block);
    }

    /** Compares the reading at block-start record p (nothing shared) to r[from, to) in UTF-16 order. */
    private int compare(int p, String r, int from, int to) {
        int suffix = mDict.get(p + 1) & 0xFF;
        p += 2;
        int n = Math.min(suffix, to - from);
        for (int i = 0; i < n; i++) {
            int d = mDict.getChar(p + 2 * i) - r.charAt(from + i);
            if (d != 0) return d;
        }
        return suffix - (to - from);
    }

    private static boolean equal(char[] cur, String r, int from, int len) {
        for (int i = 0; i < len; i++) if (cur[i] != r.charAt(from + i)) return false;
        return true;
    }

    private int skipEntries(int p, int entries) {
        for (int e = 0; e < entries; e++) {
            int f = mDict.getShort(p + 1) & 0xFFFF;
            p += 3;
            if ((f & 0x8000) == 0) p += 2;
            if ((f >> 13 & 3) == 0) p += 1 + 2 * (mDict.get(p) & 0xFF);
        }
        return p;
    }

    private List<Word> entries(int p, int entries, String reading) {
        List<Word> out = new ArrayList<>(entries);
        for (int e = 0; e < entries; e++) {
            int cost = (mDict.get(p) & 0xFF) * mCostStep;
            int f = mDict.getShort(p + 1) & 0xFFFF;
            p += 3;
            int lid = f & 0x1FFF, rid = lid;
            if ((f & 0x8000) == 0) {
                rid = mDict.getShort(p) & 0xFFFF;
                p += 2;
            }
            String surface;
            switch (f >> 13 & 3) {
                case 1:
                    surface = reading;
                    break;
                case 2:
                    surface = KanaKanjiConverter.katakana(reading);
                    break;
                default:
                    int n = mDict.get(p++) & 0xFF;
                    char[] c = new char[n];
                    for (int i = 0; i < n; i++, p += 2) c[i] = mDict.getChar(p);
                    surface = new String(c);
            }
            out.add(new Word(surface, lid, rid, cost));
        }
        return out;
    }
}
