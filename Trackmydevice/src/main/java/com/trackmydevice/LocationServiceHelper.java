package com.trackmydevice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Advanced location service with multi-source fallback
 * Supports: GPS > Network > WiFi-based positioning
 */
public class LocationServiceHelper {

    private static final String TAG = "LocationHelper";
    private static final long GPS_MIN_TIME = 10000; // 10 seconds
    private static final float GPS_MIN_DISTANCE = 10; // 10 meters
    private static final long NETWORK_MIN_TIME = 15000; // 15 seconds
    private static final float NETWORK_MIN_DISTANCE = 15; // 15 meters
    private static final long LOCATION_TIMEOUT = 60000; // 1 minute

    private Context context;
    private LocationManager locationManager;
    private WifiManager wifiManager;

    private Location currentBestLocation;
    private long lastLocationTime = 0;

    private LocationListener gpsListener;
    private LocationListener networkListener;

    private OnLocationCallback callback;

    public interface OnLocationCallback {
        void onLocationReceived(Location location, String source);
        void onLocationError(String error);
    }

    public LocationServiceHelper(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    public void setCallback(OnLocationCallback callback) {
        this.callback = callback;
    }

    /**
     * Start listening for location updates from all sources
     */
    public void startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted");
            return;
        }

        setupGpsListener();
        setupNetworkListener();

        // Try GPS first
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        GPS_MIN_TIME,
                        GPS_MIN_DISTANCE,
                        gpsListener
                );
                Log.d(TAG, "GPS provider started");
            } catch (SecurityException e) {
                Log.e(TAG, "GPS permission error", e);
            }
        }

        // Network as fallback
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        NETWORK_MIN_TIME,
                        NETWORK_MIN_DISTANCE,
                        networkListener
                );
                Log.d(TAG, "Network provider started");
            } catch (SecurityException e) {
                Log.e(TAG, "Network permission error", e);
            }
        }

        // Get last known location immediately
        getLastKnownLocation();
    }

    /**
     * Stop all location listeners
     */
    public void stopLocationUpdates() {
        if (gpsListener != null) {
            locationManager.removeUpdates(gpsListener);
        }
        if (networkListener != null) {
            locationManager.removeUpdates(networkListener);
        }
    }

    /**
     * Request single location update (for on-demand locate command)
     */
    public void requestSingleUpdate() {
        if (!hasLocationPermission()) {
            if (callback != null) callback.onLocationError("Permission denied");
            return;
        }

        try {
            // Try GPS first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(
                        LocationManager.GPS_PROVIDER,
                        new LocationListener() {
                            @Override
                            public void onLocationChanged(@NonNull Location location) {
                                handleLocation(location, "gps");
                            }

                            @Override
                            public void onStatusChanged(String provider, int status, Bundle extras) {}

                            @Override
                            public void onProviderEnabled(@NonNull String provider) {}

                            @Override
                            public void onProviderDisabled(@NonNull String provider) {}
                        },
                        null
                );
                return;
            }

            // Fallback to network
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(
                        LocationManager.NETWORK_PROVIDER,
                        new LocationListener() {
                            @Override
                            public void onLocationChanged(@NonNull Location location) {
                                handleLocation(location, "network");
                            }

                            @Override
                            public void onStatusChanged(String provider, int status, Bundle extras) {}

                            @Override
                            public void onProviderEnabled(@NonNull String provider) {}

                            @Override
                            public void onProviderDisabled(@NonNull String provider) {}
                        },
                        null
                );
                return;
            }

            // Last resort: try WiFi-based location
            getWifiLocation();

        } catch (SecurityException e) {
            Log.e(TAG, "Permission error in requestSingleUpdate", e);
            if (callback != null) callback.onLocationError("Permission error");
        }
    }

    /**
     * Get last known location from any provider
     */
    public void getLastKnownLocation() {
        if (!hasLocationPermission()) return;

        try {
            Location gpsLocation = null;
            Location networkLocation = null;

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            // Choose best location
            Location best = chooseBestLocation(gpsLocation, networkLocation);

            if (best != null && isLocationFresh(best)) {
                handleLocation(best, getLocationSource(best));
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Permission error getting last known location", e);
        }
    }

    /**
     * WiFi-based location estimation using Google Geolocation API
     */
    public void getWifiLocation() {
        if (!hasWifiPermission()) {
            Log.w(TAG, "WiFi permission not granted");
            return;
        }

        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            Log.w(TAG, "WiFi is disabled");
            return;
        }

        try {
            List<ScanResult> scanResults = wifiManager.getScanResults();
            if (scanResults == null || scanResults.isEmpty()) {
                Log.w(TAG, "No WiFi networks found");
                return;
            }

            // Sort by signal strength and take top 10
            List<ScanResult> strongNetworks = new ArrayList<>(scanResults);
            Collections.sort(strongNetworks, (a, b) -> Integer.compare(b.level, a.level));
            if (strongNetworks.size() > 10) {
                strongNetworks = strongNetworks.subList(0, 10);
            }

            // Build WiFi access points JSON
            JSONArray wifiPoints = new JSONArray();
            for (ScanResult result : strongNetworks) {
                JSONObject ap = new JSONObject();
                try {
                    ap.put("macAddress", result.BSSID);
                    ap.put("signalStrength", result.level);
                    ap.put("channel", getWifiChannel(result.frequency));
                    wifiPoints.put(ap);
                } catch (Exception e) {
                    Log.e(TAG, "Error building WiFi point", e);
                }
            }

            Log.d(TAG, "Found " + wifiPoints.length() + " WiFi networks");

            // Note: For actual WiFi geolocation, you would send this to
            // Google Geolocation API or similar service
            // For now, we'll use a fallback with just the network info

            if (callback != null && currentBestLocation == null) {
                callback.onLocationError("WiFi location requires internet for API call");
            }

        } catch (Exception e) {
            Log.e(TAG, "WiFi location error", e);
        }
    }

    private void setupGpsListener() {
        gpsListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                handleLocation(location, "gps");
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d(TAG, "GPS status: " + status);
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
                Log.d(TAG, "GPS provider enabled");
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                Log.w(TAG, "GPS provider disabled");
            }
        };
    }

    private void setupNetworkListener() {
        networkListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // Only use network location if GPS hasn't given us a recent fix
                if (currentBestLocation == null ||
                        SystemClock.elapsedRealtime() - lastLocationTime > 30000) {
                    handleLocation(location, "network");
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(@NonNull String provider) {}

            @Override
            public void onProviderDisabled(@NonNull String provider) {}
        };
    }

    private void handleLocation(Location location, String source) {
        if (location == null) return;

        // Check if this is better than current
        Location best = chooseBestLocation(currentBestLocation, location);

        if (best != currentBestLocation) {
            currentBestLocation = best;
            lastLocationTime = SystemClock.elapsedRealtime();

            Log.d(TAG, String.format("New best location: %s (%.6f, %.6f) accuracy: %.1fm",
                    source, best.getLatitude(), best.getLongitude(), best.getAccuracy()));

            if (callback != null) {
                callback.onLocationReceived(best, source);
            }
        }
    }

    private Location chooseBestLocation(Location loc1, Location loc2) {
        if (loc1 == null) return loc2;
        if (loc2 == null) return loc1;

        // Check accuracy - lower is better
        if (loc2.getAccuracy() < loc1.getAccuracy() * 0.8) {
            return loc2;
        }

        // Check freshness
        long age1 = SystemClock.elapsedRealtime() - loc1.getElapsedRealtimeNanos() / 1000000;
        long age2 = SystemClock.elapsedRealtime() - loc2.getElapsedRealtimeNanos() / 1000000;

        // If loc1 is too old, prefer loc2
        if (age1 > LOCATION_TIMEOUT && age2 < LOCATION_TIMEOUT) {
            return loc2;
        }

        // Prefer GPS over Network for same age
        if (Math.abs(age1 - age2) < 30000) {
            if (isGpsLocation(loc1) && !isGpsLocation(loc2)) {
                return loc1;
            }
        }

        return loc1;
    }

    private boolean isGpsLocation(Location location) {
        return location.getProvider() != null &&
                location.getProvider().equals(LocationManager.GPS_PROVIDER);
    }

    private String getLocationSource(Location location) {
        if (location.getProvider() == null) return "unknown";
        return location.getProvider().equals(LocationManager.GPS_PROVIDER) ? "gps" : "network";
    }

    private boolean isLocationFresh(Location location) {
        long age = SystemClock.elapsedRealtime() - location.getElapsedRealtimeNanos() / 1000000;
        return age < LOCATION_TIMEOUT;
    }

    private int getWifiChannel(int frequency) {
        if (frequency >= 2412 && frequency <= 2484) {
            return (frequency - 2412) / 5 + 1;
        } else if (frequency >= 5170 && frequency <= 5825) {
            return (frequency - 5170) / 5 + 34;
        }
        return 0;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasWifiPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Get current best location
     */
    public Location getCurrentLocation() {
        return currentBestLocation;
    }

    /**
     * Check if any location provider is enabled
     */
    public boolean isAnyProviderEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
}