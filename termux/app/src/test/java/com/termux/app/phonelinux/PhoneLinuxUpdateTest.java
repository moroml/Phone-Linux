package com.termux.app.phonelinux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.job.JobInfo;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class PhoneLinuxUpdateTest {

    @Test
    public void lastResultPicksNewestResultLine() {
        String log = "[2026-11-15 03:00:00] START\n[2026-11-15 03:01:00] RESULT failed\n"
            + "[2026-11-16 03:00:00] START\n[2026-11-16 03:02:00] RESULT ok\n[2026-11-16 03:02:01] trailing";
        assertEquals("2026-11-16 03:02:00 ok", UpdateLog.lastResult(log));
    }

    @Test
    public void lastResultEmptyWhenNoResult() {
        assertEquals("", UpdateLog.lastResult("[2026-11-15 03:00:00] START\n"));
        assertEquals("", UpdateLog.lastResult(null));
    }

    @Test
    public void jobRequiresChargingUnmeteredAndPersists() {
        Context context = ApplicationProvider.getApplicationContext();
        JobInfo info = PhoneLinuxUpdateJob.buildJobInfo(context);
        assertTrue(info.isRequireCharging());
        assertTrue(info.isPersisted());
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED, info.getNetworkType());
        assertEquals(TimeUnit.DAYS.toMillis(1), info.getIntervalMillis());
    }
}
