<?php

namespace App\Http\Controllers\Customer;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\DeviceAlert;
use App\Models\Geofence;
use Illuminate\Http\Request;

class CustomerController extends Controller
{
    private function checkAuth()
    {
        if (!session('user_logged_in')) {
            return redirect()->route('login');
        }
        return null;
    }

    public function map()
    {
        if ($r = $this->checkAuth()) return $r;

        $userId  = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        $devices = $isAdmin
            ? Device::with('user')->where('is_active', true)->get()
            : Device::where('user_id', $userId)->where('is_active', true)->get();

        $geofences = Geofence::where('is_active', true)->get();
        $unreadAlerts = DeviceAlert::where('user_id', $userId)->where('is_read', false)->count();

        return view('customer.map', compact('devices', 'geofences', 'unreadAlerts'));
    }

    public function alerts(Request $request)
    {
        if ($r = $this->checkAuth()) return $r;
        $userId = session('user_id');
        $alerts = DeviceAlert::where('user_id', $userId)
            ->with('device')
            ->orderBy('created_at', 'desc')
            ->paginate(20);
        return view('customer.alerts', compact('alerts'));
    }

    public function markAlertRead(Request $request, $id)
    {
        if ($r = $this->checkAuth()) return $r;
        $alert = DeviceAlert::where('id', $id)->where('user_id', session('user_id'))->firstOrFail();
        $alert->update(['is_read' => true]);
        return response()->json(['success' => true]);
    }
}