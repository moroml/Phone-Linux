package com.termux.app.phonelinux;

import com.termux.app.phonelinux.KanaKanjiConverter.Segment;

import java.util.ArrayList;
import java.util.List;

/** State of one conversion in progress: segments, which one has focus and which candidate each shows. */
public final class Conversion {

    public static final int PAGE = 9;

    private final KanaKanjiConverter mConverter;
    private final String mReading;
    private final List<Segment> mSegments;
    private int mFocus;

    Conversion(KanaKanjiConverter converter, String reading, List<Segment> segments) {
        mConverter = converter;
        mReading = reading;
        mSegments = new ArrayList<>(segments);
    }

    public String text() {
        StringBuilder b = new StringBuilder();
        for (Segment s : mSegments) b.append(s.text());
        return b.toString();
    }

    public int segmentCount() {
        return mSegments.size();
    }

    public int focus() {
        return mFocus;
    }

    /** Reading kana covered by the focused segment. */
    public String focusReading() {
        Segment s = seg();
        return mReading.substring(s.start, s.end);
    }

    /** Start and end char offsets of the focused segment inside {@link #text()}. */
    public int[] focusRange() {
        int start = 0;
        for (int i = 0; i < mFocus; i++) start += mSegments.get(i).text().length();
        return new int[] {start, start + seg().text().length()};
    }

    private Segment seg() {
        return mSegments.get(mFocus);
    }

    /** Candidates of the page that holds the chosen one of the focused segment. */
    public List<String> pageCandidates() {
        Segment s = seg();
        int from = s.index / PAGE * PAGE;
        List<String> out = new ArrayList<>();
        for (int i = from; i < Math.min(s.candidates.size(), from + PAGE); i++) out.add(s.candidates.get(i).text);
        return out;
    }

    /** Position of the chosen candidate inside {@link #pageCandidates()}. */
    public int pageIndex() {
        return seg().index % PAGE;
    }

    public void next() {
        Segment s = seg();
        s.index = (s.index + 1) % s.candidates.size();
    }

    public void prev() {
        Segment s = seg();
        s.index = (s.index + s.candidates.size() - 1) % s.candidates.size();
    }

    /** Picks the n-th (1-based) candidate of the visible page and moves on to the next segment. */
    public boolean select(int n) {
        Segment s = seg();
        int i = s.index / PAGE * PAGE + n - 1;
        if (n < 1 || i >= s.candidates.size()) return false;
        s.index = i;
        moveFocus(1);
        return true;
    }

    public void moveFocus(int d) {
        mFocus = Math.max(0, Math.min(mSegments.size() - 1, mFocus + d));
    }

    /** Shrinks (d < 0) or extends (d > 0) the focused segment by kana and re-segments what follows. */
    public boolean resize(int d) {
        Segment s = seg();
        int end = s.end + d;
        if (end <= s.start || end > mReading.length()) return false;
        List<Segment> tail = new ArrayList<>();
        tail.add(mConverter.span(mReading, s.start, end));
        tail.addAll(mConverter.segment(mReading, end));
        while (mSegments.size() > mFocus) mSegments.remove(mSegments.size() - 1);
        mSegments.addAll(tail);
        return true;
    }

    /** Records the user's choices for learning and returns the converted text. */
    public String commit() {
        mConverter.learn(mSegments);
        return text();
    }
}
