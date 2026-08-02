<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\User;
use App\Models\DeviceAlert;
use App\Models\DeviceLocation;
use Illuminate\Http\Request;

class AdminController extends Controller
{
    private function checkAdmin()
    {
        if (!session('user_logged_in') || session('user_role') !== 'admin') {
            return redirect()->route('login');
        }
        return null;
    }

    public function dashboard()
    {
        if ($r = $this->checkAdmin()) return $r;

        $totalDevices   = Device::count();
        $onlineDevices  = Device::where('is_online', true)->count();
        $offlineDevices = Device::where('is_online', false)->count();
        $totalUsers     = User::where('role', 'customer')->count();
        $totalAlerts    = DeviceAlert::where('is_read', false)->count();
        $recentAlerts   = DeviceAlert::with('device')->orderBy('created_at', 'desc')->take(10)->get();
        $devicesByType  = Device::selectRaw('type, count(*) as count')->groupBy('type')->get();
        $locationCount  = DeviceLocation::whereDate('created_at', today())->count();
        $activeDevices  = Device::where('is_online', true)
            ->with('user')
            ->orderBy('last_seen_at', 'desc')
            ->take(10)
            ->get();

        return view('admin.dashboard', compact(
            'totalDevices', 'onlineDevices', 'offlineDevices',
            'totalUsers', 'totalAlerts', 'recentAlerts',
            'devicesByType', 'locationCount', 'activeDevices'
        ));
    }
}