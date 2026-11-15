package com.termux.app.phonelinux;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Read-only SKK dictionary: the sorted `reading TAB cand/cand` file (see tools/build_skk_dict.py)
 * kept as raw bytes plus a line offset index, answering lookups by binary search.
 */
public final class SkkDictionary implements KanaDictionary {

    private static final String ASSET = "phone-linux-dict/skk-jisyo-l.tsv.gz";

    private final byte[] mData;
    private final int[] mOffsets;

    public SkkDictionary(File tsv) throws IOException {
        this(readAll(tsv));
    }

    SkkDictionary(byte[] data) {
        mData = data;
        int[] offsets = new int[1024];
        int n = 0;
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != '\n') continue;
            if (i > start) {
                if (n == offsets.length) offsets = Arrays.copyOf(offsets, n * 2);
                offsets[n++] = start;
            }
            start = i + 1;
        }
        if (start < data.length) {
            if (n == offsets.length) offsets = Arrays.copyOf(offsets, n + 1);
            offsets[n++] = start;
        }
        mOffsets = Arrays.copyOf(offsets, n + 1);
        mOffsets[n] = data.length + 1; // sentinel: line n would end at data.length
    }

    /** Extracts the bundled dictionary once per app install/update, then opens it. */
    public static SkkDictionary fromAsset(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), "phone-linux-dict");
        File tsv = new File(dir, "skk.tsv");
        File ver = new File(dir, "skk.ver");
        String version;
        try {
            version = String.valueOf(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).lastUpdateTime);
        } catch (Exception e) {
            version = "0";
        }
        if (!(tsv.isFile() && ver.isFile() && version.equals(new String(readAll(ver), StandardCharsets.UTF_8)))) {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdir " + dir);
            File tmp = new File(dir, "skk.tsv.tmp");
            try (InputStream in = new GZIPInputStream(new BufferedInputStream(context.getAssets().open(ASSET)));
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
            }
            if (!tmp.renameTo(tsv)) throw new IOException("rename " + tmp);
            try (OutputStream out = new FileOutputStream(ver)) {
                out.write(version.getBytes(StandardCharsets.UTF_8));
            }
        }
        return new SkkDictionary(tsv);
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(16, f.length()));
            byte[] buf = new byte[65536];
            for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    private int lineEnd(int i) {
        return mOffsets[i + 1] - 1;
    }

    private String keyAt(int i) {
        int s = mOffsets[i], e = s;
        while (e < mData.length && mData[e] != '\t' && mData[e] != '\n') e++;
        return new String(mData, s, e - s, StandardCharsets.UTF_8);
    }

    @Override
    public List<String> lookup(String key) {
        int lo = 0, hi = mOffsets.length - 2;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = keyAt(mid).compareTo(key);
            if (c < 0) lo = mid + 1;
            else if (c > 0) hi = mid - 1;
            else return candidatesAt(mid);
        }
        return Collections.emptyList();
    }

    private List<String> candidatesAt(int i) {
        int s = mOffsets[i];
        int e = Math.min(lineEnd(i), mData.length);
        while (s < e && mData[s] != '\t') s++;
        String rest = new String(mData, s + 1, Math.max(0, e - s - 1), StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        for (String c : rest.split("/")) if (!c.isEmpty()) out.add(c);
        return out;
    }
}
