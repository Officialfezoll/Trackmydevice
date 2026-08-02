<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\DeviceAlert;
use App\Models\DeviceLocation;
use Carbon\Carbon;
use Illuminate\Http\Request;

class ReportController extends Controller
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

        $from = $request->from ? \Carbon\Carbon::parse($request->from) : now()->subDays(7);
        $to   = $request->to   ? \Carbon\Carbon::parse($request->to)   : now();

        $alertsByType = DeviceAlert::selectRaw('type, count(*) as count')
            ->whereBetween('created_at', [$from, $to])
            ->groupBy('type')
            ->get();

        $locationPoints = DeviceLocation::whereBetween('recorded_at', [$from, $to])->count();

        $topActiveDevices = Device::withCount(['locations' => function ($q) use ($from, $to) {
                $q->whereBetween('recorded_at', [$from, $to]);
            }])
            ->orderByDesc('locations_count')
            ->take(10)
            ->get();

        $offlineEvents = DeviceAlert::where('type', 'device_offline')
            ->whereBetween('created_at', [$from, $to])
            ->with('device')
            ->orderBy('created_at', 'desc')
            ->take(20)
            ->get();

        $simChanges = DeviceAlert::where('type', 'sim_change')
            ->whereBetween('created_at', [$from, $to])
            ->with('device')
            ->orderBy('created_at', 'desc')
            ->take(20)
            ->get();

        return view('admin.reports.index', compact(
            'alertsByType', 'locationPoints', 'topActiveDevices',
            'offlineEvents', 'simChanges', 'from', 'to'
        ));
    }

    public function export(Request $request)
    {
        if ($r = $this->checkAdmin()) return $r;

        $from = $request->from ? \Carbon\Carbon::parse($request->from) : now()->subDays(7);
        $to   = $request->to   ? \Carbon\Carbon::parse($request->to)   : now();

        $alerts = DeviceAlert::with('device')
            ->whereBetween('created_at', [$from, $to])
            ->orderBy('created_at', 'desc')
            ->get();

        $csv = "ID,Device,Type,Message,Date\n";
        foreach ($alerts as $a) {
            $csv .= "{$a->id},{$a->device->name},{$a->type},\"{$a->message}\",{$a->created_at}\n";
        }

        return response($csv, 200, [
            'Content-Type'        => 'text/csv',
            'Content-Disposition' => 'attachment; filename="report-' . date('Y-m-d') . '.csv"',
        ]);
    }
}