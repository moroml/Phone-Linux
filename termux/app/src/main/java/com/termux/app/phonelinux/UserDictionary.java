package com.termux.app.phonelinux;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Surfaces the user picked before, kept in `reading TAB cand/cand` lines, newest reading last. */
public final class UserDictionary {

    static final int MAX_READINGS = 2000;
    private static final int MAX_CANDIDATES = 12;

    private final File mFile;
    private final LinkedHashMap<String, List<String>> mMap = new LinkedHashMap<>();

    /** @param file backing file, or null for a memory-only dictionary */
    public UserDictionary(File file) {
        mFile = file;
        if (file == null || !file.isFile()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                List<String> cands = new ArrayList<>();
                for (String c : line.substring(tab + 1).split("/")) if (!c.isEmpty()) cands.add(c);
                if (!cands.isEmpty()) mMap.put(line.substring(0, tab), cands);
            }
        } catch (IOException ignored) {
        }
        trim();
    }

    public synchronized List<String> lookup(String key) {
        List<String> c = mMap.get(key);
        return c == null ? new ArrayList<String>() : new ArrayList<>(c);
    }

    /** Makes {@code candidate} the first choice for {@code key}. */
    public synchronized void promote(String key, String candidate) {
        List<String> cands = mMap.remove(key);
        if (cands == null) cands = new ArrayList<>();
        cands.remove(candidate);
        cands.add(0, candidate);
        while (cands.size() > MAX_CANDIDATES) cands.remove(cands.size() - 1);
        mMap.put(key, cands);
        trim();
    }

    private void trim() {
        Iterator<Map.Entry<String, List<String>>> it = mMap.entrySet().iterator();
        while (mMap.size() > MAX_READINGS && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    public synchronized void save() {
        if (mFile == null) return;
        File dir = mFile.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
        File tmp = new File(mFile.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<String>> e : mMap.entrySet()) {
                StringBuilder b = new StringBuilder(e.getKey()).append('\t');
                for (String c : e.getValue()) b.append(c).append('/');
                b.setLength(b.length() - 1);
                w.write(b.append('\n').toString());
            }
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(mFile)) {
            mFile.delete();
            tmp.renameTo(mFile);
        }
    }
}
