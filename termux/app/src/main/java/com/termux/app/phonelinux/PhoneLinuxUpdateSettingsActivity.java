package com.termux.app.phonelinux;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Automatic update on/off, "update now" and the last run result. */
public class PhoneLinuxUpdateSettingsActivity extends Activity {

    private static final File LOG = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.phone-linux/update.log");
    private TextView mLast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.phone_linux_update_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        CheckBox enabled = new CheckBox(this);
        enabled.setText(R.string.phone_linux_update_enabled);
        enabled.setChecked(PhoneLinuxUpdateJob.isEnabled(this));
        enabled.setOnCheckedChangeListener((b, on) -> PhoneLinuxUpdateJob.setEnabled(this, on));

        Button now = new Button(this);
        now.setText(R.string.phone_linux_update_now);
        now.setOnClickListener(v -> {
            PhoneLinuxUpdateJob.runNow(this);
            Toast.makeText(this, R.string.phone_linux_update_started, Toast.LENGTH_SHORT).show();
        });

        mLast = new TextView(this);
        root.addView(enabled);
        root.addView(now);
        root.addView(mLast);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String last = "";
        if (LOG.isFile()) {
            try (InputStream in = new FileInputStream(LOG)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                last = UpdateLog.lastResult(new String(out.toByteArray(), StandardCharsets.UTF_8));
            } catch (java.io.IOException ignored) {
            }
        }
        mLast.setText(getString(R.string.phone_linux_update_last,
            last.isEmpty() ? getString(R.string.phone_linux_update_never) : last));
    }
}
