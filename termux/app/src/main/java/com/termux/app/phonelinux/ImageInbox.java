package com.termux.app.phonelinux;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Saves images under $HOME/inbox and types their paths into the current terminal session, so Claude Code can read them. */
public final class ImageInbox {

    private static final String LOG_TAG = "ImageInbox";
    public static final String INBOX_DIR_PATH = TermuxConstants.TERMUX_HOME_DIR_PATH + "/inbox";
    static final long MAX_BYTES = 20L * 1024 * 1024;
    static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int REQUEST_PICK_IMAGE = 0x504C;
    private static final int PENDING_RETRY_MS = 250;
    private static final int PENDING_RETRY_MAX = 40;

    private static Handler sMain;
    /** Text waiting for a session to exist (a share arrived before the activity/session was ready). */
    private static String sPending;

    private ImageInbox() {}

    // ---- entry points ----

    public static void pickImage(@NonNull TermuxActivity activity) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        }
        try {
            activity.startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (Exception e) {
            toast(activity, activity.getString(R.string.phone_linux_image_failed, e.getMessage()));
        }
    }

    public static void pasteClipboardImage(@NonNull TermuxActivity activity) {
        Uri uri = null;
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount() && uri == null; i++) {
                Uri u = clip.getItemAt(i).getUri();
                if (u != null && isImage(activity.getContentResolver().getType(u))) uri = u;
            }
        }
        if (uri == null) {
            toast(activity, activity.getString(R.string.phone_linux_no_clipboard_image));
            return;
        }
        importAndInsertAsync(activity, activity, Collections.singletonList(uri));
    }

    /** Copies the image to inbox and returns its absolute path. Throws IOException (message is user-facing). */
    public static String importUri(@NonNull Context ctx, @NonNull Uri uri) throws IOException {
        File dir = new File(INBOX_DIR_PATH);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("inbox を作れません: " + dir);
        String mime = ctx.getContentResolver().getType(uri);
        File out = uniqueFile(dir, baseName(System.currentTimeMillis(), TimeZone.getDefault()), extensionForMime(mime));
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("画像を開けません");
            long total = 0;
            byte[] buf = new byte[16384];
            try (FileOutputStream fos = new FileOutputStream(out)) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("画像が大きすぎます(上限 20 MB)");
                    fos.write(buf, 0, n);
                }
            }
        } catch (IOException | RuntimeException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("画像を読めません: " + e.getMessage());
        }
        return out.getAbsolutePath();
    }

    /**
     * Imports each uri on a worker thread, then types the paths (each followed by a space) into the current
     * session. With a null activity (share target) the text is queued until a TermuxActivity has a session.
     */
    public static void importAndInsertAsync(@NonNull Context ctx, @Nullable TermuxActivity activity, @NonNull List<Uri> uris) {
        importAndInsertAsync(ctx, activity, uris, null);
    }

    /** As above; onDone runs on the main thread after the import (the share receiver finishes there, keeping the URI grants alive). */
    public static void importAndInsertAsync(@NonNull Context ctx, @Nullable TermuxActivity activity, @NonNull List<Uri> uris, @Nullable Runnable onDone) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            String error = null;
            for (Uri uri : uris) {
                try {
                    sb.append(importUri(app, uri)).append(' ');
                } catch (IOException e) {
                    error = e.getMessage();
                    Logger.logStackTraceWithMessage(LOG_TAG, "import failed: " + uri, e);
                }
            }
            final String text = sb.toString(), err = error;
            main().post(() -> {
                if (err != null) toast(app, app.getString(R.string.phone_linux_image_failed, err));
                if (!text.isEmpty()) {
                    TerminalSession session = activity == null ? null : activity.getCurrentSession();
                    if (session != null) session.write(text);
                    else sPending = sPending == null ? text : sPending + text;
                    if (activity == null) startTermuxActivity(app);
                }
                if (onDone != null) onDone.run();
            });
        }, "ImageInbox-import").start();
    }

    private static void startTermuxActivity(Context app) {
        Intent i = new Intent(app, TermuxActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            app.startActivity(i);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "cannot start TermuxActivity", e);
        }
    }

    /** Called from TermuxActivity.onResume: delivers paths that arrived via share while no session was ready. */
    public static void flushPending(@NonNull TermuxActivity activity) {
        flushPending(activity, 0);
    }

    private static void flushPending(TermuxActivity activity, int attempt) {
        if (sPending == null || activity.isFinishing()) return;
        TerminalSession session = activity.getCurrentSession();
        if (session != null) {
            String text = sPending;
            sPending = null;
            session.write(text);
        } else if (attempt < PENDING_RETRY_MAX) {
            main().postDelayed(() -> flushPending(activity, attempt + 1), PENDING_RETRY_MS);
        }
    }

    /** Called from TermuxActivity.onActivityResult. Returns true if the result was ours. */
    public static boolean onActivityResult(@NonNull TermuxActivity activity, int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != REQUEST_PICK_IMAGE) return false;
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null)
            importAndInsertAsync(activity, activity, Collections.singletonList(data.getData()));
        return true;
    }

    // ---- cleanup ----

    /** Deletes inbox files older than 7 days (background thread). */
    public static void cleanOldAsync() {
        new Thread(() -> {
            File[] files = new File(INBOX_DIR_PATH).listFiles();
            if (files == null) return;
            List<String> names = new ArrayList<>();
            List<Long> times = new ArrayList<>();
            for (File f : files) {
                if (!f.isFile()) continue;
                names.add(f.getName());
                times.add(f.lastModified());
            }
            for (String name : selectExpired(names, times, System.currentTimeMillis(), MAX_AGE_MS)) {
                //noinspection ResultOfMethodCallIgnored
                new File(INBOX_DIR_PATH, name).delete();
            }
        }, "ImageInbox-clean").start();
    }

    // ---- pure logic (unit tested) ----

    static String baseName(long millis, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        f.setTimeZone(tz);
        return "img-" + f.format(new Date(millis));
    }

    static String extensionForMime(@Nullable String mime) {
        if (mime == null) return "jpg";
        String m = mime.toLowerCase(Locale.US).trim();
        if (m.equals("image/jpeg") || m.equals("image/jpg") || !m.startsWith("image/")) return "jpg";
        String sub = m.substring(6);
        int plus = sub.indexOf('+');
        if (plus >= 0) sub = sub.substring(0, plus);
        if (sub.equals("svg") || !sub.matches("[a-z0-9]{1,8}")) return "jpg";
        return sub;
    }

    static boolean isImage(@Nullable String mime) {
        return mime != null && mime.toLowerCase(Locale.US).startsWith("image/");
    }

    /** Names whose lastModified is older than maxAgeMs relative to now. */
    static List<String> selectExpired(List<String> names, List<Long> lastModified, long now, long maxAgeMs) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (now - lastModified.get(i) > maxAgeMs) out.add(names.get(i));
        }
        return out;
    }

    private static File uniqueFile(File dir, String base, String ext) {
        File f = new File(dir, base + "." + ext);
        for (int i = 2; f.exists(); i++) f = new File(dir, base + "-" + i + "." + ext);
        return f;
    }

    private static Handler main() {
        if (sMain == null) sMain = new Handler(Looper.getMainLooper());
        return sMain;
    }

    private static void toast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }
}
