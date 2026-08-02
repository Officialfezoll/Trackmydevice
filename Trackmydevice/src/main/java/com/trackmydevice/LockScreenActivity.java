package com.trackmydevice;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LockScreenActivity extends Activity {

    private static final String TAG = "LockScreenActivity";
    private SharedPreferences prefs;
    private int failCount = 0;
    private BroadcastReceiver unlockReceiver;
    private boolean isUnlocking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if this is a force unlock from remote command
        if (getIntent() != null && getIntent().getBooleanExtra("force_unlock", false)) {
            finish();
            return;
        }

        // Check if already unlocked
        prefs = getSharedPreferences("DeviceInfo", MODE_PRIVATE);
        if (!prefs.getBoolean("is_locked", false)) {
            finish();
            return;
        }

        // Mark as locked
        prefs.edit().putBoolean("is_locked", true).apply();

        // FULLSCREEN IMMERSIVE MODE - hide ALL system bars
        setupFullScreen();

        setContentView(R.layout.activity_lockscreen);

        // Listen for remote unlock broadcast
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Unlock broadcast received");
                isUnlocking = true;
                prefs.edit().putBoolean("is_locked", false).apply();
                sendUnlockEvent();
                finish();
            }
        };
        IntentFilter filter = new IntentFilter("com.trackmydevice.UNLOCK");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(unlockReceiver, filter);
        }

        // Set owner info
        TextView lockOwner = findViewById(R.id.lockOwner);
        TextView lockMessage = findViewById(R.id.lockMessage);
        TextView lockSubtitle = findViewById(R.id.lockSubtitle);

        String ownerName = prefs.getString("owner_name", "TrackMyDevice Admin");
        String ownerMessage = prefs.getString("owner_message", "This device is tracked and locked remotely.");
        String ownerPhone = prefs.getString("owner_phone", "");

        lockOwner.setText("Locked by: " + ownerName);
        lockMessage.setText(ownerMessage);

        if (!ownerPhone.isEmpty()) {
            lockSubtitle.setText("Contact: " + ownerPhone);
        }

        // PIN input
        EditText pinInput = findViewById(R.id.pinInput);
        Button btnUnlock = findViewById(R.id.btnUnlock);
        Button btnEmergency = findViewById(R.id.btnEmergency);
        TextView errorText = findViewById(R.id.errorText);

        btnUnlock.setOnClickListener(v -> {
            String pin = pinInput.getText().toString().trim();
            String savedPin = prefs.getString("unlock_pin", "");

            if (savedPin.isEmpty()) {
                isUnlocking = true;
                prefs.edit().putBoolean("is_locked", false).apply();
                sendUnlockEvent();
                finish();
                return;
            }

            if (pin.equals(savedPin)) {
                isUnlocking = true;
                prefs.edit().putBoolean("is_locked", false).apply();
                sendUnlockEvent();
                finish();
            } else {
                failCount++;
                errorText.setVisibility(View.VISIBLE);
                errorText.setText("Wrong PIN. Attempt " + failCount + "/5");

                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(500);
                    }
                }

                pinInput.setText("");

                if (failCount >= 5) {
                    errorText.setText("Too many attempts. Try again in 60 seconds.");
                    pinInput.setEnabled(false);
                    btnUnlock.setEnabled(false);
                    pinInput.postDelayed(() -> {
                        pinInput.setEnabled(true);
                        btnUnlock.setEnabled(true);
                        failCount = 0;
                        errorText.setVisibility(View.GONE);
                    }, 60000);
                    sendFailedAttemptAlert();
                }
            }
        });

        btnEmergency.setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL);
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);
        });
    }

    private void setupFullScreen() {
        // Hide navigation bar and status bar
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        // Keep screen on, show over lock screen
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        // Make it a dialog-style activity that stays on top
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply fullscreen on resume
        setupFullScreen();

        // Re-lock if unlocked via back button or other means
        if (!isUnlocking && !prefs.getBoolean("is_locked", false)) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unlockReceiver != null) {
            try { unregisterReceiver(unlockReceiver); } catch (Exception ignored) {}
        }
        if (!isUnlocking) {
            // If not intentionally unlocked, re-lock
            prefs.edit().putBoolean("is_locked", true).apply();
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(lockIntent);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && !isUnlocking) {
            // Re-apply fullscreen when focus is lost
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isUnlocking) {
                    setupFullScreen();
                    // Bring back to front
                    Intent intent = new Intent(this, LockScreenActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            }, 100);
        }
    }

    // Block ALL navigation
    @Override
    public void onBackPressed() {
        // Do nothing - lock screen cannot be dismissed
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Keep lock screen alive
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isUnlocking) {
            // Immediately re-lock when paused
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isUnlocking && !isFinishing()) {
                    setupFullScreen();
                    Intent intent = new Intent(this, LockScreenActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            }, 50);
        }
    }

    private void sendFailedAttemptAlert() {
        new Thread(() -> {
            try {
                String baseUrl = prefs.getString("url", "");
                String token = prefs.getString("token", "");
                if (baseUrl.isEmpty() || token.isEmpty()) return;
                if (!baseUrl.endsWith("/")) baseUrl += "/";

                java.net.URL url = new java.net.URL(baseUrl + "api/device/status");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("X-Device-Token", token);
                conn.setDoOutput(true);
                conn.getOutputStream().write("{\"status\":\"alarm\"}".getBytes("UTF-8"));
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "sendFailedAttemptAlert error", e);
            }
        }).start();
    }

    private void sendUnlockEvent() {
        new Thread(() -> {
            try {
                String baseUrl = prefs.getString("url", "");
                String token = prefs.getString("token", "");
                if (baseUrl.isEmpty() || token.isEmpty()) return;
                if (!baseUrl.endsWith("/")) baseUrl += "/";

                java.net.URL url = new java.net.URL(baseUrl + "api/device/status");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("X-Device-Token", token);
                conn.setDoOutput(true);
                conn.getOutputStream().write("{\"status\":\"online\"}".getBytes("UTF-8"));
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "sendUnlockEvent error", e);
            }
        }).start();
    }
}
