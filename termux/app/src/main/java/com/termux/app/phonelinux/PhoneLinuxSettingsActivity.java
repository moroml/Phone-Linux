package com.termux.app.phonelinux;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

/** Settings hub: links to every Phone-Linux settings screen plus the original Termux settings. */
public class PhoneLinuxSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        list.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.phone_linux_settings_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setPadding(0, 0, 0, pad);
        list.addView(title);

        addEntry(list, R.string.phone_linux_settings_keyboard, PhoneLinuxKeyboardSettingsActivity.class);
        addEntry(list, R.string.phone_linux_appearance_title, PhoneLinuxAppearanceSettingsActivity.class);
        addEntry(list, R.string.phone_linux_settings_phone, PhoneLinuxPhoneSettingsActivity.class);
        addEntry(list, R.string.phone_linux_update_title, PhoneLinuxUpdateSettingsActivity.class);
        addEntry(list, R.string.phone_linux_settings_termux, SettingsActivity.class);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        setContentView(scroll);
    }

    private void addEntry(LinearLayout list, int label, Class<? extends Activity> target) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> startActivity(new Intent(this, target)));
        list.addView(button);
    }
}
