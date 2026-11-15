package com.termux.app.phonelinux;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.concurrent.TimeUnit;

/** Daily background run of phone-linux-update while charging on an unmetered network. */
public class PhoneLinuxUpdateJob extends JobService {

    static final String PREFS = "phone_linux_update";
    static final String KEY_ENABLED = "enabled";
    static final String SETUP_DONE = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.phone-linux/setup-done";
    private static final int JOB_ID = 0x504C55; // "PLU"

    @NonNull
    static JobInfo buildJobInfo(Context context) {
        return new JobInfo.Builder(JOB_ID, new ComponentName(context, PhoneLinuxUpdateJob.class))
            .setPeriodic(TimeUnit.DAYS.toMillis(1))
            .setRequiresCharging(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            .setPersisted(true)
            .build();
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        schedule(context);
    }

    /** Schedules or cancels the job to match the setting; keeps an already pending job as is. */
    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        if (!isEnabled(context)) {
            scheduler.cancel(JOB_ID);
        } else if (scheduler.getPendingJob(JOB_ID) == null) {
            scheduler.schedule(buildJobInfo(context));
        }
    }

    /** Runs the update now in the background; the result arrives at PhoneLinuxUpdateResultReceiver. */
    static void runNow(Context context) {
        Intent result = new Intent(context, PhoneLinuxUpdateResultReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, result, PendingIntent.FLAG_UPDATE_CURRENT);
        PhoneLinuxLink.runInBackground(context, "phone-linux-update", pending);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (isEnabled(this) && new File(SETUP_DONE).exists()) runNow(this);
        return false; // the script keeps running in TermuxService; failures are reported by the receiver
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
