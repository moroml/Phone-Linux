package com.termux.app.phonelinux;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

/** Receives the exit code of phone-linux-update and notifies only on failure. */
public class PhoneLinuxUpdateResultReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "phone_linux_update";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle result = intent.getBundleExtra(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE);
        if (result == null || result.getInt(TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, 0) == 0) return;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.phone_linux_update_title), NotificationManager.IMPORTANCE_DEFAULT));
            b = new Notification.Builder(context, CHANNEL_ID);
        } else {
            b = new Notification.Builder(context);
        }
        nm.notify(CHANNEL_ID.hashCode(), b.setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(context.getString(R.string.phone_linux_update_failed))
            .setContentText(context.getString(R.string.phone_linux_update_failed_hint))
            .setAutoCancel(true).build());
    }
}
