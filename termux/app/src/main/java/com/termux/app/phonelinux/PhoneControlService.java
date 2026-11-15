package com.termux.app.phonelinux;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Accessibility service that hosts the local phone-control API while the user has it enabled. */
public class PhoneControlService extends AccessibilityService implements PhoneActions {

    private static final int MAX_NODES = 1500;
    private static final int MAX_TEXT = 200;

    private static volatile PhoneControlService instance;
    private static volatile String serverError;

    private PhoneApiServer server;
    private PhoneConfirmer confirmer;

    /** True while the service is enabled and the API socket is listening. */
    public static boolean isRunning() {
        PhoneControlService s = instance;
        return s != null && s.server != null;
    }

    public static String lastError() {
        return serverError;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        startServer();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        shutdown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private synchronized void startServer() {
        stopServer();
        confirmer = new PhoneConfirmer(this);
        confirmer.register();
        PhoneApiHandler handler = new PhoneApiHandler(PhoneApiConfig.keys(this), this, confirmer,
            PhoneApiServer.fileLog(PhoneApiConfig.LOG_FILE), System::currentTimeMillis);
        PhoneApiServer s = new PhoneApiServer(PhoneApiConfig.SOCKET_FILE, handler);
        try {
            s.start();
            server = s;
            serverError = null;
        } catch (IOException e) {
            serverError = e.getMessage();
            stopServer();
        }
    }

    private synchronized void stopServer() {
        if (server != null) server.stop();
        server = null;
        if (confirmer != null) confirmer.unregister();
        confirmer = null;
    }

    private void shutdown() {
        stopServer();
        if (instance == this) instance = null;
    }

    @Override
    public byte[] screenshotPng() throws ActionException {
        if (Build.VERSION.SDK_INT < 30) throw new ActionException(501, "screenshot needs Android 11 or later");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ScreenshotResult> result = new AtomicReference<>();
        AtomicReference<Integer> failure = new AtomicReference<>();
        takeScreenshot(Display.DEFAULT_DISPLAY, Runnable::run, new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult r) {
                result.set(r);
                latch.countDown();
            }

            @Override
            public void onFailure(int errorCode) {
                failure.set(errorCode);
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new ActionException(504, "screenshot timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ActionException(500, "interrupted");
        }
        ScreenshotResult r = result.get();
        if (r == null) {
            int code = failure.get();
            throw new ActionException(code == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ? 429 : 500, "screenshot failed (code " + code + ")");
        }
        HardwareBuffer hb = r.getHardwareBuffer();
        try {
            Bitmap hw = Bitmap.wrapHardwareBuffer(hb, r.getColorSpace());
            if (hw == null) throw new ActionException(500, "screenshot failed");
            Bitmap bmp = hw.copy(Bitmap.Config.ARGB_8888, false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            return out.toByteArray();
        } finally {
            hb.close();
        }
    }

    @Override
    public String uiTree() throws ActionException {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) throw new ActionException(503, "no active window content");
        try {
            int[] budget = {MAX_NODES};
            JSONObject out = new JSONObject();
            out.put("package", String.valueOf(root.getPackageName()));
            out.put("root", node(root, budget));
            out.put("truncated", budget[0] <= 0);
            return out.toString();
        } catch (JSONException e) {
            throw new ActionException(500, "ui tree failed");
        } finally {
            root.recycle();
        }
    }

    private static JSONObject node(AccessibilityNodeInfo n, int[] budget) throws JSONException {
        budget[0]--;
        JSONObject o = new JSONObject();
        o.put("class", String.valueOf(n.getClassName()));
        if (n.getText() != null) o.put("text", cut(n.getText().toString()));
        if (n.getContentDescription() != null) o.put("contentDescription", cut(n.getContentDescription().toString()));
        if (n.getViewIdResourceName() != null) o.put("viewId", n.getViewIdResourceName());
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        o.put("bounds", new JSONArray().put(b.left).put(b.top).put(b.right).put(b.bottom));
        o.put("clickable", n.isClickable());
        o.put("focused", n.isFocused());
        JSONArray kids = new JSONArray();
        for (int i = 0; i < n.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c == null) continue;
            if (c.isVisibleToUser()) kids.put(node(c, budget));
            c.recycle();
        }
        if (kids.length() > 0) o.put("children", kids);
        return o;
    }

    private static String cut(String s) {
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }

    @Override
    public void tap(int x, int y) throws ActionException {
        Path p = new Path();
        p.moveTo(x, y);
        gesture(p, 50);
    }

    @Override
    public void swipe(int x1, int y1, int x2, int y2, int ms) throws ActionException {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        gesture(p, ms);
    }

    private void gesture(Path path, int ms) throws ActionException {
        if (Build.VERSION.SDK_INT < 24) throw new ActionException(501, "gestures need Android 7 or later");
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] completed = {false};
        GestureDescription g = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, ms)).build();
        boolean accepted = dispatchGesture(g, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription d) {
                completed[0] = true;
                latch.countDown();
            }

            @Override
            public void onCancelled(GestureDescription d) {
                latch.countDown();
            }
        }, new Handler(Looper.getMainLooper()));
        if (!accepted) throw new ActionException(500, "gesture rejected (coordinates off screen?)");
        try {
            latch.await(ms + 3000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!completed[0]) throw new ActionException(500, "gesture cancelled");
    }

    @Override
    public void type(String text, boolean append) throws ActionException {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo field = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        try {
            if (field == null || !field.isEditable()) throw new ActionException(409, "no focused editable field");
            CharSequence old = field.getText();
            String value = append && old != null && !isHint(field) ? old + text : text;
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) throw new ActionException(500, "set text failed");
        } finally {
            if (field != null) field.recycle();
            if (root != null) root.recycle();
        }
    }

    private static boolean isHint(AccessibilityNodeInfo n) {
        return Build.VERSION.SDK_INT >= 26 && n.isShowingHintText();
    }

    @Override
    public void key(String name) throws ActionException {
        int action;
        switch (name) {
            case "back": action = GLOBAL_ACTION_BACK; break;
            case "home": action = GLOBAL_ACTION_HOME; break;
            case "recents": action = GLOBAL_ACTION_RECENTS; break;
            default: action = GLOBAL_ACTION_NOTIFICATIONS;
        }
        if (!performGlobalAction(action)) throw new ActionException(500, "action failed");
    }

    @Override
    public void open(String pkg, String url) throws ActionException {
        Intent i = pkg != null ? getPackageManager().getLaunchIntentForPackage(pkg) : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (i == null) throw new ActionException(404, "no launchable app: " + pkg);
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            throw new ActionException(404, "no app handles this");
        }
    }
}
