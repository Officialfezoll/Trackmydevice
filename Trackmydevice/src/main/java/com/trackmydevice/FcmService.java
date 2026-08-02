package com.trackmydevice;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FcmService extends FirebaseMessagingService {

    private static final String TAG = "FcmService";
    private static final String ALERT_CHANNEL_ID = "FcmAlertChannel";
    private static MediaPlayer alarmPlayer;
    private static Vibrator alarmVibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        createAlertChannel();
    }

    private void createAlertChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel channel = new NotificationChannel(
                    ALERT_CHANNEL_ID, "FCM Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Firebase push notification alerts");
            channel.enableVibration(true);
            channel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            );
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Token: " + token);

        getSharedPreferences("DeviceInfo", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();

        sendTokenToServer(token, 0);
    }

    private void sendTokenToServer(String token, int retryCount) {
        final int maxRetries = 5;
        final int retryDelay = 5000;

        new Thread(() -> {
            try {
                String baseUrl = getSharedPreferences("DeviceInfo", MODE_PRIVATE)
                        .getString("url", "");
                String deviceToken = getSharedPreferences("DeviceInfo", MODE_PRIVATE)
                        .getString("token", "");

                if (deviceToken == null || deviceToken.isEmpty()) {
                    if (retryCount < maxRetries) {
                        Log.d(TAG, "Device not registered, retry " + (retryCount + 1) + "/" + maxRetries);
                        Thread.sleep(retryDelay);
                        sendTokenToServer(token, retryCount + 1);
                    }
                    return;
                }

                if (baseUrl == null || baseUrl.isEmpty()) return;
                if (!baseUrl.endsWith("/")) baseUrl += "/";

                OkHttpClient client = getOkHttpClient();
                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        "{\"fcm_token\":\"" + token + "\"}");

                Request request = new Request.Builder()
                        .url(baseUrl + "api/device/fcm-token")
                        .post(body)
                        .addHeader("X-Device-Token", deviceToken)
                        .build();

                Response response = client.newCall(request).execute();
                int code = response.code();
                response.close();
                Log.d(TAG, "Token sent to server, response: " + code);

                if (code != 200 && retryCount < maxRetries) {
                    Thread.sleep(retryDelay);
                    sendTokenToServer(token, retryCount + 1);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to send token", e);
                if (retryCount < maxRetries) {
                    try { Thread.sleep(retryDelay); } catch (InterruptedException ignored) {}
                    sendTokenToServer(token, retryCount + 1);
                }
            }
        }).start();
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

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM Message received from: " + remoteMessage.getFrom());

        Map<String, String> data = remoteMessage.getData();
        if (data.isEmpty()) {
            // Handle notification-only messages
            RemoteMessage.Notification n = remoteMessage.getNotification();
            if (n != null) {
                showNotification(n.getTitle() != null ? n.getTitle() : "TrackMyDevice",
                        n.getBody() != null ? n.getBody() : "", null);
            }
            return;
        }

        String command = data.get("command");
        String title = data.get("title");
        String body = data.get("body");

        if (title == null || title.isEmpty()) title = "TrackMyDevice";
        if (body == null || body.isEmpty()) body = "New command received";

        // Execute command immediately via FCM (real-time!)
        if (command != null && !command.isEmpty()) {
            Log.d(TAG, "FCM Command received: " + command);
            executeCommand(command, data);
        }

        // Show notification with result
        String resultBody = body;
        switch (command != null ? command : "") {
            case "alarm": resultBody = "Alarm triggered! Find your device!"; break;
            case "alarm_off": resultBody = "Alarm stopped."; break;
            case "lock": resultBody = "Device locked remotely."; break;
            case "unlock": resultBody = "Device unlocked."; break;
            case "locate": resultBody = "Forcing location update..."; break;
            case "restart": resultBody = "Service restarting..."; break;
            case "silent": resultBody = "Silent mode activated."; break;
            case "normal": resultBody = "Normal mode restored."; break;
            case "stealth_on": resultBody = "App hidden from drawer."; break;
            case "stealth_off": resultBody = "App visible in drawer."; break;
            case "set_pin": resultBody = "PIN code updated."; break;
        }
        showNotification(title, resultBody, command);
    }

    private void executeCommand(String command, Map<String, String> data) {
        Log.d(TAG, "Executing FCM command: " + command);

        // Execute ALL commands DIRECTLY in FCM service (works even when app is killed)
        switch (command) {
            case "alarm":
                playAlarmNow();
                launchLockScreen(); // Show alarm screen
                return;
            case "alarm_off":
                stopAlarmNow();
                // Dismiss alarm activity
                try {
                    Intent offIntent = new Intent(this, AlarmActivity.class);
                    offIntent.putExtra("stop_alarm", true);
                    offIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(offIntent);
                } catch (Exception e) {}
                return;

            case "lock":
                getSharedPreferences("DeviceInfo", MODE_PRIVATE).edit()
                        .putBoolean("is_locked", true).commit();
                launchLockScreen();
                return;

            case "unlock":
                getSharedPreferences("DeviceInfo", MODE_PRIVATE).edit()
                        .putBoolean("is_locked", false).commit();
                // Dismiss lock screen
                try {
                    Intent unlockIntent = new Intent(this, LockScreenActivity.class);
                    unlockIntent.putExtra("force_unlock", true);
                    unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(unlockIntent);
                } catch (Exception e) {}
                // Also send broadcast for any listening activity
                try {
                    sendBroadcast(new Intent("com.trackmydevice.UNLOCK"));
                } catch (Exception e) {}
                return;

            case "silent":
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        int min = am.getStreamMinVolume(AudioManager.STREAM_RING);
                        am.setStreamVolume(AudioManager.STREAM_RING, min, 0);
                        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, min, 0);
                        am.setStreamVolume(AudioManager.STREAM_ALARM, min, 0);
                    }
                } catch (Exception e) {}
                return;

            case "normal":
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        int max = am.getStreamMaxVolume(AudioManager.STREAM_RING);
                        am.setStreamVolume(AudioManager.STREAM_RING, max / 2, 0);
                        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, max / 2, 0);
                        am.setStreamVolume(AudioManager.STREAM_ALARM, max / 2, 0);
                    }
                } catch (Exception e) {}
                return;

            case "restart":
                // Restart the service
                try {
                    Intent restartIntent = new Intent(this, MyService.class);
                    stopService(restartIntent);
                    Thread.sleep(500);
                    Intent startIntent = new Intent(this, MyService.class);
                    startIntent.putExtra("command", "restart");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(startIntent);
                    } else {
                        startService(startIntent);
                    }
                } catch (Exception e) {}
                return;

            case "locate":
                // Force location update - start service with command
                try {
                    Intent locateIntent = new Intent(this, MyService.class);
                    locateIntent.putExtra("command", "locate");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(locateIntent);
                    } else {
                        startService(locateIntent);
                    }
                } catch (Exception e) {}
                return;

            case "set_pin":
                String pin = data.get("pin");
                if (pin != null && !pin.isEmpty()) {
                    getSharedPreferences("DeviceInfo", MODE_PRIVATE).edit()
                            .putString("unlock_pin", pin).apply();
                }
                return;

            case "stealth_on":
                try {
                    // Only hide the LAUNCHER activity, not the whole app
                    // This keeps FcmService, MyService, and StealthReceiver alive
                    ComponentName launcher = new ComponentName(this,
                            getPackageName() + ".MainActivity");
                    getPackageManager().setComponentEnabledSetting(launcher,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);

                    // Also try to hide the StartActivity
                    try {
                        ComponentName start = new ComponentName(this,
                                getPackageName() + ".StartActivity");
                        getPackageManager().setComponentEnabledSetting(start,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP);
                    } catch (Exception ignored) {}

                    getSharedPreferences("DeviceInfo", MODE_PRIVATE).edit()
                            .putBoolean("stealth_mode", true).apply();
                    Log.d(TAG, "Stealth mode ON - app hidden from drawer");
                } catch (Exception e) {
                    Log.e(TAG, "stealth_on error", e);
                }
                return;

            case "stealth_off":
                try {
                    // Re-enable the launcher activity
                    ComponentName launcher = new ComponentName(this,
                            getPackageName() + ".MainActivity");
                    getPackageManager().setComponentEnabledSetting(launcher,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);

                    // Re-enable StartActivity
                    try {
                        ComponentName start = new ComponentName(this,
                                getPackageName() + ".StartActivity");
                        getPackageManager().setComponentEnabledSetting(start,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                PackageManager.DONT_KILL_APP);
                    } catch (Exception ignored) {}

                    getSharedPreferences("DeviceInfo", MODE_PRIVATE).edit()
                            .putBoolean("stealth_mode", false).apply();
                    Log.d(TAG, "Stealth mode OFF - app visible in drawer");

                    // Launch the app to show it's back
                    Intent showIntent = new Intent(this, MainActivity.class);
                    showIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(showIntent);
                } catch (Exception e) {
                    Log.e(TAG, "stealth_off error", e);
                }
                return;

            default:
                // Unknown command - try service
                try {
                    Intent defIntent = new Intent(this, MyService.class);
                    defIntent.putExtra("command", command);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(defIntent);
                    } else {
                        startService(defIntent);
                    }
                } catch (Exception e) {}
                return;
        }
    }

    private void playAlarmNow() {
        try {
            // Stop any existing alarm first
            stopAlarmNow();

            alarmPlayer = new MediaPlayer();
            alarmPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());

            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            alarmPlayer.setDataSource(this, alarmUri);
            alarmPlayer.setLooping(true);
            alarmPlayer.prepare();
            alarmPlayer.start();

            // Vibrate
            alarmVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (alarmVibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    alarmVibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 1000, 500, 1000, 500, 1000}, 0));
                } else {
                    alarmVibrator.vibrate(new long[]{0, 1000, 500, 1000, 500, 1000}, 0);
                }
            }

            getSharedPreferences("DeviceInfo", MODE_PRIVATE).edit()
                    .putBoolean("alarm_playing", true).apply();

            Log.d(TAG, "Alarm started via FCM!");
        } catch (Exception e) {
            Log.e(TAG, "playAlarmNow error", e);
        }
    }

    private void stopAlarmNow() {
        try {
            // Stop MediaPlayer
            if (alarmPlayer != null) {
                if (alarmPlayer.isPlaying()) {
                    alarmPlayer.stop();
                }
                alarmPlayer.release();
                alarmPlayer = null;
            }

            // Stop vibration
            if (alarmVibrator != null) {
                alarmVibrator.cancel();
                alarmVibrator = null;
            }

            getSharedPreferences("DeviceInfo", MODE_PRIVATE).edit()
                    .putBoolean("alarm_playing", false).apply();

            Log.d(TAG, "Alarm stopped via FCM!");
        } catch (Exception e) {
            Log.e(TAG, "stopAlarmNow error", e);
        }
    }

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

    private void stopAlarmActivity() {
        try {
            Intent intent = new Intent(this, AlarmActivity.class);
            intent.putExtra("stop_alarm", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "stopAlarmActivity error", e);
        }
    }

    private void restartService() {
        try {
            Intent serviceIntent = new Intent(this, MyService.class);
            stopService(serviceIntent);
            Thread.sleep(1000);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "Service restarted via FCM!");
        } catch (Exception e) {
            Log.e(TAG, "restartService error", e);
        }
    }

    private void launchLockScreen() {
        try {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(lockIntent);
            Log.d(TAG, "LockScreenActivity launched via FCM");
        } catch (Exception e) {
            Log.e(TAG, "launchLockScreen error", e);
        }
    }

    private void showNotification(String title, String body, String command) {
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        int icon = android.R.drawable.ic_dialog_info;
        if ("alarm".equals(command)) icon = android.R.drawable.ic_dialog_alert;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if ("alarm".equals(command)) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    /** Called by AlarmActivity when user taps STOP */
    public static void stopAlarmFromActivity(Context context) {
        if (alarmPlayer != null) {
            try {
                if (alarmPlayer.isPlaying()) alarmPlayer.stop();
                alarmPlayer.release();
            } catch (Exception ignored) {}
            alarmPlayer = null;
        }
        if (alarmVibrator != null) {
            alarmVibrator.cancel();
            alarmVibrator = null;
        }
        context.getSharedPreferences("DeviceInfo", MODE_PRIVATE).edit()
                .putBoolean("alarm_playing", false).apply();
        Log.d("FcmService", "Alarm stopped from activity");
    }
}
