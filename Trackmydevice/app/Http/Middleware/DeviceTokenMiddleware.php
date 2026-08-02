<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use App\Models\Device;

class DeviceTokenMiddleware
{
    public function handle(Request $request, Closure $next)
    {
        $token = $request->header('X-Device-Token');

        if (!$token) {
            return response()->json(['error' => 'Device token required'], 401);
        }

        $device = Device::where('api_token', $token)->where('is_active', true)->first();

        if (!$device) {
            return response()->json(['error' => 'Invalid or inactive device token'], 401);
        }

        $request->merge(['authenticated_device' => $device]);
        $request->attributes->set('authenticated_device', $device);

        return $next($request);
    }
}