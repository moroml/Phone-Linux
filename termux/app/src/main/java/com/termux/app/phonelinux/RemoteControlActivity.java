package com.termux.app.phonelinux;

import android.app.Activity;
import android.os.Bundle;

/** Invisible target of the launcher shortcut "Remote Control". */
public class RemoteControlActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PhoneLinuxLink.startRemoteControl(this);
        finish();
    }
}
