package com.trackmydevice;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.BatteryManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MyService extends Service {

    private static final String TAG = "MyService";
    private static final String CHANNEL_ID = "TrackingServiceChannel";
    private static final String ALERT_CHANNEL_ID = "AlertChannel";
    private static final int MAX_CHUNK = 500;
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int ALERT_CHECK_INTERVAL = 30000; // 30 seconds

    private SharedPreferences DeviceInfo;
    private Timer timer;
    private LocationManager locationManager;
    private Location lastLocation = null;
    private int lastBattery = -1;
    private LocationListener locationListener;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        try {
            DeviceInfo = getSharedPreferences("DeviceInfo", MODE_PRIVATE);

            createNotificationChannels();
            startForegroundServiceCompat();

            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

            timer = new Timer("MyServiceTimer", true);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        checkBattery();
                        checkSimChange();
                        syncLocations();
                        checkPendingAlerts();
                    } catch (Exception e) {
                        Log.e(TAG, "Timer task error", e);
                    }
                }
            }, 0, 15000); // Every 15 seconds instead of 60

            startLocationUpdates();

        } catch (Exception e) {
            Log.e(TAG, "onCreate crash", e);
            stopSelf();
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Tracking channel
            NotificationChannel trackingChannel = new NotificationChannel(
                    CHANNEL_ID, "Tracking Service", NotificationManager.IMPORTANCE_LOW);
            trackingChannel.setDescription("Shows when device tracking is active");
            trackingChannel.setShowBadge(false);
            manager.createNotificationChannel(trackingChannel);

            // Alert channel with sound
            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH);
            alertChannel.setDescription("Device alerts and notifications");
            alertChannel.enableVibration(true);
            alertChannel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            );
            manager.createNotificationChannel(alertChannel);
        }
    }

    private void startForegroundServiceCompat() {
        Notification notification = createNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } catch (Exception e) {
                Log.e(TAG, "startForeground with LOCATION failed, trying without type", e);
                startForeground(FOREGROUND_NOTIFICATION_ID, notification);
            }
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification);
        }
    }

    // =========================
    // 📡 LOCATION TRACKING (GPS + Network fallback)
    // =========================
    private void startLocationUpdates() {
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Location permission not granted");
                return;
            }

            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    handleLocation(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {}
            };

            // Try GPS first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        10000, 10, locationListener);
                Log.d(TAG, "GPS location updates started");
            }

            // Also try Network provider as fallback
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        15000, 15, locationListener);
                Log.d(TAG, "Network location updates started (fallback)");
            }

        } catch (Exception e) {
            Log.e(TAG, "startLocationUpdates error", e);
        }
    }

    private void handleLocation(Location location) {
        if (location == null) return;

        // Determine source
        String source = "gps";
        if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider())) {
            source = "network";
        }

        // Skip if location hasn't changed much (less than 10 meters)
        if (lastLocation != null && location.distanceTo(lastLocation) < 10) return;

        lastLocation = location;
        long timestamp = System.currentTimeMillis();
        String record = location.getLatitude() + "," +
                location.getLongitude() + "," +
                location.getAccuracy() + "," +
                (location.hasSpeed() ? location.getSpeed() : 0) + "," +
                (location.hasBearing() ? location.getBearing() : 0) + "," +
                source + "," +
                timestamp;

        saveLocation(record);
        Log.d(TAG, "Location saved: " + location.getLatitude() + ", " + location.getLongitude() + " (" + source + ")");
    }

    private void saveLocation(String record) {
        try {
            String queue = DeviceInfo.getString("loc_queue", "");
            if (queue.length() > 50000) {
                queue = queue.substring(queue.length() - 35000);
            }
            queue += record + ";;";
            DeviceInfo.edit().putString("loc_queue", queue).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveLocation error", e);
        }
    }

    // =========================
    // 🔄 BULK SYNC
    // =========================
    private void syncLocations() {
        try {
            if (!isOnline()) return;

            String queue = DeviceInfo.getString("loc_queue", "");
            if (queue == null || queue.isEmpty()) return;

            String[] items = queue.split(";;");
            if (items == null || items.length == 0) return;

            int total = items.length;
            int sent = 0;

            while (sent < total) {
                StringBuilder json = new StringBuilder("[");
                int count = 0;

                while (count < MAX_CHUNK && sent < total) {
                    String item = items[sent];
                    sent++;

                    if (item == null || item.isEmpty()) continue;

                    String[] p = item.split(",");
                    if (p.length < 7) continue;

                    try {
                        Double.parseDouble(p[0]);
                        Double.parseDouble(p[1]);
                        Float.parseFloat(p[2]);
                        Long.parseLong(p[6]);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    json.append("{")
                            .append("\"lat\":\"").append(p[0]).append("\",")
                            .append("\"lng\":\"").append(p[1]).append("\",")
                            .append("\"accuracy\":\"").append(p[2]).append("\",")
                            .append("\"speed\":\"").append(p[3]).append("\",")
                            .append("\"bearing\":\"").append(p[4]).append("\",")
                            .append("\"source\":\"").append(p[5]).append("\",")
                            .append("\"recorded_at\":\"").append(p[6]).append("\"")
                            .append("},");
                    count++;
                }

                if (json.length() > 1 && json.charAt(json.length() - 1) == ',') {
                    json.deleteCharAt(json.length() - 1);
                }
                json.append("]");

                if (json.length() > 2) {
                    boolean success = post("api/device/sync", json.toString());
                    if (!success) return;
                }
            }

            DeviceInfo.edit().remove("loc_queue").apply();

        } catch (Exception e) {
            Log.e(TAG, "syncLocations error", e);
        }
    }

    // =========================
    // 🔔 CHECK PENDING ALERTS
    // =========================
    private void checkPendingAlerts() {
        try {
            if (!isOnline()) return;
            if (!DeviceInfo.contains("token")) return;

            String baseUrl = DeviceInfo.getString("url", "");
            if (baseUrl == null || baseUrl.isEmpty()) return;
            if (!baseUrl.endsWith("/")) baseUrl += "/";

            // Check alerts
            URL alertsUrl = new URL(baseUrl + "api/device/pending-alerts");
            HttpURLConnection alertsConn = (HttpURLConnection) alertsUrl.openConnection();
            alertsConn.setRequestMethod("GET");
            alertsConn.setRequestProperty("X-Device-Token", DeviceInfo.getString("token", ""));
            alertsConn.setConnectTimeout(10000);
            alertsConn.setReadTimeout(15000);

            int alertsCode = alertsConn.getResponseCode();
            if (alertsCode == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(alertsConn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                parseAndShowAlerts(response.toString());
            }
            alertsConn.disconnect();

            // Check commands
            URL commandsUrl = new URL(baseUrl + "api/device/pending-commands");
            HttpURLConnection commandsConn = (HttpURLConnection) commandsUrl.openConnection();
            commandsConn.setRequestMethod("GET");
            commandsConn.setRequestProperty("X-Device-Token", DeviceInfo.getString("token", ""));
            commandsConn.setConnectTimeout(10000);
            commandsConn.setReadTimeout(15000);

            int commandsCode = commandsConn.getResponseCode();
            Log.d(TAG, "Commands response code: " + commandsCode);
            if (commandsCode == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(commandsConn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                parseAndExecuteCommands(response.toString());
            }
            commandsConn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "checkPendingCommands error", e);
        }
    }

    private void parseAndShowAlerts(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray alerts = root.optJSONArray("alerts");
            if (alerts == null || alerts.length() == 0) return;

            for (int i = 0; i < alerts.length(); i++) {
                JSONObject alert = alerts.getJSONObject(i);
                String type = alert.optString("type", "info");
                String message = alert.optString("message", "New alert");
                int priority = alert.optInt("priority", 1);

                showAlertNotification(type, message, priority);
            }

        } catch (Exception e) {
            Log.e(TAG, "parseAndShowAlerts error", e);
        }
    }

    private void parseAndExecuteCommands(String json) {
        Log.d(TAG, "parseAndExecuteCommands: " + json);
        try {
            JSONObject root = new JSONObject(json);
            JSONArray commands = root.optJSONArray("commands");
            if (commands == null || commands.length() == 0) {
                Log.d(TAG, "No commands found");
                return;
            }

            for (int i = 0; i < commands.length(); i++) {
                JSONObject cmd = commands.getJSONObject(i);
                String command = cmd.optString("command", "");
                Log.d(TAG, "Command received: " + command);
                if (!command.isEmpty()) {
                    showAlertNotification("command", "Command: " + command, 5);
                    // For set_pin, extract pin from description
                    if ("set_pin".equals(command)) {
                        String desc = cmd.optString("description", "");
                        if (desc.contains("PIN:")) {
                            String pin = desc.substring(desc.indexOf("PIN:") + 4).trim();
                            DeviceInfo.edit().putString("unlock_pin", pin).apply();
                            Log.d(TAG, "PIN set via polling: " + pin);
                            showAlertNotification("command", "🔑 PIN updated", 2);
                        }
                    } else {
                        handleCommand(command, null);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "parseAndExecuteCommands error", e);
        }
    }

    private void showAlertNotification(String type, String message, int priority) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        // Determine notification icon and sound based on type
        int icon = android.R.drawable.ic_dialog_info;
        boolean playSound = priority >= 3;

        if (type.contains("sos") || type.contains("alarm")) {
            icon = android.R.drawable.ic_dialog_alert;
            playSound = true;
        } else if (type.contains("geofence")) {
            icon = android.R.drawable.ic_dialog_info;
        } else if (type.contains("battery")) {
            icon = android.R.drawable.ic_dialog_info;
        } else if (type.contains("sim")) {
            icon = android.R.drawable.ic_dialog_alert;
            playSound = true;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle("TMD Alert")
                .setContentText(message)
                .setSmallIcon(icon)
                .setPriority(priority >= 3 ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        if (playSound) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

            // Also vibrate for critical alerts
            if (priority >= 4 && vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 500, 200, 500, 200, 500}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, -1);
                }
            }
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void showAlarmNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        // Create high priority channel for alarm
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    "AlarmChannel", "Device Alarm", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Device alarm notifications");
            channel.enableVibration(true);
            channel.setSound(null, null); // Will use default alarm
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "AlarmChannel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 DEVICE ALARM TRIGGERED!")
                .setContentText("Someone sent an alarm command to this device!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setVibrate(new long[]{0, 500, 200, 500, 200, 500, 200, 500});

        manager.notify(9999, builder.build());
    }

    // =========================
    // 🔋 BATTERY
    // =========================
    private void checkBattery() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus;

            if (Build.VERSION.SDK_INT >= 33) {
                batteryStatus = registerReceiver(null, ifilter, Context.RECEIVER_NOT_EXPORTED);
            } else if (Build.VERSION.SDK_INT >= 26) {
                batteryStatus = registerReceiver(null, ifilter, Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
            } else {
                batteryStatus = registerReceiver(null, ifilter);
            }

            if (batteryStatus == null) return;

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            if (level < 0 || scale <= 0) return;

            int batteryPct = (int) ((level / (float) scale) * 100);
            if (batteryPct == lastBattery) return;
            lastBattery = batteryPct;

            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            String json = "{"
                    + "\"level\":\"" + batteryPct + "\","
                    + "\"charging\":\"" + (charging ? "1" : "0") + "\""
                    + "}";

            post("api/device/battery", json);

        } catch (Exception e) {
            Log.e(TAG, "checkBattery error", e);
        }
    }

    // =========================
    // 📶 SIM CHANGE
    // =========================
    private void checkSimChange() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (tm == null) return;

            if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(this,
                            android.Manifest.permission.READ_PHONE_STATE)
                            != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            String currentSim = tm.getSimOperatorName();
            if (currentSim == null) currentSim = "unknown";

            String savedSim = DeviceInfo.getString("sim", "");

            if (savedSim.isEmpty()) {
                DeviceInfo.edit().putString("sim", currentSim).apply();
                return;
            }

            if (!savedSim.equals(currentSim)) {
                String json = "{"
                        + "\"old_sim\":\"" + savedSim + "\","
                        + "\"new_sim\":\"" + currentSim + "\""
                        + "}";
                post("api/device/sim-change", json);
                DeviceInfo.edit().putString("sim", currentSim).apply();

                // Show immediate notification for SIM change
                showAlertNotification("sim_change",
                    "SIM Card Changed: " + currentSim, 4);
            }

        } catch (Exception e) {
            Log.e(TAG, "checkSimChange error", e);
        }
    }

    // =========================
    // 🌐 HTTP POST
    // =========================
    private boolean post(String endpoint, String json) {
        try {
            if (!DeviceInfo.contains("token")) return false;

            String baseUrl = DeviceInfo.getString("url", "");
            if (baseUrl == null || baseUrl.isEmpty()) return false;
            if (!baseUrl.endsWith("/")) baseUrl += "/";

            OkHttpClient client = getOkHttpClient();

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"), json);

            Request request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .post(body)
                    .addHeader("X-Device-Token", DeviceInfo.getString("token", ""))
                    .addHeader("Accept", "application/json")
                    .build();

            Response response = client.newCall(request).execute();
            int code = response.code();
            response.close();
            return code >= 200 && code < 300;

        } catch (Exception e) {
            Log.e(TAG, "POST error: " + endpoint, e);
            return false;
        }
    }

    private OkHttpClient getOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                }
            };
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            return new OkHttpClient();
        }
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo net = cm.getActiveNetworkInfo();
            return net != null && net.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    // =========================
    // 🔔 NOTIFICATION
    // =========================
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking Active")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setShowWhen(false);

        return builder.build();
    }

    // =========================
    // LIFECYCLE
    // =========================
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle commands from server
        if (intent != null && intent.hasExtra("command")) {
            String command = intent.getStringExtra("command");
            handleCommand(command, intent);
        }

        return START_STICKY;
    }

    private void handleCommand(String command, Intent intent) {
        Log.d(TAG, "Received command: " + command);

        switch (command) {
            case "alarm":
                playAlarmSound();
                launchAlarmActivity();
                break;
            case "alarm_off":
                stopAlarmSound();
                FcmService.stopAlarmFromActivity(this);
                break;

            case "lock":
                lockDevice();
                break;
            case "unlock":
                unlockDevice();
                break;

            case "stealth_on":
                setStealthMode(true);
                break;
            case "stealth_off":
                setStealthMode(false);
                break;

            case "set_pin":
                String pin = intent.getStringExtra("pin");
                if (pin != null && !pin.isEmpty()) {
                    DeviceInfo.edit().putString("unlock_pin", pin).apply();
                    Log.d(TAG, "PIN set: " + pin);
                    showAlertNotification("command", "🔑 PIN code updated", 2);
                }
                break;

            case "locate":
                forceLocate();
                break;

            case "restart":
                Log.d(TAG, "Restarting service...");
                stopSelf();
                Intent restartIntent = new Intent(this, MyService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restartIntent);
                } else {
                    startService(restartIntent);
                }
                break;

            case "reboot":
                showAlertNotification("command", "Please reboot your device manually", 3);
                break;

            case "silent":
                setSilentMode(true);
                break;
            case "normal":
                setSilentMode(false);
                break;

            case "gps_on":
                openLocationSettings();
                break;
            case "gps_off":
                showAlertNotification("command", "GPS cannot be disabled remotely. Please do it manually.", 2);
                break;

            case "data_on":
                showAlertNotification("command", "Please enable mobile data in settings", 3);
                break;
            case "data_off":
                showAlertNotification("command", "Please disable mobile data in settings", 2);
                break;

            default:
                Log.d(TAG, "Unknown command: " + command);
                showAlertNotification("command", "Command received: " + command, 2);
                break;
        }
    }

    // =========================
    // 🔒 LOCK DEVICE
    // =========================
    private void lockDevice() {
        Log.d(TAG, "Lock command received");

        // Set lock flag BEFORE launching activity
        DeviceInfo.edit().putBoolean("is_locked", true).commit();

        // Launch custom lock screen activity (stays on top, blocks access)
        try {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(lockIntent);
            Log.d(TAG, "Launched LockScreenActivity with is_locked=true");
            showAlertNotification("command", "🔒 Device locked", 4);
        } catch (Exception e) {
            Log.e(TAG, "LockScreenActivity launch failed", e);
            showAlertNotification("command", "Lock command received", 3);
        }
    }

    // =========================
    // 🔓 UNLOCK DEVICE
    // =========================
    private void unlockDevice() {
        Log.d(TAG, "Unlock command received");
        try {
            // Send broadcast to dismiss LockScreenActivity
            Intent unlockIntent = new Intent("com.trackmydevice.UNLOCK");
            sendBroadcast(unlockIntent);

            // Also try to finish LockScreenActivity directly
            Intent finishIntent = new Intent(this, LockScreenActivity.class);
            finishIntent.putExtra("force_unlock", true);
            finishIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(finishIntent);

            showAlertNotification("command", "🔓 Device unlocked", 2);
            Log.d(TAG, "Unlock command executed");
        } catch (Exception e) {
            Log.e(TAG, "unlockDevice error", e);
        }
    }

    // =========================
    // 👻 STEALTH MODE
    // =========================
    private void setStealthMode(boolean enable) {
        try {
            if (enable) {
                // Only hide the LAUNCHER activity - keep services alive
                ComponentName launcher = new ComponentName(this,
                        getPackageName() + ".MainActivity");
                getPackageManager().setComponentEnabledSetting(
                    launcher,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                );
                // Also hide StartActivity
                try {
                    ComponentName start = new ComponentName(this,
                            getPackageName() + ".StartActivity");
                    getPackageManager().setComponentEnabledSetting(
                        start,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    );
                } catch (Exception ignored) {}
                DeviceInfo.edit().putBoolean("stealth_mode", true).apply();
                Log.d(TAG, "Stealth mode: ON (app hidden from drawer)");
                showAlertNotification("command", "App hidden from drawer", 2);
            } else {
                // Re-enable launcher
                ComponentName launcher = new ComponentName(this,
                        getPackageName() + ".MainActivity");
                getPackageManager().setComponentEnabledSetting(
                    launcher,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                );
                // Re-enable StartActivity
                try {
                    ComponentName start = new ComponentName(this,
                            getPackageName() + ".StartActivity");
                    getPackageManager().setComponentEnabledSetting(
                        start,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    );
                } catch (Exception ignored) {}
                DeviceInfo.edit().putBoolean("stealth_mode", false).apply();
                Log.d(TAG, "Stealth mode: OFF (app visible)");
                showAlertNotification("command", "App visible in drawer", 2);

                // Launch app to show it
                Intent showIntent = new Intent(this, MainActivity.class);
                showIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(showIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "setStealthMode error", e);
        }
    }

    // =========================
    // 📍 FORCE LOCATE
    // =========================
    private void forceLocate() {
        Log.d(TAG, "Force locate command");
        if (locationManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= 23 &&
                        ContextCompat.checkSelfPermission(this,
                                android.Manifest.permission.ACCESS_FINE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED) {
                    Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastKnown == null) {
                        lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                    if (lastKnown != null) {
                        handleLocation(lastKnown);
                        Log.d(TAG, "Force location sent: " + lastKnown.getLatitude() + ", " + lastKnown.getLongitude());
                    } else {
                        Log.d(TAG, "No last known location available");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Force locate error", e);
            }
        }
    }

    // =========================
    // 🔇 SILENT MODE
    // =========================
    private void setSilentMode(boolean enable) {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) return;

            if (enable) {
                // Lower volume to minimum instead of changing ringer mode
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
                int minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_RING);
                audioManager.setStreamVolume(AudioManager.STREAM_RING, minVolume, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, minVolume, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, minVolume, 0);

                // Also try vibrate-only mode (less restricted than silent)
                try {
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                } catch (Exception ignored) {}

                Log.d(TAG, "Silent mode enabled - volume minimized");
                showAlertNotification("command", "🔇 Silent mode activated", 2);
            } else {
                // Restore volume to half
                int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
                int halfVolume = maxVolume / 2;
                audioManager.setStreamVolume(AudioManager.STREAM_RING, halfVolume, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, halfVolume, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, halfVolume, 0);

                try {
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                } catch (Exception ignored) {}

                Log.d(TAG, "Normal mode restored");
                showAlertNotification("command", "🔊 Normal mode restored", 2);
            }
        } catch (Exception e) {
            Log.e(TAG, "setSilentMode error", e);
            // Fallback: just show notification
            showAlertNotification("command",
                enable ? "🔇 Silent mode requested" : "🔊 Normal mode requested", 2);
        }
    }

    // =========================
    // ⚙️ OPEN LOCATION SETTINGS
    // =========================
    private void openLocationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Location settings opened");
            showAlertNotification("command", "⚙️ Location settings opened", 3);
        } catch (Exception e) {
            Log.e(TAG, "openLocationSettings error", e);
        }
    }

    // =========================
    // 🔔 LAUNCH ALARM ACTIVITY
    // =========================
    private void launchAlarmActivity() {
        try {
            Intent alarmIntent = new Intent(this, AlarmActivity.class);
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(alarmIntent);
        } catch (Exception e) {
            Log.e(TAG, "launchAlarmActivity error", e);
        }
    }

    private void playAlarmSound() {
        try {
            Log.d(TAG, "Playing alarm...");

            // Stop any existing alarm
            stopAlarmSound();

            // Show high priority notification first
            showAlarmNotification();

            // Launch alarm activity for full screen alarm
            try {
                Intent alarmIntent = new Intent(this, AlarmActivity.class);
                alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(alarmIntent);
            } catch (Exception e) {
                Log.e(TAG, "AlarmActivity launch error", e);
            }

            // Play notification sound
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());

            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }

            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Also vibrate continuously
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 1000, 500, 1000, 500, 1000}, 0));
                } else {
                    vibrator.vibrate(new long[]{0, 1000, 500, 1000, 500, 1000}, 0);
                }
            }

            Log.d(TAG, "Alarm started!");
        } catch (Exception e) {
            Log.e(TAG, "playAlarmSound error", e);
        }
    }

    private void stopAlarmSound() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
            if (vibrator != null) {
                vibrator.cancel();
            }
            Log.d(TAG, "Alarm stopped!");
        } catch (Exception e) {
            Log.e(TAG, "stopAlarmSound error", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }

        stopAlarmSound();

        try {
            stopForeground(true);
        } catch (Exception e) {
            Log.e(TAG, "stopForeground error", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}