/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

import com.android.systemui.R;

public class VibrateToggle extends Toggle {

    public VibrateToggle(Context context) {
        super(context);

        updateState();
        setLabel(R.string.toggle_vibrate);
        if (mToggle.isChecked()) {
            setIcon(R.drawable.toggle_vibrate);
        } else {
            setIcon(R.drawable.toggle_vibrate_off);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        context.registerReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                updateState();
            }
        }, filter);
    }

    @Override
    protected boolean updateInternalToggleState() {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        int mode = am.getRingerMode();
        mToggle.setChecked(mode == AudioManager.RINGER_MODE_VIBRATE);
        if (mToggle.isChecked()) {
            setIcon(R.drawable.toggle_vibrate);
            return true;
        } else {
            setIcon(R.drawable.toggle_vibrate_off);
            return false;
        }
    }

    @Override
    protected void onCheckChanged(boolean isChecked) {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(isChecked ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_NORMAL);
        if (mToggle.isChecked()) {
            setIcon(R.drawable.toggle_vibrate);
        } else {
            setIcon(R.drawable.toggle_vibrate_off);
        }
    }

    @Override
    protected void onStatusbarExpanded() {
        super.onStatusbarExpanded();
        updateState();
    }

    @Override
    protected boolean onLongPress() {
        Intent intent = new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        return true;
    }
}
