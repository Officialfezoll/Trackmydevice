package com.trackmydevice;

import android.app.Application;
import android.util.Log;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class TrackMyDeviceApp extends Application {

    private static final String TAG = "TrackMyDeviceApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TrackMyDevice Application started");

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized");

            // Get FCM token
            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM token failed", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);

                    // Save token locally
                    SharedPreferences prefs = getSharedPreferences("DeviceInfo", MODE_PRIVATE);
                    prefs.edit().putString("fcm_token", token).apply();

                    // Send to server if device is registered
                    if (prefs.contains("token")) {
                        sendTokenToServer(token, prefs.getString("url", ""), prefs.getString("token", ""));
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Firebase init error", e);
        }
    }

    private void sendTokenToServer(String token, String baseUrl, String deviceToken) {
        if (baseUrl == null || baseUrl.isEmpty() || deviceToken == null || deviceToken.isEmpty()) {
            return;
        }

        final String finalBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        final String finalToken = token;
        final String finalDeviceToken = deviceToken;

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(finalBaseUrl + "api/device/fcm-token");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-Device-Token", finalDeviceToken);
                conn.setDoOutput(true);

                String json = "{\"fcm_token\":\"" + finalToken + "\"}";
                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.close();

                int code = conn.getResponseCode();
                Log.d(TAG, "FCM Token sent to server, response: " + code);
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Failed to send FCM token", e);
            }
        }).start();
    }
}
