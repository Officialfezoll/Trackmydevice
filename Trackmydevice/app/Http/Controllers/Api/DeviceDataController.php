<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\DeviceLocation;
use App\Models\DeviceAlert;
use App\Models\Geofence;
use App\Services\AlertNotificationService;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

class DeviceDataController extends Controller
{
    private AlertNotificationService $alertService;

    public function __construct(AlertNotificationService $alertService)
    {
        $this->alertService = $alertService;
    }

    private function getDevice(Request $request): Device
    {
        return $request->attributes->get('authenticated_device');
    }

    public function location(Request $request)
    {
        $request->validate([
            'lat'           => 'required|numeric|between:-90,90',
            'lng'           => 'required|numeric|between:-180,180',
            'accuracy'      => 'nullable|numeric',
            'altitude'      => 'nullable|numeric',
            'speed'         => 'nullable|numeric',
            'bearing'       => 'nullable|numeric',
            'source'        => 'nullable|string|in:gps,wifi,bluetooth,network',
            'recorded_at'   => 'nullable|date',
        ]);

        $device = $this->getDevice($request);

        $location = DeviceLocation::create([
            'device_id'   => $device->id,
            'lat'         => $request->lat,
            'lng'         => $request->lng,
            'accuracy'    => $request->accuracy,
            'altitude'    => $request->altitude,
            'speed'       => $request->speed,
            'bearing'     => $request->bearing,
            'source'      => $request->source ?? 'gps',
            'recorded_at' => $request->recorded_at ? Carbon::parse($request->recorded_at) : now(),
        ]);

        // Update device last known position
        $device->update([
            'last_lat'     => $request->lat,
            'last_lng'     => $request->lng,
            'last_seen_at' => now(),
            'is_online'    => true,
        ]);

        // Check geofences
        $this->checkGeofences($device, $request->lat, $request->lng);

        // Check speed alerts
        if ($request->speed && $request->speed > 0) {
            $this->checkSpeedAlert($device, $request->speed);
        }

        return response()->json(['success' => true, 'location_id' => $location->id]);
    }

    public function battery(Request $request)
    {
        $request->validate([
            'level'    => 'required|integer|between:0,100',
            'charging' => 'nullable|boolean',
        ]);

        $device = $this->getDevice($request);
        $device->update([
            'battery_level' => $request->level,
            'is_charging'   => $request->charging ?? false,
            'is_online'     => true,
            'last_seen_at'  => now(),
        ]);

        // Alert if battery low - configurable threshold
        $threshold = $this->getAlertThreshold('battery_low', 20);
        if ($request->level <= $threshold && !$request->charging) {
            $alert = $this->createAlert($device, 'battery_low', "Battery low: {$request->level}%");
            $this->alertService->queueAlert($alert);
        }

        return response()->json(['success' => true]);
    }

    public function status(Request $request)
    {
        $request->validate([
            'status'     => 'required|string|in:online,offline,idle,moving,locked,alarm',
            'is_online'  => 'nullable|boolean',
            'extra'      => 'nullable|array',
        ]);

        $device = $this->getDevice($request);
        $device->update([
            'status'       => $request->status,
            'is_online'    => $request->is_online ?? ($request->status !== 'offline'),
            'last_seen_at' => now(),
        ]);

        if ($request->status === 'offline') {
            $alert = $this->createAlert($device, 'device_offline', 'Device went offline');
            $this->alertService->queueAlert($alert);
        }

        // SOS alert
        if ($request->status === 'alarm') {
            $alert = $this->createAlert($device, 'sos', 'SOS alarm triggered!');
            $this->alertService->sendCriticalAlert($alert);
        }

        return response()->json(['success' => true]);
    }

    public function simChange(Request $request)
    {
        $request->validate([
            'old_sim' => 'nullable|string|max:30',
            'new_sim' => 'required|string|max:30',
            'imei'    => 'nullable|string|max:50',
        ]);

        $device = $this->getDevice($request);

        $oldSim = $device->sim_number;
        $device->update(['sim_number' => $request->new_sim]);

        $alert = $this->createAlert($device, 'sim_change',
            "SIM changed from {$oldSim} to {$request->new_sim}",
            ['old_sim' => $oldSim, 'new_sim' => $request->new_sim, 'imei' => $request->imei]
        );
        $this->alertService->sendCriticalAlert($alert);

        return response()->json(['success' => true]);
    }

    /**
     * Offline sync - batch location data
     */
    public function sync(Request $request)
    {
        $request->validate([
            'locations'   => 'required|array|max:500',
            'locations.*.lat'         => 'required|numeric',
            'locations.*.lng'         => 'required|numeric',
            'locations.*.recorded_at' => 'required|date',
            'locations.*.source'      => 'nullable|string',
        ]);

        $device = $this->getDevice($request);
        $inserted = 0;

        foreach ($request->locations as $loc) {
            DeviceLocation::create([
                'device_id'   => $device->id,
                'lat'         => $loc['lat'],
                'lng'         => $loc['lng'],
                'accuracy'    => $loc['accuracy'] ?? null,
                'speed'       => $loc['speed'] ?? null,
                'bearing'     => $loc['bearing'] ?? null,
                'source'      => $loc['source'] ?? 'gps',
                'recorded_at' => Carbon::parse($loc['recorded_at']),
            ]);
            $inserted++;
        }

        $device->update(['last_seen_at' => now(), 'is_online' => true]);

        return response()->json(['success' => true, 'synced' => $inserted]);
    }

    private function checkGeofences(Device $device, float $lat, float $lng): void
    {
        $geofences = Geofence::where('is_active', true)
            ->whereHas('devices', function ($q) use ($device) {
                $q->where('device_id', $device->id);
            })->orWhere(function ($q) use ($device) {
                $q->where('apply_to_all', true);
            })->get();

        foreach ($geofences as $geofence) {
            $inside = $this->isInsidePolygon($lat, $lng, $geofence->polygon_points);
            $states = $device->geofence_states ?? [];
            $wasInside = $states[$geofence->id] ?? null;

            if ($inside && $wasInside === false) {
                $alert = $this->createAlert($device, 'geofence_enter',
                    "Entered geofence: {$geofence->name}",
                    ['geofence_id' => $geofence->id]);
                $this->alertService->queueAlert($alert);
            } elseif (!$inside && $wasInside === true) {
                $alert = $this->createAlert($device, 'geofence_exit',
                    "Exited geofence: {$geofence->name}",
                    ['geofence_id' => $geofence->id]);
                $this->alertService->queueAlert($alert);
            }

            $states[$geofence->id] = $inside;
            $device->update(['geofence_states' => $states]);
        }
    }

    private function checkSpeedAlert(Device $device, float $speed): void
    {
        $threshold = $this->getAlertThreshold('speed_exceed', 120); // km/h
        if ($speed > $threshold) {
            $alert = $this->createAlert($device, 'speed_alert',
                "Speed exceeded {$threshold} km/h: " . round($speed, 1) . " km/h",
                ['speed' => $speed, 'threshold' => $threshold]
            );
            $this->alertService->queueAlert($alert);
        }
    }

    private function getAlertThreshold(string $type, int $default): int
    {
        $rule = \App\Models\AlertRule::where('type', $type)
            ->where('is_active', true)
            ->first();

        return $rule?->threshold ?? $default;
    }

    private function isInsidePolygon(float $lat, float $lng, array $polygon): bool
    {
        $n = count($polygon);
        if ($n < 3) return false;

        $inside = false;
        $j = $n - 1;

        for ($i = 0; $i < $n; $i++) {
            $xi = $polygon[$i]['lat'];
            $yi = $polygon[$i]['lng'];
            $xj = $polygon[$j]['lat'];
            $yj = $polygon[$j]['lng'];

            if ((($yi > $lng) !== ($yj > $lng)) &&
                ($lat < ($xj - $xi) * ($lng - $yi) / ($yj - $yi) + $xi)) {
                $inside = !$inside;
            }
            $j = $i;
        }

        return $inside;
    }

    private function createAlert(Device $device, string $type, string $message, array $meta = []): DeviceAlert
    {
        return DeviceAlert::create([
            'device_id' => $device->id,
            'user_id'   => $device->user_id,
            'type'      => $type,
            'message'   => $message,
            'meta'      => $meta,
            'is_read'   => false,
        ]);
    }
}