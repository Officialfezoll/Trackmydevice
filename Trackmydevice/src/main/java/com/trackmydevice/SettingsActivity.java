package com.trackmydevice;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("DeviceInfo", MODE_PRIVATE);

        EditText ownerName = findViewById(R.id.ownerName);
        EditText ownerPhone = findViewById(R.id.ownerPhone);
        EditText ownerMessage = findViewById(R.id.ownerMessage);
        EditText unlockPin = findViewById(R.id.unlockPin);
        TextView pinStatus = findViewById(R.id.pinStatus);
        Button btnSave = findViewById(R.id.btnSave);

        // Load saved values
        ownerName.setText(prefs.getString("owner_name", ""));
        ownerPhone.setText(prefs.getString("owner_phone", ""));
        ownerMessage.setText(prefs.getString("owner_message", ""));

        String savedPin = prefs.getString("unlock_pin", "");
        if (!savedPin.isEmpty()) {
            unlockPin.setHint("PIN set (enter new to change)");
            pinStatus.setText("PIN is set");
        }

        btnSave.setOnClickListener(v -> {
            String name = ownerName.getText().toString().trim();
            String phone = ownerPhone.getText().toString().trim();
            String message = ownerMessage.getText().toString().trim();
            String pin = unlockPin.getText().toString().trim();

            SharedPreferences.Editor editor = prefs.edit();

            if (!name.isEmpty()) editor.putString("owner_name", name);
            if (!phone.isEmpty()) editor.putString("owner_phone", phone);
            if (!message.isEmpty()) editor.putString("owner_message", message);

            if (!pin.isEmpty()) {
                if (pin.length() < 4) {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
                    return;
                }
                editor.putString("unlock_pin", pin);
                pinStatus.setText("PIN updated!");
            }

            editor.apply();
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
