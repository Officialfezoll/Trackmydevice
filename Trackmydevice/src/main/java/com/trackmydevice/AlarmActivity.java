package com.trackmydevice;

import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class AlarmActivity extends AppCompatActivity {

    private static final String TAG = "AlarmActivity";
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private boolean alarmStopped = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if this is a stop command from FCM
        if (getIntent() != null && getIntent().getBooleanExtra("stop_alarm", false)) {
            forceStopAll();
            finish();
            return;
        }

        // Wake up screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_alarm);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Start alarm sound
        playAlarmSound();

        // Setup stop button
        Button stopBtn = findViewById(R.id.btnStopAlarm);
        stopBtn.setOnClickListener(v -> stopAndFinish());

        // Allow tap anywhere to stop
        findViewById(android.R.id.content).setOnClickListener(v -> stopAndFinish());
    }

    private void playAlarmSound() {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());

            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                            new long[]{0, 1000, 500, 1000, 500, 1000}, 0));
                } else {
                    vibrator.vibrate(new long[]{0, 1000, 500, 1000, 500, 1000}, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "playAlarmSound error", e);
        }
    }

    private void stopAndFinish() {
        if (alarmStopped) return;
        alarmStopped = true;

        try {
            // Stop local MediaPlayer
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            if (vibrator != null) {
                vibrator.cancel();
            }

            // Stop FCM static alarm player too
            FcmService.stopAlarmFromActivity(this);

            // Tell MyService to stop
            Intent serviceIntent = new Intent(this, MyService.class);
            serviceIntent.putExtra("command", "alarm_off");
            startService(serviceIntent);

            Log.d(TAG, "Alarm stopped and activity finishing");
        } catch (Exception e) {
            Log.e(TAG, "stopAndFinish error", e);
        }

        finish();
    }

    private void forceStopAll() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            if (vibrator != null) vibrator.cancel();

            FcmService.stopAlarmFromActivity(this);

            Intent serviceIntent = new Intent(this, MyService.class);
            serviceIntent.putExtra("command", "alarm_off");
            startService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "forceStopAll error", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!alarmStopped) stopAndFinish();
    }

    @Override
    public void onBackPressed() {
        stopAndFinish();
    }
}
