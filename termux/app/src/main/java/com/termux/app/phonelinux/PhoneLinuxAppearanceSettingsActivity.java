package com.termux.app.phonelinux;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PhoneLinuxAppearanceSettingsActivity extends AppCompatActivity {

    private static final String LOG_TAG = "PhoneLinuxAppearance";
    private static final int REQUEST_PICK_IMAGE = 1;

    private AppearanceSettings mSettings;
    private TermuxAppSharedPreferences mPrefs;
    private TextView mFontSizeLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = TermuxAppSharedPreferences.build(this, true);
        if (mPrefs == null) {
            finish();
            return;
        }
        mSettings = AppearanceSettings.load();

        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text(getString(R.string.phone_linux_appearance_title), 22);
        root.addView(title);

        SwitchCompat toolbar = new SwitchCompat(this);
        toolbar.setText(R.string.phone_linux_appearance_toolbar);
        toolbar.setChecked(mPrefs.shouldShowTerminalToolbar());
        toolbar.setOnCheckedChangeListener((v, on) -> {
            mPrefs.setShowTerminalToolbar(on);
            PhoneLinuxAppearance.markChanged();
        });
        root.addView(toolbar, rowParams());

        mFontSizeLabel = text("", 16);
        root.addView(mFontSizeLabel, rowParams());
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.addView(button("A-", v -> changeFontSize(-2)));
        sizeRow.addView(button("A+", v -> changeFontSize(2)));
        root.addView(sizeRow);
        updateFontSizeLabel();

        SwitchCompat font = new SwitchCompat(this);
        font.setText(R.string.phone_linux_appearance_font_bundled);
        font.setChecked(mSettings.bundledFont);
        font.setOnCheckedChangeListener((v, on) -> {
            mSettings.bundledFont = on;
            save();
            PhoneLinuxAppearance.syncFont(this);
        });
        root.addView(font, rowParams());
        root.addView(text(getString(R.string.phone_linux_appearance_font_note), 12));

        root.addView(text(getString(R.string.phone_linux_appearance_preset), 18), rowParams());
        RadioGroup presets = new RadioGroup(this);
        int[] labels = {R.string.phone_linux_appearance_preset_default, R.string.phone_linux_appearance_preset_teal,
            R.string.phone_linux_appearance_preset_light};
        for (int i = 0; i < ColorPresets.NAMES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(labels[i]);
            rb.setTag(ColorPresets.NAMES[i]);
            presets.addView(rb);
            if (ColorPresets.NAMES[i].equals(mSettings.preset)) rb.setChecked(true);
        }
        presets.setOnCheckedChangeListener((group, id) -> {
            mSettings.preset = (String) group.findViewById(id).getTag();
            save();
            try {
                PhoneLinuxAppearance.applyColorPreset(mSettings.preset);
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply colour preset", e);
            }
        });
        root.addView(presets);

        root.addView(text(getString(R.string.phone_linux_appearance_background), 18), rowParams());
        LinearLayout bgRow = new LinearLayout(this);
        bgRow.addView(button(getString(R.string.phone_linux_appearance_pick_image), v -> pickImage()));
        bgRow.addView(button(getString(R.string.phone_linux_appearance_clear_background), v -> {
            mSettings.bgMode = AppearanceSettings.MODE_NONE;
            save();
        }));
        root.addView(bgRow);

        EditText color = new EditText(this);
        color.setHint(R.string.phone_linux_appearance_color_hint);
        color.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        color.setSingleLine(true);
        if (AppearanceSettings.MODE_COLOR.equals(mSettings.bgMode)) color.setText(AppearanceSettings.formatColor(mSettings.bgColor));
        root.addView(color);
        root.addView(button(getString(R.string.phone_linux_appearance_use_color), v -> {
            Integer c = AppearanceSettings.parseColor(color.getText().toString());
            if (c == null) {
                Toast.makeText(this, R.string.phone_linux_appearance_color_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            mSettings.bgMode = AppearanceSettings.MODE_COLOR;
            mSettings.bgColor = c;
            save();
        }));

        TextView dimLabel = text("", 16);
        root.addView(dimLabel, rowParams());
        SeekBar dim = new SeekBar(this);
        dim.setMax(AppearanceSettings.DIM_MAX);
        dim.setProgress(mSettings.dim);
        dimLabel.setText(getString(R.string.phone_linux_appearance_dim, mSettings.dim));
        dim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                dimLabel.setText(getString(R.string.phone_linux_appearance_dim, progress));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                mSettings.dim = bar.getProgress();
                save();
            }
        });
        root.addView(dim);
    }

    private void changeFontSize(int delta) {
        mPrefs.setFontSize(mPrefs.getFontSize() + delta);
        PhoneLinuxAppearance.markChanged();
        updateFontSizeLabel();
    }

    private void updateFontSizeLabel() {
        mFontSizeLabel.setText(getString(R.string.phone_linux_appearance_font_size, mPrefs.getFontSize()));
    }

    private void save() {
        try {
            mSettings.save();
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save appearance settings", e);
        }
        PhoneLinuxAppearance.markChanged();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String ext = AppearanceSettings.extForMime(getContentResolver().getType(uri));
        new Thread(() -> {
            boolean ok = copyImage(uri, ext);
            runOnUiThread(() -> {
                if (!ok) {
                    Toast.makeText(this, R.string.phone_linux_appearance_image_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                mSettings.bgMode = AppearanceSettings.MODE_IMAGE;
                mSettings.bgExt = ext;
                save();
            });
        }, "phone-linux-pick-image").start();
    }

    /** Copies the picked image to ~/.phone-linux/background.&lt;ext&gt;, replacing any earlier one. */
    private boolean copyImage(Uri uri, String ext) {
        File dir = new File(AppearanceSettings.DIR_PATH);
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        File tmp = new File(dir, "background.tmp");
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return false;
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        } catch (IOException | SecurityException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to copy image", e);
            tmp.delete();
            return false;
        }
        for (String other : new String[] {"jpg", "png", "webp"}) new File(dir, "background." + other).delete();
        return tmp.renameTo(new File(dir, "background." + ext));
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(16);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
