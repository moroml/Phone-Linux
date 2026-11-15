package com.termux.app.phonelinux;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.termux.app.TermuxActivity;
import com.termux.R;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

/** Remote Control launcher and background-command helper shared by the shortcut, drawer button and update job. */
public final class PhoneLinuxLink {

    private PhoneLinuxLink() {}

    /** Called once from TermuxActivity.onCreate: wires the drawer button and (re)schedules the daily update. */
    public static void init(TermuxActivity activity) {
        activity.findViewById(R.id.remote_control_button).setOnClickListener(v -> {
            startRemoteControl(activity);
            activity.getDrawer().closeDrawers();
        });
        PhoneLinuxUpdateJob.schedule(activity);
    }

    /** Opens a new terminal session running claude-rc. */
    public static void startRemoteControl(Context context) {
        Intent intent = executeIntent(context, "claude-rc");
        intent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION,
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY);
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, "Remote Control");
        context.startService(intent);
    }

    /** Runs a $PREFIX/bin script through TermuxService's own background (AppShell) runner. */
    static void runInBackground(Context context, String script, android.app.PendingIntent result) {
        Intent intent = executeIntent(context, script);
        intent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, true);
        intent.putExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT, result);
        // TermuxService calls startForeground itself; a job may start it while it runs.
        androidx.core.content.ContextCompat.startForegroundService(context, intent);
    }

    private static Intent executeIntent(Context context, String script) {
        Uri uri = new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/" + script).build();
        Intent intent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, uri);
        intent.setClassName(context, TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        intent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, TermuxConstants.TERMUX_HOME_DIR_PATH);
        return intent;
    }
}
