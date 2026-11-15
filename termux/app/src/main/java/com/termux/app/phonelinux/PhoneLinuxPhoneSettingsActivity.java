package com.termux.app.phonelinux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/** Status of the phone-control service and management of its API keys. */
public class PhoneLinuxPhoneSettingsActivity extends AppCompatActivity {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long[] EXPIRY_DAYS = {0, 1, 7, 30, 90};

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.phone_linux_phone_title);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        root.removeAllViews();
        TextView title = text(getString(R.string.phone_linux_phone_title), 20);
        root.addView(title);

        String status = PhoneControlService.isRunning()
            ? getString(R.string.phone_linux_phone_status_on, PhoneApiConfig.port(this))
            : PhoneControlService.lastError() != null
                ? getString(R.string.phone_linux_phone_status_error, PhoneControlService.lastError())
                : getString(R.string.phone_linux_phone_status_off);
        root.addView(text(status, 16));
        root.addView(button(R.string.phone_linux_phone_open_accessibility, v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        LinearLayout portRow = new LinearLayout(this);
        EditText port = new EditText(this);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText(String.valueOf(PhoneApiConfig.port(this)));
        port.setHint(R.string.phone_linux_phone_port);
        portRow.addView(port, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        portRow.addView(button(R.string.phone_linux_phone_save_port, v -> savePort(port.getText().toString())));
        root.addView(portRow);

        root.addView(text(getString(R.string.phone_linux_phone_keys), 18));
        root.addView(button(R.string.phone_linux_phone_claude_key, v -> {
            try {
                PhoneApiConfig.issueClaudeKey(this);
                Toast.makeText(this, getString(R.string.phone_linux_phone_claude_key_done, PhoneApiConfig.KEY_FILE.getPath()), Toast.LENGTH_LONG).show();
                refresh();
            } catch (IOException e) {
                Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
            }
        }));
        root.addView(button(R.string.phone_linux_phone_issue_key, v -> showIssueDialog()));

        DateFormat df = DateFormat.getDateInstance();
        for (PhoneApiKeys.Key k : PhoneApiConfig.keys(this).list()) {
            String expiry = k.expiresAt == 0 ? getString(R.string.phone_linux_phone_no_expiry) : df.format(new Date(k.expiresAt));
            String line = getString(R.string.phone_linux_phone_key_line, k.name, TextUtils.join("/", k.scopes), expiry,
                getString(k.confirm ? R.string.phone_linux_phone_confirm_on : R.string.phone_linux_phone_confirm_off));
            LinearLayout row = new LinearLayout(this);
            row.addView(text(line, 14), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(button(R.string.phone_linux_phone_revoke, v -> revoke(k)));
            root.addView(row);
        }
    }

    private void savePort(String value) {
        try {
            int p = Integer.parseInt(value.trim());
            if (p < 1024 || p > 65535) throw new NumberFormatException();
            PhoneApiConfig.setPort(this, p);
            PhoneControlService.restart();
            refresh();
        } catch (NumberFormatException | IOException e) {
            Toast.makeText(this, R.string.phone_linux_phone_port_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    private void revoke(PhoneApiKeys.Key k) {
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.phone_linux_phone_revoke_confirm, k.name))
            .setPositiveButton(R.string.phone_linux_phone_revoke, (d, w) -> {
                try {
                    PhoneApiConfig.keys(this).revoke(k.id);
                } catch (IOException e) {
                    Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                }
                refresh();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showIssueDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = new EditText(this);
        name.setHint(R.string.phone_linux_phone_key_name);
        form.addView(name);
        CheckBox read = check(form, R.string.phone_linux_phone_scope_read, true);
        CheckBox act = check(form, R.string.phone_linux_phone_scope_act, false);
        CheckBox open = check(form, R.string.phone_linux_phone_scope_open, false);
        CheckBox confirm = check(form, R.string.phone_linux_phone_confirm_each, true);
        Spinner expiry = new Spinner(this);
        String[] labels = new String[EXPIRY_DAYS.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = EXPIRY_DAYS[i] == 0 ? getString(R.string.phone_linux_phone_no_expiry) : getString(R.string.phone_linux_phone_expiry_days, (int) EXPIRY_DAYS[i]);
        }
        expiry.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        form.addView(expiry);

        new AlertDialog.Builder(this)
            .setTitle(R.string.phone_linux_phone_issue_key)
            .setView(form)
            .setPositiveButton(R.string.phone_linux_phone_issue, (d, w) -> {
                Set<String> scopes = new LinkedHashSet<>();
                if (read.isChecked()) scopes.add(PhoneApiKeys.SCOPE_READ);
                if (act.isChecked()) scopes.add(PhoneApiKeys.SCOPE_ACT);
                if (open.isChecked()) scopes.add(PhoneApiKeys.SCOPE_OPEN);
                String n = name.getText().toString().trim();
                if (n.isEmpty() || scopes.isEmpty()) {
                    Toast.makeText(this, R.string.phone_linux_phone_issue_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }
                long now = System.currentTimeMillis();
                long days = EXPIRY_DAYS[expiry.getSelectedItemPosition()];
                try {
                    PhoneApiKeys.Issued issued = PhoneApiConfig.keys(this).issue(n, scopes, days == 0 ? 0 : now + days * DAY, confirm.isChecked(), now);
                    showToken(issued.token);
                } catch (IOException e) {
                    Toast.makeText(this, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showToken(String token) {
        TextView t = text(token, 14);
        t.setTextIsSelectable(true);
        t.setPadding(dp(24), dp(8), dp(24), 0);
        new AlertDialog.Builder(this)
            .setTitle(R.string.phone_linux_phone_token_title)
            .setMessage(R.string.phone_linux_phone_token_once)
            .setView(t)
            .setPositiveButton(R.string.phone_linux_phone_copy, (d, w) -> {
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("phone-api-key", token));
                Toast.makeText(this, R.string.phone_linux_phone_copied, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.phone_linux_phone_close, null)
            .setOnDismissListener(d -> refresh())
            .show();
    }

    private CheckBox check(LinearLayout parent, int label, boolean checked) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setChecked(checked);
        parent.addView(c);
        return c;
    }

    private Button button(int label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
