package com.termux.app.phonelinux;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Sets the window background behind the terminal. The terminal view only paints cells whose colour
 * differs from the default background, so a window background image shows through the default one.
 */
final class PhoneLinuxBackground {

    private static Bitmap sBitmap;
    private static String sKey;
    private static String sLoadingKey;

    private PhoneLinuxBackground() {}

    /** @param terminalBg the terminal's current default background colour (ARGB) */
    static void apply(Activity activity, int terminalBg) {
        View decor = activity.getWindow().getDecorView();
        AppearanceSettings s = AppearanceSettings.load();

        if (AppearanceSettings.MODE_COLOR.equals(s.bgMode)) {
            decor.setBackgroundColor(s.bgColor);
            return;
        }
        File file = s.imageFile();
        if (!AppearanceSettings.MODE_IMAGE.equals(s.bgMode) || !file.isFile()) {
            decor.setBackgroundColor(terminalBg);
            return;
        }

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int minSide = Math.min(dm.widthPixels, dm.heightPixels);
        String key = file.getPath() + ":" + file.lastModified() + ":" + minSide;
        Bitmap bitmap;
        synchronized (PhoneLinuxBackground.class) {
            bitmap = key.equals(sKey) ? sBitmap : null;
        }
        if (bitmap == null) {
            decor.setBackgroundColor(terminalBg);
            loadAsync(activity, file, key, minSide, terminalBg);
            return;
        }
        int overlay = (AppearanceSettings.overlayAlpha(s.dim) << 24) | (terminalBg & 0xFFFFFF);
        decor.setBackground(new CoverDrawable(bitmap, overlay));
    }

    private static void loadAsync(Activity activity, File file, String key, int minSide, int terminalBg) {
        synchronized (PhoneLinuxBackground.class) {
            if (key.equals(sLoadingKey)) return;
            sLoadingKey = key;
        }
        new Thread(() -> {
            Bitmap decoded = decode(file, minSide);
            synchronized (PhoneLinuxBackground.class) {
                sLoadingKey = null;
                if (decoded == null) return;
                // The previous bitmap is only dropped, not recycled: the window may still be drawing it.
                sBitmap = decoded;
                sKey = key;
            }
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing()) apply(activity, terminalBg);
            });
        }, "phone-linux-background").start();
    }

    /** Decodes so that the short side is at least {@code minSide} pixels but memory stays small. */
    private static Bitmap decode(File file, int minSide) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int shortSide = Math.min(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (shortSide / (sample * 2) >= minSide) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getPath(), opts);
    }

    /** Draws a bitmap centre-cropped to the bounds, then a translucent colour over it. */
    private static final class CoverDrawable extends Drawable {
        private final Bitmap mBitmap;
        private final int mOverlay;
        private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Matrix mMatrix = new Matrix();

        CoverDrawable(Bitmap bitmap, int overlay) {
            mBitmap = bitmap;
            mOverlay = overlay;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float scale = Math.max(b.width() / (float) mBitmap.getWidth(), b.height() / (float) mBitmap.getHeight());
            mMatrix.setScale(scale, scale);
            mMatrix.postTranslate(b.left + (b.width() - mBitmap.getWidth() * scale) / 2f,
                b.top + (b.height() - mBitmap.getHeight() * scale) / 2f);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawBitmap(mBitmap, mMatrix, mPaint);
            canvas.drawColor(mOverlay);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
