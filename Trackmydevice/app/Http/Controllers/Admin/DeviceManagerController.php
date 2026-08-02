<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\DeviceLocation;
use Illuminate\Http\Request;

class DeviceManagerController extends Controller
{
    private function checkAdmin()
    {
        if (!session('user_logged_in') || session('user_role') !== 'admin') {
            return redirect()->route('login');
        }
        return null;
    }

    public function index(Request $request)
    {
        if ($r = $this->checkAdmin()) return $r;

        $query = Device::with('user');

        if ($request->search) {
            $query->where(function ($q) use ($request) {
                $q->where('name', 'like', "%{$request->search}%")
                  ->orWhere('short_code', 'like', "%{$request->search}%")
                  ->orWhere('uuid', 'like', "%{$request->search}%");
            });
        }

        if ($request->type) $query->where('type', $request->type);
        if ($request->status) $query->where('is_online', $request->status === 'online');

        $devices = $query->orderBy('last_seen_at', 'desc')->paginate(20);

        return view('admin.devices.index', compact('devices'));
    }

    public function show($id)
    {
        if ($r = $this->checkAdmin()) return $r;
        $device = Device::with('user', 'alerts')->findOrFail($id);
        $recentLocations = DeviceLocation::where('device_id', $id)
            ->orderBy('recorded_at', 'desc')->take(50)->get();
        return view('admin.devices.show', compact('device', 'recentLocations'));
    }

    public function update(Request $request, $id)
    {
        if ($r = $this->checkAdmin()) return $r;

        $device = Device::findOrFail($id);
        $request->validate([
            'name'        => 'required|string|max:255',
            'type'        => 'required|in:car,mobile,tv,other',
            'imei'        => 'nullable|string|max:50',
            'mac_address' => 'nullable|string|max:50',
            'is_active'   => 'nullable|boolean',
            'notes'       => 'nullable|string',
        ]);

        $device->update($request->only(['name', 'type', 'imei', 'mac_address', 'is_active', 'notes']));

        return redirect()->route('admin.devices.show', $id)->with('success', 'Device updated.');
    }

    public function history($id)
    {
        if ($r = $this->checkAdmin()) return $r;
        $device = Device::findOrFail($id);
        $locations = DeviceLocation::where('device_id', $id)
            ->orderBy('recorded_at', 'desc')
            ->paginate(100);
        return view('admin.devices.history', compact('device', 'locations'));
    }
}