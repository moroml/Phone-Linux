package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.TimeZone;

public class ImageInboxTest {

    @Test
    public void baseNameUsesTimestamp() {
        // 2026-11-15 03:04:05 UTC
        long ms = 1794711845000L;
        assertEquals("img-20261115-030405", ImageInbox.baseName(ms, TimeZone.getTimeZone("UTC")));
        assertEquals("img-20261115-120405", ImageInbox.baseName(ms, TimeZone.getTimeZone("Asia/Tokyo")));
    }

    @Test
    public void extensionFromMime() {
        assertEquals("jpg", ImageInbox.extensionForMime("image/jpeg"));
        assertEquals("png", ImageInbox.extensionForMime("image/png"));
        assertEquals("webp", ImageInbox.extensionForMime("IMAGE/WEBP"));
        assertEquals("heic", ImageInbox.extensionForMime("image/heic"));
        assertEquals("jpg", ImageInbox.extensionForMime(null));
        assertEquals("jpg", ImageInbox.extensionForMime("text/plain"));
        assertEquals("jpg", ImageInbox.extensionForMime("image/svg+xml"));
        assertEquals("jpg", ImageInbox.extensionForMime("image/../../x"));
    }

    @Test
    public void isImage() {
        assertTrue(ImageInbox.isImage("image/png"));
        assertFalse(ImageInbox.isImage("text/plain"));
        assertFalse(ImageInbox.isImage(null));
    }

    @Test
    public void selectExpiredByAge() {
        long now = 10 * ImageInbox.MAX_AGE_MS;
        assertEquals(Arrays.asList("old"), ImageInbox.selectExpired(
            Arrays.asList("old", "new", "edge"),
            Arrays.asList(now - ImageInbox.MAX_AGE_MS - 1, now - 1000L, now - ImageInbox.MAX_AGE_MS),
            now, ImageInbox.MAX_AGE_MS));
        assertTrue(ImageInbox.selectExpired(Collections.<String>emptyList(), Collections.<Long>emptyList(), now, ImageInbox.MAX_AGE_MS).isEmpty());
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void createUniqueNeverReturnsTheSameFileTwice() throws Exception {
        Set<String> names = ConcurrentHashMap.newKeySet();
        Thread[] ts = new Thread[8];
        for (int t = 0; t < ts.length; t++) {
            ts[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < 25; i++) names.add(ImageInbox.createUnique(tmp.getRoot(), "img-1", "png").getName());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        assertEquals(200, names.size());
        assertTrue(names.contains("img-1.png"));
        assertTrue(names.contains("img-1-2.png"));
        assertEquals(200, tmp.getRoot().list().length);
    }
}
