<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Geofence;
use App\Models\Device;
use Illuminate\Http\Request;

class GeofenceController extends Controller
{
    private function checkAdmin()
    {
        if (!session('user_logged_in') || session('user_role') !== 'admin') {
            return redirect()->route('login');
        }
        return null;
    }

    public function index()
    {
        if ($r = $this->checkAdmin()) return $r;
        $geofences = Geofence::with('devices')->orderBy('created_at', 'desc')->get();
        $devices = Device::where('is_active', true)->get(['id', 'name', 'short_code']);
        return view('admin.geofences.index', compact('geofences', 'devices'));
    }

    public function store(Request $request)
    {
        if ($r = $this->checkAdmin()) return $r;
        $request->validate([
            'name'           => 'required|string|max:255',
            'polygon_points' => 'required|json',
            'alert_on_enter' => 'nullable|boolean',
            'alert_on_exit'  => 'nullable|boolean',
            'apply_to_all'   => 'nullable|boolean',
            'device_ids'     => 'nullable|array',
        ]);

        $geofence = Geofence::create([
            'name'           => $request->name,
            'polygon_points' => json_decode($request->polygon_points, true),
            'alert_on_enter' => $request->boolean('alert_on_enter', true),
            'alert_on_exit'  => $request->boolean('alert_on_exit', true),
            'apply_to_all'   => $request->boolean('apply_to_all', false),
            'is_active'      => true,
            'created_by'     => session('user_id'),
        ]);

        if ($request->device_ids) {
            $geofence->devices()->sync($request->device_ids);
        }

        return redirect()->route('admin.geofences.index')->with('success', 'Geofence created successfully');
        // return response()->json(['success' => true, 'geofence' => $geofence]);
    }

    public function update(Request $request, $id)
    {
        if ($r = $this->checkAdmin()) return $r;
        $geofence = Geofence::findOrFail($id);
        $geofence->update($request->only(['name', 'is_active', 'alert_on_enter', 'alert_on_exit', 'apply_to_all']));
        return redirect()->route('admin.geofences.index')->with('success', 'Geofence updated successfully');
        // return response()->json(['success' => true]);
    }
}