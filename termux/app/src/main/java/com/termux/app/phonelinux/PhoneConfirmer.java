package com.termux.app.phonelinux;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import com.termux.R;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Asks the user via a high-priority notification (Allow / Deny) before a confirm-flagged key acts. */
final class PhoneConfirmer implements PhoneApiHandler.Confirmer {

    private static final String ACTION = "com.termux.app.phonelinux.CONFIRM";
    private static final String CHANNEL = "phone_linux_confirm";
    static final long TIMEOUT_SECONDS = 30;

    private static final class Pending {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean allowed = new AtomicBoolean();
    }

    private final Context context;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            Pending p = pending.get(intent.getStringExtra("id"));
            if (p == null) return;
            p.allowed.set(intent.getBooleanExtra("allow", false));
            p.latch.countDown();
        }
    };

    PhoneConfirmer(Context context) {
        this.context = context.getApplicationContext();
    }

    void register() {
        IntentFilter filter = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, filter);
    }

    void unregister() {
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
        for (Pending p : pending.values()) p.latch.countDown();
    }

    @Override
    public boolean confirm(String keyName, String description) {
        String id = UUID.randomUUID().toString();
        Pending p = new Pending();
        pending.put(id, p);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        int notifId = id.hashCode();
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, context.getString(R.string.phone_linux_confirm_channel), NotificationManager.IMPORTANCE_HIGH));
                b = new Notification.Builder(context, CHANNEL).setTimeoutAfter(TIMEOUT_SECONDS * 1000);
            } else {
                b = new Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
            }
            b.setSmallIcon(R.drawable.ic_service_notification)
                .setContentTitle(context.getString(R.string.phone_linux_confirm_title, keyName))
                .setContentText(description)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.phone_linux_confirm_allow), action(id, true, notifId))
                .addAction(0, context.getString(R.string.phone_linux_confirm_deny), action(id, false, notifId + 1));
            nm.notify(notifId, b.build());
            return p.latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) && p.allowed.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            pending.remove(id);
            nm.cancel(notifId);
        }
    }

    private PendingIntent action(String id, boolean allow, int requestCode) {
        Intent i = new Intent(ACTION).setPackage(context.getPackageName()).putExtra("id", id).putExtra("allow", allow);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(context, requestCode, i, flags);
    }
}
