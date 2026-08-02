<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Device;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class DeviceAuthController extends Controller
{
    /**
     * Device login / auto-register.
     * If device UUID exists -> return token and continue.
     * If not -> auto-register device with minimal info.
     */
    public function login(Request $request)
    {
        $request->validate([
            'uuid'         => 'required|string|max:255',
            'device_name'  => 'nullable|string|max:255',
            'device_type'  => 'nullable|string|in:car,mobile,tv,other',
            'imei'         => 'nullable|string|max:50',
            'mac_address'  => 'nullable|string|max:50',
            'sim_number'   => 'nullable|string|max:30',
            'model'        => 'nullable|string|max:100',
            'os_version'   => 'nullable|string|max:50',
            'app_version'  => 'nullable|string|max:20',
        ]);

        $device = Device::where('uuid', $request->uuid)->first();

        if (!$device) {
            // Auto-register
            $shortCode = $this->generateUniqueShortCode();
            $device = Device::create([
                'uuid'         => $request->uuid,
                'short_code'   => $shortCode,
                'name'         => strtoupper($request->device_name ?? 'Device-' . strtoupper(Str::random(6))),
                'type'         => $request->device_type ?? 'other',
                'imei'         => $request->imei,
                'mac_address'  => $request->mac_address,
                'sim_number'   => $request->sim_number,
                'model'        => $request->model,
                'os_version'   => $request->os_version,
                'app_version'  => $request->app_version,
                'api_token'    => Str::random(60),
                'is_active'    => true,
                'is_registered' => false, // not yet claimed by user
            ]);
        } else {
            // Update metadata
            $device->update(array_filter([
                'model'       => $request->model ?? $device->model,
                'os_version'  => $request->os_version ?? $device->os_version,
                'app_version' => $request->app_version ?? $device->app_version,
                'last_seen_at' => now(),
            ]));

            // Rotate token on each login for security
            $device->update(['api_token' => Str::random(60)]);
        }

        return response()->json([
            'success'    => true,
            'token'      => $device->api_token,
            'short_code' => $device->short_code,
            'device_id'  => $device->id,
            'registered' => $device->is_registered,
        ]);
    }

    private function generateUniqueShortCode(): string
    {
        do {
            $characters = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
            $code = '';

            for ($i = 0; $i < 8; $i++) {
                $code .= $characters[random_int(0, strlen($characters) - 1)];
            }
            // $code = strtoupper(Str::random(8));
        } while (Device::where('short_code', $code)->exists());

        return $code;
    }
}