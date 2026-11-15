package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Properties;

public class AppearanceTest {

    @Test
    public void settingsRoundTrip() throws IOException {
        File f = File.createTempFile("appearance", ".properties");
        f.deleteOnExit();
        AppearanceSettings s = new AppearanceSettings();
        s.bgMode = AppearanceSettings.MODE_IMAGE;
        s.bgColor = 0xFF123456;
        s.dim = 35;
        s.bgExt = "png";
        s.preset = ColorPresets.TEAL;
        s.bundledFont = false;
        s.save(f);

        AppearanceSettings r = AppearanceSettings.load(f);
        assertEquals(AppearanceSettings.MODE_IMAGE, r.bgMode);
        assertEquals(0xFF123456, r.bgColor);
        assertEquals(35, r.dim);
        assertEquals("png", r.bgExt);
        assertEquals(ColorPresets.TEAL, r.preset);
        assertFalse(r.bundledFont);
    }

    @Test
    public void missingOrInvalidValuesFallBackToDefaults() {
        AppearanceSettings d = AppearanceSettings.load(new File("does-not-exist.properties"));
        assertEquals(AppearanceSettings.MODE_NONE, d.bgMode);
        assertTrue(d.bundledFont);
        assertEquals(ColorPresets.DEFAULT, d.preset);

        Properties p = new Properties();
        p.setProperty("bg_mode", "bogus");
        p.setProperty("bg_dim", "999");
        p.setProperty("bg_ext", "../x");
        p.setProperty("color_preset", "nope");
        p.setProperty("bg_color", "zzz");
        AppearanceSettings s = AppearanceSettings.fromProperties(p);
        assertEquals(AppearanceSettings.MODE_NONE, s.bgMode);
        assertEquals(AppearanceSettings.DIM_MAX, s.dim);
        assertEquals("jpg", s.bgExt);
        assertEquals(ColorPresets.DEFAULT, s.preset);
        assertEquals(0xFF000000, s.bgColor);
    }

    @Test
    public void colorParsingAndMime() {
        assertEquals(Integer.valueOf(0xFF33D6D6), AppearanceSettings.parseColor("#33d6d6"));
        assertEquals(Integer.valueOf(0xFF33D6D6), AppearanceSettings.parseColor(" 33D6D6 "));
        assertNull(AppearanceSettings.parseColor("#33d6"));
        assertNull(AppearanceSettings.parseColor(null));
        assertEquals("#33d6d6", AppearanceSettings.formatColor(0xFF33D6D6));
        assertEquals("png", AppearanceSettings.extForMime("image/PNG"));
        assertEquals("webp", AppearanceSettings.extForMime("image/webp"));
        assertEquals("jpg", AppearanceSettings.extForMime("image/heic"));
        assertEquals(0, AppearanceSettings.overlayAlpha(-5));
        assertEquals(153, AppearanceSettings.overlayAlpha(60));
        assertEquals(AppearanceSettings.overlayAlpha(90), AppearanceSettings.overlayAlpha(500));
    }

    @Test
    public void presetsGenerateValidColorProperties() throws IOException {
        assertNull(ColorPresets.generate(ColorPresets.DEFAULT));
        for (String name : new String[] {ColorPresets.TEAL, ColorPresets.LIGHT}) {
            String text = ColorPresets.generate(name);
            assertTrue(ColorPresets.isOurs(text));
            Properties p = new Properties();
            p.load(new java.io.StringReader(text));
            assertEquals(19, p.size());
            for (String key : p.stringPropertyNames()) assertNotNull(key, AppearanceSettings.parseColor(p.getProperty(key)));
        }
        Properties teal = new Properties();
        teal.load(new java.io.StringReader(ColorPresets.generate(ColorPresets.TEAL)));
        assertEquals("#33d6d6", teal.getProperty("foreground"));
        assertFalse(ColorPresets.isOurs("foreground=#ffffff\n"));
    }

    @Test
    public void fontPolicyNeverTouchesUserFont() {
        String b = FontPolicy.BUNDLED_SHA256;
        assertEquals(FontPolicy.Action.INSTALL, FontPolicy.decide(true, null, null));
        assertEquals(FontPolicy.Action.NONE, FontPolicy.decide(false, null, null));
        assertEquals(FontPolicy.Action.NONE, FontPolicy.decide(true, "user", null));
        assertEquals(FontPolicy.Action.NONE, FontPolicy.decide(false, "user", b));
        assertEquals(FontPolicy.Action.NONE, FontPolicy.decide(true, b, b));
        assertEquals(FontPolicy.Action.REMOVE, FontPolicy.decide(false, b, b));
        assertEquals(FontPolicy.Action.INSTALL, FontPolicy.decide(true, "old", "old"));
        assertEquals(FontPolicy.Action.REMOVE, FontPolicy.decide(false, "old", "old"));
    }

    @Test
    public void bundledFontHashMatchesConstant() throws Exception {
        File font = new File("src/main/assets/phone-linux-fonts/UDEVGothic-Regular.ttf");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(font)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        assertEquals(FontPolicy.BUNDLED_SHA256, sb.toString());
        assertTrue(Files.size(font.toPath()) > 1_000_000);
    }
}
