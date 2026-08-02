package com.trackmydevice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Receiver to start tracking service after device boot
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.d(TAG, "Boot completed, checking if tracking should start");

            SharedPreferences prefs = context.getSharedPreferences(
                    "DeviceInfo", Context.MODE_PRIVATE);

            // Check if device is registered
            if (prefs.contains("token") && prefs.contains("short_code")) {
                Log.d(TAG, "Device registered, starting tracking service");

                Intent serviceIntent = new Intent(context, MyService.class);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.d(TAG, "Device not registered, skipping service start");
            }
        }
    }
}