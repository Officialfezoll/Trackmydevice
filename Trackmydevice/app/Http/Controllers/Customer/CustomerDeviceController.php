<?php

namespace App\Http\Controllers\Customer;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\DeviceLocation;
use Carbon\Carbon;
use Illuminate\Http\Request;

class CustomerDeviceController extends Controller
{
    private function checkAuth()
    {
        if (!session('user_logged_in')) {
            return redirect()->route('login');
        }
        return null;
    }

    public function index()
    {
        if ($r = $this->checkAuth()) return $r;
        $devices = Device::where('user_id', session('user_id'))->orderBy('created_at', 'desc')->get();
        return view('customer.devices', compact('devices'));
    }

    public function addByCode(Request $request)
    {
        if ($r = $this->checkAuth()) return $r;
        $request->validate(['short_code' => 'required|string|max:20']);

        $device = Device::where('short_code', strtoupper(trim($request->short_code)))
            ->whereNull('user_id')
            ->first();

        if (!$device) {
            return response()->json([
                'success' => false,
                'message' => 'Device code not found or already claimed.'
            ], 422);
        }

        $device->update([
            'user_id'       => session('user_id'),
            'is_registered' => true,
        ]);

        return response()->json([
            'success' => true,
            'device'  => $device,
            'message' => "Device '{$device->name}' added to your account!"
        ]);
    }

    public function update(Request $request, $id)
    {
        if ($r = $this->checkAuth()) return $r;

        $device = Device::where('id', $id)->where('user_id', session('user_id'))->firstOrFail();
        $request->validate([
            'name'        => 'required|string|max:255',
            'type'        => 'required|in:car,mobile,tv,other',
            'imei'        => 'nullable|string|max:50',
            'mac_address' => 'nullable|string|max:50',
        ]);

        $device->update($request->only(['name', 'type', 'imei', 'mac_address']));

        return response()->json(['success' => true, 'device' => $device]);
    }

    public function latestLocation($id)
    {
        if ($r = $this->checkAuth()) return $r;

        $userId  = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        $device = $isAdmin
            ? Device::findOrFail($id)
            : Device::where('id', $id)->where('user_id', $userId)->firstOrFail();

        $location = DeviceLocation::where('device_id', $id)
            ->orderBy('recorded_at', 'desc')
            ->first();

        return response()->json([
            'device'   => $device,
            'location' => $location,
        ]);
    }

    public function route(Request $request, $id)
    {
        if ($r = $this->checkAuth()) return $r;

        $userId  = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        if (!$isAdmin) {
            Device::where('id', $id)->where('user_id', $userId)->firstOrFail();
        }

        $from = $request->from ? Carbon::parse($request->from) : now()->subHours(6);
        $to   = $request->to   ? Carbon::parse($request->to)   : now();

        $locations = DeviceLocation::where('device_id', $id)
            ->whereBetween('recorded_at', [$from, $to])
            ->orderBy('recorded_at', 'asc')
            ->get(['lat', 'lng', 'speed', 'source', 'recorded_at']);

        return response()->json(['route' => $locations]);
    }
}