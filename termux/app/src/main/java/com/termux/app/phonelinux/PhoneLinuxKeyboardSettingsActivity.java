package com.termux.app.phonelinux;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;

import java.util.LinkedHashMap;
import java.util.Map;

/** Layout (JIS/US), JP mode switching and shortcut bindings; saved to $HOME/.phone-linux/keys.json. */
public class PhoneLinuxKeyboardSettingsActivity extends AppCompatActivity {

    private final Map<String, EditText> mFields = new LinkedHashMap<>();
    private RadioButton mJis;
    private Switch mJpSwitch;

    private static final int[] ACTION_LABELS = {
        R.string.phone_linux_kb_action_copy, R.string.phone_linux_kb_action_paste,
        R.string.phone_linux_kb_action_new_session, R.string.phone_linux_kb_action_next_session,
        R.string.phone_linux_kb_action_prev_session, R.string.phone_linux_kb_action_font_up,
        R.string.phone_linux_kb_action_font_down, R.string.phone_linux_kb_action_insert_image,
        R.string.phone_linux_kb_action_newline,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.phone_linux_kb_title);
        KeysConfig config = KeysStore.load();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        root.addView(heading(getString(R.string.phone_linux_kb_layout), 16));
        RadioGroup group = new RadioGroup(this);
        mJis = new RadioButton(this);
        mJis.setId(View.generateViewId());
        mJis.setText(R.string.phone_linux_kb_layout_jis);
        RadioButton us = new RadioButton(this);
        us.setId(View.generateViewId());
        us.setText(R.string.phone_linux_kb_layout_us);
        group.addView(mJis);
        group.addView(us);
        group.check(config.jis ? mJis.getId() : us.getId());
        root.addView(group);

        mJpSwitch = new Switch(this);
        mJpSwitch.setText(R.string.phone_linux_kb_jp_switch);
        mJpSwitch.setChecked(config.jpModeSwitch);
        root.addView(mJpSwitch);

        root.addView(heading(getString(R.string.phone_linux_kb_shortcuts), 16));
        for (int i = 0; i < KeysConfig.ACTIONS.length; i++) {
            String action = KeysConfig.ACTIONS[i];
            TextView label = new TextView(this);
            label.setText(ACTION_LABELS[i]);
            root.addView(label);
            EditText field = new EditText(this);
            field.setSingleLine();
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            field.setText(config.shortcuts.get(action));
            mFields.put(action, field);
            root.addView(field);
        }

        Button save = new Button(this);
        save.setText(R.string.phone_linux_kb_save);
        save.setOnClickListener(v -> save());
        root.addView(save);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView heading(String text, int sp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    private void save() {
        KeysConfig config = KeysConfig.defaults();
        config.jis = mJis.isChecked();
        config.jpModeSwitch = mJpSwitch.isChecked();
        for (Map.Entry<String, EditText> e : mFields.entrySet()) {
            String text = e.getValue().getText().toString().trim();
            if (!text.isEmpty() && KeysConfig.Binding.parse(text) == null) {
                Toast.makeText(this, getString(R.string.phone_linux_kb_invalid, text), Toast.LENGTH_LONG).show();
                return;
            }
            config.shortcuts.put(e.getKey(), text);
        }
        Toast.makeText(this, KeysStore.save(config) ? R.string.phone_linux_kb_saved : R.string.phone_linux_kb_save_failed,
            Toast.LENGTH_SHORT).show();
    }
}
