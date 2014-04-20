/*
 * Copyright (C) 2013 Mahdi-Rom
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

package com.android.settings.mahdi;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.PreferenceCategory;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Display;
import android.view.Window;
import android.widget.Toast;

import com.android.internal.util.mahdi.DeviceUtils;
import com.android.internal.util.cm.LockscreenBackgroundUtil;

import com.android.settings.R;
import com.android.settings.ChooseLockSettingsHelper;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

public class LockscreenInterface extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "LockscreenInterface";

    private static final String LOCKSCREEN_GENERAL_CATEGORY = "lockscreen_general_category";
    private static final String KEY_LOCKSCREEN_MODLOCK_ENABLED = "lockscreen_modlock_enabled";
    private static final String KEY_BATTERY_STATUS = "lockscreen_battery_status";
    private static final String BATTERY_AROUND_LOCKSCREEN_RING = "battery_around_lockscreen_ring";
    private static final String LOCKSCREEN_SHORTCUTS_CATEGORY = "lockscreen_shortcuts_category";
    private static final String PREF_LOCKSCREEN_TORCH = "lockscreen_glowpad_torch";
    private static final String LOCKSCREEN_BACKGROUND_STYLE = "lockscreen_background_style";

    private static final String LOCKSCREEN_WALLPAPER_TEMP_NAME = ".lockwallpaper";

    private static final int REQUEST_PICK_WALLPAPER = 201;
        
    private CheckBoxPreference mEnableModLock;
    private ListPreference mBatteryStatus;
    private CheckBoxPreference mLockRingBattery;
    private CheckBoxPreference mGlowpadTorch;
    private ListPreference mLockBackground;

    private boolean mCheckPreferences;

    private Activity mActivity;
    private ContentResolver mResolver;

    private File mTempWallpaper, mWallpaper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivity = getActivity();
        mResolver = mActivity.getContentResolver();              

        createCustomLockscreenView();
    }

    private PreferenceScreen createCustomLockscreenView() {
        mCheckPreferences = false;
        PreferenceScreen prefs = getPreferenceScreen();
        if (prefs != null) {
            prefs.removeAll();
        }

        addPreferencesFromResource(R.xml.lockscreen_interface_settings);
        prefs = getPreferenceScreen();           

        // Find categories
        PreferenceCategory generalCategory = (PreferenceCategory)
                findPreference(LOCKSCREEN_GENERAL_CATEGORY);
        PreferenceCategory lockscreen_shortcuts_category = (PreferenceCategory)
                findPreference(LOCKSCREEN_SHORTCUTS_CATEGORY);

        mEnableModLock = (CheckBoxPreference) findPreference(KEY_LOCKSCREEN_MODLOCK_ENABLED);
        if (mEnableModLock != null) {
            mEnableModLock.setOnPreferenceChangeListener(this);
        }

        mBatteryStatus = (ListPreference) findPreference(KEY_BATTERY_STATUS);
        if (mBatteryStatus != null) {
            mBatteryStatus.setOnPreferenceChangeListener(this);
        }
        
        // Update battery status
        if (mBatteryStatus != null) {
            ContentResolver cr = getActivity().getContentResolver();
            int batteryStatus = Settings.System.getInt(cr,
                    Settings.System.LOCKSCREEN_BATTERY_VISIBILITY, 0);
            mBatteryStatus.setValueIndex(batteryStatus);
            mBatteryStatus.setSummary(mBatteryStatus.getEntries()[batteryStatus]);
        }

        mLockRingBattery = (CheckBoxPreference)findPreference(BATTERY_AROUND_LOCKSCREEN_RING);
        mLockRingBattery.setChecked(Settings.System.getInt(getActivity().getApplicationContext().getContentResolver(),
                Settings.System.BATTERY_AROUND_LOCKSCREEN_RING, 0) == 1);

        // Remove glowpad torch if device doesn't have a torch
        mGlowpadTorch = (CheckBoxPreference) findPreference(PREF_LOCKSCREEN_TORCH);
        if (!Utils.isPhone(getActivity())) {
            lockscreen_shortcuts_category.removePreference(mGlowpadTorch);
        }

        if (mEnableModLock != null) {
            generalCategory.removePreference(mEnableModLock);
            mEnableModLock = null;
        }

        mLockBackground = (ListPreference) findPreference(LOCKSCREEN_BACKGROUND_STYLE);
        mLockBackground.setOnPreferenceChangeListener(this);

        mTempWallpaper = getActivity().getFileStreamPath(LOCKSCREEN_WALLPAPER_TEMP_NAME);
        mWallpaper = LockscreenBackgroundUtil.getWallpaperFile(getActivity());
                
        final int unsecureUnlockMethod = Settings.Secure.getInt(getActivity().getContentResolver(),
                Settings.Secure.LOCKSCREEN_UNSECURE_USED, 1);

        //setup custom lockscreen customize view
        if ((unsecureUnlockMethod != 1)
                 || unsecureUnlockMethod == -1) {             
        }
                        
        mCheckPreferences = true;
        return prefs;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Update mod lockscreen status
        if (mEnableModLock != null) {
            ContentResolver cr = getActivity().getContentResolver();
            boolean checked = Settings.System.getInt(
                    cr, Settings.System.LOCKSCREEN_MODLOCK_ENABLED, 1) == 1;
            mEnableModLock.setChecked(checked);
        }
        createCustomLockscreenView();
        updateBackgroundPreference();
    }

    @Override
    public void onPause() {
        super.onPause();        
    }

    private void updateBackgroundPreference() {
        int lockVal = LockscreenBackgroundUtil.getLockscreenStyle(getActivity());
        mLockBackground.setValue(Integer.toString(lockVal));
    }

    /**
     * Checks if the device has hardware buttons.
     * @return has Buttons
     */
    public boolean hasButtons() {
        return !getResources().getBoolean(com.android.internal.R.bool.config_showNavigationBar);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {        
        if (preference == mLockRingBattery) {
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.BATTERY_AROUND_LOCKSCREEN_RING, 
                    mLockRingBattery.isChecked() ? 1 : 0);
            return true;
        }
       return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        ContentResolver cr = getActivity().getContentResolver();
        if (preference == mBatteryStatus) {
            int value = Integer.valueOf((String) objValue);
            int index = mBatteryStatus.findIndexOfValue((String) objValue);
            Settings.System.putInt(cr, Settings.System.LOCKSCREEN_BATTERY_VISIBILITY, value);
            mBatteryStatus.setSummary(mBatteryStatus.getEntries()[index]);
            return true;
        } else if (preference == mLockBackground) {
            int index = mLockBackground.findIndexOfValue((String) objValue);
            handleBackgroundSelection(index);
            return true;
        } else if (preference == mEnableModLock) {
            boolean value = (Boolean) objValue;
            Settings.System.putInt(cr, Settings.System.LOCKSCREEN_MODLOCK_ENABLED,
                    value ? 1 : 0);
            return true;
        }
        return false;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_WALLPAPER) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data != null ? data.getData() : null;
                if (uri == null) {
                    uri = Uri.fromFile(mTempWallpaper);
                }
                new SaveUserWallpaperTask().execute(uri);
            } else {
                toastLockscreenWallpaperStatus(false);
            }
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) {
        ParcelFileDescriptor parcelFileDescriptor = null;
        try {
            parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            return image;
        } catch (IOException e) {
        } finally {
            if (parcelFileDescriptor != null) {
                try {
                    parcelFileDescriptor.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }

    private void handleBackgroundSelection(int index) {
        if (index == LockscreenBackgroundUtil.LOCKSCREEN_STYLE_IMAGE) {
            // Launches intent for user to select an image/crop it to set as background
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
            intent.setType("image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("scale", true);
            intent.putExtra("scaleUpIfNeeded", false);
            intent.putExtra("scaleType", 6);
            intent.putExtra("layout_width", -1);
            intent.putExtra("layout_height", -2);
            intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());

            final Display display = getActivity().getWindowManager().getDefaultDisplay();
            boolean isPortrait = getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_PORTRAIT;

            Point screenDimension = new Point();
            display.getSize(screenDimension);
            int width = screenDimension.x;
            int height = screenDimension.y;

            intent.putExtra("aspectX", isPortrait ? width : height);
            intent.putExtra("aspectY", isPortrait ? height : width);

            try {
                mTempWallpaper.createNewFile();
                mTempWallpaper.setWritable(true, false);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTempWallpaper));
                intent.putExtra("return-data", false);
                getActivity().startActivityFromFragment(this, intent, REQUEST_PICK_WALLPAPER);
            } catch (IOException e) {
                toastLockscreenWallpaperStatus(false);
            } catch (ActivityNotFoundException e) {
                toastLockscreenWallpaperStatus(false);
            }
        } else if (index == LockscreenBackgroundUtil.LOCKSCREEN_STYLE_DEFAULT) {
            // Sets background to default
            Settings.System.putInt(getContentResolver(),
                            Settings.System.LOCKSCREEN_BACKGROUND_STYLE, LockscreenBackgroundUtil.LOCKSCREEN_STYLE_DEFAULT);
            if (mWallpaper.exists()) {
                mWallpaper.delete();
            }
            updateKeyguardWallpaper();
            updateBackgroundPreference();
        }
    }

    private void toastLockscreenWallpaperStatus(boolean success) {
        Toast.makeText(getActivity(), getResources().getString(
                success ? R.string.background_result_successful
                        : R.string.background_result_not_successful),
                Toast.LENGTH_LONG).show();
    }

    private void updateKeyguardWallpaper() {
        getActivity().sendBroadcast(new Intent(Intent.ACTION_KEYGUARD_WALLPAPER_CHANGED));
    }

    private class SaveUserWallpaperTask extends AsyncTask<Uri, Void, Boolean> {

        private Toast mToast;

        @Override
        protected void onPreExecute() {
            mToast = Toast.makeText(getActivity(), R.string.setting_lockscreen_background,
                    Toast.LENGTH_LONG);
            mToast.show();
        }

        @Override
        protected Boolean doInBackground(Uri... params) {
            if (getActivity().isFinishing()) {
                return false;
            }
            FileOutputStream out = null;
            try {
                Bitmap wallpaper = getBitmapFromUri(params[0]);
                if (wallpaper == null) {
                    return false;
                }
                mWallpaper.createNewFile();
                mWallpaper.setReadable(true, false);
                out = new FileOutputStream(mWallpaper);
                wallpaper.compress(Bitmap.CompressFormat.JPEG, 85, out);

                if (mTempWallpaper.exists()) {
                    mTempWallpaper.delete();
                }
                return true;
            } catch (IOException e) {
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                    }
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mToast.cancel();
            toastLockscreenWallpaperStatus(result);
            if (result) {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.LOCKSCREEN_BACKGROUND_STYLE,
                        LockscreenBackgroundUtil.LOCKSCREEN_STYLE_IMAGE);
                updateKeyguardWallpaper();
                updateBackgroundPreference();
            }
        }
    }
}
