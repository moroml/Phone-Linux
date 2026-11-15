package com.termux.app.phonelinux;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Sliding-window hit counter per id. */
public final class PhoneRateLimiter {

    private final int limit;
    private final long windowMs;
    private final Map<String, ArrayDeque<Long>> hits = new HashMap<>();

    public PhoneRateLimiter(int limit, long windowMs) {
        this.limit = limit;
        this.windowMs = windowMs;
    }

    /** True once the window already holds {@code limit} hits. */
    public synchronized boolean isFull(String id, long now) {
        ArrayDeque<Long> q = hits.get(id);
        if (q == null) return false;
        while (!q.isEmpty() && now - q.peekFirst() >= windowMs) q.pollFirst();
        return q.size() >= limit;
    }

    /** Records a hit; false (and nothing recorded) if the window is already full. */
    public synchronized boolean tryAcquire(String id, long now) {
        if (isFull(id, now)) return false;
        ArrayDeque<Long> q = hits.get(id);
        if (q == null) {
            q = new ArrayDeque<>();
            hits.put(id, q);
        }
        q.addLast(now);
        return true;
    }
}
