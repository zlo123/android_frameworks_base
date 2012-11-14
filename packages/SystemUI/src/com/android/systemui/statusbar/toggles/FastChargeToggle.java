/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.toggles;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;

import com.android.systemui.R;

public class FastChargeToggle extends Toggle {

    public static final String FAST_CHARGE_DIR = "/sys/kernel/fast_charge";
    public static final String FAST_CHARGE_FILE = "force_fast_charge";

    public FastChargeToggle(Context context) {
        super(context);

        setLabel(R.string.toggle_fastcharge);
        IntentFilter f = new IntentFilter();
        f.addAction("com.android.settings.FCHARGE_CHANGED");
        context.registerReceiver(mIntentReceiver, f);
        updateState();
    }

    @Override
    protected boolean updateInternalToggleState() {
        mToggle.setChecked(isFastChargeOn());
        if (mToggle.isChecked()) {
            setIcon(R.drawable.toggle_fastcharge);
        } else {
            setIcon(R.drawable.toggle_fastcharge_off);
        }
        return mToggle.isChecked();
    }

    @Override
    protected void onCheckChanged(boolean isChecked) {
        updateFastCharge(isChecked);
        updateState();
    }

    @Override
    protected boolean onLongPress() {
        return false;
    }

    public boolean isFastChargeOn() {
        try {
            File fastcharge = new File(FAST_CHARGE_DIR, FAST_CHARGE_FILE);
            FileReader reader = new FileReader(fastcharge);
            BufferedReader breader = new BufferedReader(reader);
            return (breader.readLine().equals("1"));
        } catch (IOException e) {
            Log.e("FastChargeToggle", "Couldn't read fast_charge file");
            return false;
        }
    }

    public void updateFastCharge(boolean on) {
        try {
            File fastcharge = new File(FAST_CHARGE_DIR, FAST_CHARGE_FILE);
            FileWriter fwriter = new FileWriter(fastcharge);
            BufferedWriter bwriter = new BufferedWriter(fwriter);
            bwriter.write(on ? "1" : "0");
            bwriter.close();
        } catch (IOException e) {
            Log.e("FastChargeToggle", "Couldn't write fast_charge file");
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("com.android.settings.FCHARGE_CHANGED")) {
                updateState();
            }
        }
    };
}
