<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Device;
use Illuminate\Http\Request;

class DeviceListController extends Controller
{
    /**
     * Get list of devices for Agent app.
     */
    public function index(Request $request)
    {
        $devices = Device::where('is_active', true)
            ->orderBy('last_seen_at', 'desc')
            ->get()
            ->map(function ($device) {
                return [
                    'id' => $device->id,
                    'name' => $device->name,
                    'type' => $device->type,
                    'is_online' => $device->last_seen_at && $device->last_seen_at->diffInMinutes(now()) < 5,
                    'battery_level' => $device->battery_level ?? 100,
                    'last_lat' => $device->last_lat,
                    'last_lng' => $device->last_lng,
                    'status' => $device->status ?? 'unknown',
                    'last_seen_at' => $device->last_seen_at ? $device->last_seen_at->toIso8601String() : null,
                ];
            });

        return response()->json([
            'success' => true,
            'devices' => $devices,
        ]);
    }
}
