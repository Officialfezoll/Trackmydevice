package com.trackmydevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Receiver to unhide the app when hidden (stealth mode).
 * Triggered by: notification action, ADB broadcast, or dial code.
 */
public class StealthReceiver extends BroadcastReceiver {

    private static final String TAG = "StealthReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Stealth unhide received");

        // Re-enable the app
        try {
            ComponentName componentName = new ComponentName(context, MainActivity.class);
            context.getPackageManager().setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            );

            // Save stealth mode off
            context.getSharedPreferences("DeviceInfo", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("stealth_mode", false)
                    .apply();

            // Launch the app
            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launchIntent);

            Toast.makeText(context, "TrackMyDevice is now visible", Toast.LENGTH_LONG).show();
            Log.d(TAG, "App unhidden successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to unhide app", e);
        }
    }
}
