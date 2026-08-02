package com.trackmydevice;

import android.Manifest;
import android.animation.*;
import android.app.*;
import android.app.Activity;
import android.content.*;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.button.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.text.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.*;
import org.json.*;
import java.util.UUID;
import  android.provider.Settings;

public class StartActivity extends AppCompatActivity {
	
	private Timer _timer = new Timer();
	
	private String info = "";
	private HashMap<String, Object> infoData = new HashMap<>();
	private HashMap<String, Object> LogedDeviceInfo = new HashMap<>();
	private HashMap<String, Object> UpdateHeader = new HashMap<>();
	private HashMap<String, Object> LocationUpdate = new HashMap<>();
	private double batteryPct = 0;
	private double level = 0;
	private double scale = 0;
	private double status = 0;
	private HashMap<String, Object> batteryInfo = new HashMap<>();
	private boolean isCharging = false;
	
	private LinearLayout linear1;
	private LinearLayout linear2;
	private LinearLayout linear3;
	private TextView textview1;
	private TextView textview2;
	private MaterialButton materialbutton1;
	private TextView textview3;
	private ImageButton btnInfo;
	
	private LocationManager Location;
	private LocationListener _Location_location_listener;
	private RequestNetwork Request;
	private RequestNetwork.RequestListener _Request_request_listener;
	private SharedPreferences DeviceInfo;
	private TimerTask Timer;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.start);
		initialize(_savedInstanceState);

		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
			ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1000);
		} else {
			initializeLogic();
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == 1000) {
			initializeLogic();
		}
	}
	
	private void initialize(Bundle _savedInstanceState) {
		linear1 = findViewById(R.id.linear1);
		linear2 = findViewById(R.id.linear2);
		linear3 = findViewById(R.id.linear3);
		textview1 = findViewById(R.id.textview1);
		textview2 = findViewById(R.id.textview2);
		materialbutton1 = findViewById(R.id.materialbutton1);
		textview3 = findViewById(R.id.textview3);
		btnInfo = findViewById(R.id.btn_info);
		try {
			Location = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		} catch (Exception e) {
			Location = null;
		}
		Request = new RequestNetwork(this);
		DeviceInfo = getSharedPreferences("DeviceInfo", Activity.MODE_PRIVATE);

		btnInfo.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				showProjectInfo();
			}
		});

		materialbutton1.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				String url = DeviceInfo.getString("url", "https://mytracker.ink/");
				if (!url.endsWith("/")) url += "/";
				Toast.makeText(StartActivity.this, "Connecting to: " + url, Toast.LENGTH_SHORT).show();
				Request.setParams(infoData, RequestNetworkController.REQUEST_BODY);
				Request.startRequestNetwork(RequestNetworkController.POST, url + "api/device/login", "login", _Request_request_listener);
			}
		});
		
		_Location_location_listener = new LocationListener() {
			@Override
			public void onLocationChanged(Location _param1) {
				final double _lat = _param1.getLatitude();
				final double _lng = _param1.getLongitude();
				final double _acc = _param1.getAccuracy();
				if (DeviceInfo.contains("token")) {
					UpdateHeader.put("X-Device-Token", DeviceInfo.getString("token", ""));
					Request.setHeaders(UpdateHeader);
					LocationUpdate.put("lat", String.valueOf(_lat));
					LocationUpdate.put("lng", String.valueOf(_lng));
					LocationUpdate.put("accuracy", String.valueOf(_acc));
					Request.setParams(LocationUpdate, RequestNetworkController.REQUEST_BODY);
					Request.startRequestNetwork(RequestNetworkController.POST, getApiUrl() + "device/location", "location", _Request_request_listener);
				}
			}
			
			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {
			}
			
			@Override
			public void onProviderEnabled(String provider) {
			}
			
			@Override
			public void onProviderDisabled(String provider) {
			}
		};
		
		_Request_request_listener = new RequestNetwork.RequestListener() {
			@Override
			public void onResponse(String _param1, String _param2, HashMap<String, Object> _param3) {
				final String _tag = _param1;
				final String _response = _param2;
				final HashMap<String, Object> _responseHeaders = _param3;
				if (_tag.equals("login")) {
					try{
						LogedDeviceInfo = new Gson().fromJson(_response, new TypeToken<HashMap<String, Object>>(){}.getType());
						if (LogedDeviceInfo.containsKey("token")) {
							DeviceInfo.edit().putString("token", LogedDeviceInfo.get("token").toString()).commit();
						}
						if (LogedDeviceInfo.containsKey("short_code")) {
							DeviceInfo.edit().putString("short_code", LogedDeviceInfo.get("short_code").toString()).commit();
							textview2.setText("Connection Code");
							materialbutton1.setVisibility(View.GONE);
							textview3.setVisibility(View.VISIBLE);
							textview3.setText(LogedDeviceInfo.get("short_code").toString());
							Toast.makeText(StartActivity.this, "Connected! Code: " + LogedDeviceInfo.get("short_code").toString(), Toast.LENGTH_SHORT).show();
							if (ContextCompat.checkSelfPermission(StartActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
								Location.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, _Location_location_listener);
							}
							// Send FCM token after successful login
							sendFcmTokenToServer();
						} else {
							Toast.makeText(StartActivity.this, "Response: " + _response, Toast.LENGTH_LONG).show();
						}
					}catch(Exception e){
						Toast.makeText(StartActivity.this, "Parse Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
					}
				}
			}

			@Override
			public void onErrorResponse(String _param1, String _param2) {
				final String _tag = _param1;
				final String _message = _param2;
				runOnUiThread(() -> {
					Toast.makeText(StartActivity.this, "Error: " + _message, Toast.LENGTH_LONG).show();
				});
			}
		};
	}

	private void sendFcmTokenToServer() {
		new Thread(() -> {
			try {
				Thread.sleep(2000); // Wait for FCM token
				String fcmToken = DeviceInfo.getString("fcm_token", null);
				String deviceToken = DeviceInfo.getString("token", null);
				String baseUrl = DeviceInfo.getString("url", "https://mytracker.ink");

				if (fcmToken == null || deviceToken == null) {
					android.util.Log.d("FCM", "Tokens not ready, retrying...");
					sendFcmTokenToServer(); // Retry
					return;
				}

				if (!baseUrl.endsWith("/")) baseUrl += "/";

				java.net.URL url = new java.net.URL(baseUrl + "api/device/fcm-token");
				java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setRequestProperty("X-Device-Token", deviceToken);
				conn.setDoOutput(true);

				String json = "{\"fcm_token\":\"" + fcmToken + "\"}";
				java.io.OutputStream os = conn.getOutputStream();
				os.write(json.getBytes());
				os.close();

				int code = conn.getResponseCode();
				android.util.Log.d("FCM", "Token sent after login, response: " + code);
				conn.disconnect();
			} catch (Exception e) {
				android.util.Log.e("FCM", "Failed to send FCM token", e);
			}
		}).start();
	}

	private void initializeLogic() {
		DeviceInfo.edit().putString("url", "https://mytracker.ink").commit();
		linear1.setBackground(new GradientDrawable(GradientDrawable.Orientation.BR_TL, new int[] {0xFF1565C0,0xFF0D47A1}));
		
		infoData.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
		infoData.put("brand", Build.BRAND);
		infoData.put("model", Build.DEVICE);
		infoData.put("os_version", Build.VERSION.RELEASE);
		infoData.put("uuid", android.provider.Settings.Secure.getString(
		        getContentResolver(),
		        android.provider.Settings.Secure.ANDROID_ID
		));
		textview3.setVisibility(View.GONE);
		if (DeviceInfo.contains("token")) {
			DeviceInfo.edit().putString("X-Device-Token", DeviceInfo.getString("token", "")).commit();
			if (DeviceInfo.contains("short_code")) {
				// Check & request permissions
				if (Build.VERSION.SDK_INT >= 23) {
					    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) 
					        != PackageManager.PERMISSION_GRANTED) {
						        requestPermissions(new String[]{
							            Manifest.permission.ACCESS_FINE_LOCATION,
							            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
							            Manifest.permission.READ_PHONE_STATE,
							            Manifest.permission.POST_NOTIFICATIONS
							        }, 100);
						        return; // Wait for callback before starting service
						    }
				}
				// Now start your service
				startService(new Intent(this, MyService.class));
				textview2.setText("Connection Code");
				materialbutton1.setVisibility(View.GONE);
				textview3.setVisibility(View.VISIBLE);
				textview3.setText(DeviceInfo.getString("short_code", ""));
				if (Location != null && ContextCompat.checkSelfPermission(StartActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
					try {
						Location.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, _Location_location_listener);
					} catch (Exception e) {
						// GPS not available in Waydroid
					}
				}
				Timer = new TimerTask() {
					@Override
					public void run() {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								android.content.IntentFilter ifilter = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
								android.content.Intent batteryStatus = registerReceiver(null, ifilter);
								
								level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
								scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
								
								batteryPct = (int) ((level / (float) scale) * 100);
								
								status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
								isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
								        || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
								batteryInfo.put("level", String.valueOf((long)(batteryPct)));
								if (isCharging) {
									batteryInfo.put("charging", String.valueOf((long)(1)));
								}
								else {
									batteryInfo.put("charging", String.valueOf((long)(0)));
								}
								UpdateHeader.put("X-Device-Token", DeviceInfo.getString("token", ""));
								Request.setHeaders(UpdateHeader);
								Request.setParams(batteryInfo, RequestNetworkController.REQUEST_BODY);
								Request.startRequestNetwork(RequestNetworkController.POST, getApiUrl() + "device/battery", "battery", _Request_request_listener);
							}
						});
					}
				};
				_timer.scheduleAtFixedRate(Timer, (int)(0), (int)(15000));
			}
		}
	}

	private void showProjectInfo() {
		String info = "📱 TrackMyDevice\n\n" +
			"A GPS tracking system that allows users to track and monitor their Android devices in real-time.\n\n" +
			"Features:\n" +
			"• Real-time location tracking\n" +
			"• Battery monitoring\n" +
			"• SIM card change alerts\n" +
			"• SOS/Alarm commands\n" +
			"• Direct P2P communication\n\n" +
			"━━━━━━━━━━━━━━━━━━━━\n\n" +
			"Final Project for Bachelor Degree in Computer Science\n" +
			"At Ruaha Catholic University (RUCU)\n\n" +
			"Built by:\n" +
			"• Fadhili Michael Clever\n" +
			"• Said Haruna Shabani\n" +
			"• Benjamini Fredy Nkane";

		new AlertDialog.Builder(this)
			.setTitle("About Project")
			.setMessage(info)
			.setPositiveButton("OK", null)
			.show();
	}

	private String getApiUrl() {
		String url = DeviceInfo.getString("url", "https://mytracker.ink/");
		if (!url.endsWith("/")) url += "/";
		return url + "api/";
	}

	@Deprecated
	public void showMessage(String _s) {
		Toast.makeText(getApplicationContext(), _s, Toast.LENGTH_SHORT).show();
	}
	
	@Deprecated
	public int getLocationX(View _v) {
		int _location[] = new int[2];
		_v.getLocationInWindow(_location);
		return _location[0];
	}
	
	@Deprecated
	public int getLocationY(View _v) {
		int _location[] = new int[2];
		_v.getLocationInWindow(_location);
		return _location[1];
	}
	
	@Deprecated
	public int getRandom(int _min, int _max) {
		Random random = new Random();
		return random.nextInt(_max - _min + 1) + _min;
	}
	
	@Deprecated
	public ArrayList<Double> getCheckedItemPositionsToArray(ListView _list) {
		ArrayList<Double> _result = new ArrayList<Double>();
		SparseBooleanArray _arr = _list.getCheckedItemPositions();
		for (int _iIdx = 0; _iIdx < _arr.size(); _iIdx++) {
			if (_arr.valueAt(_iIdx))
			_result.add((double)_arr.keyAt(_iIdx));
		}
		return _result;
	}
	
	@Deprecated
	public float getDip(int _input) {
		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, _input, getResources().getDisplayMetrics());
	}
	
	@Deprecated
	public int getDisplayWidthPixels() {
		return getResources().getDisplayMetrics().widthPixels;
	}
	
	@Deprecated
	public int getDisplayHeightPixels() {
		return getResources().getDisplayMetrics().heightPixels;
	}
}