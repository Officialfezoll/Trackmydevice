<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\DeviceAlert;
use App\Models\AlertRule;
use Illuminate\Http\Request;

class AlertsController extends Controller
{
    /**
     * Get list of alerts for Agent app.
     */
    public function index(Request $request)
    {
        $query = DeviceAlert::orderBy('created_at', 'desc');

        // Filter by type if provided
        if ($request->type) {
            $query->where('type', $request->type);
        }

        // Filter by device if provided
        if ($request->device_id) {
            $query->where('device_id', $request->device_id);
        }

        // Filter by unread only
        if ($request->boolean('unread_only')) {
            $query->where('is_read', false);
        }

        $alerts = $query->limit($request->limit ?? 50)
            ->get()
            ->map(function ($alert) {
                $rule = AlertRule::where('type', $alert->type)->first();
                return [
                    'id' => $alert->id,
                    'device_id' => $alert->device_id,
                    'type' => $alert->type,
                    'message' => $alert->message,
                    'meta' => $alert->meta,
                    'is_read' => (bool) $alert->is_read,
                    'priority' => $alert->priority,
                    'sound_enabled' => $rule?->sound_enabled ?? false,
                    'channels' => $rule?->channels ?? ['app'],
                    'created_at' => $alert->created_at ? $alert->created_at->toIso8601String() : null,
                ];
            });

        $unreadCount = DeviceAlert::where('is_read', false)->count();

        return response()->json([
            'success' => true,
            'alerts' => $alerts,
            'unread_count' => $unreadCount,
            'total' => $alerts->count(),
        ]);
    }

    /**
     * Get alert statistics
     */
    public function stats()
    {
        $stats = [
            'total' => DeviceAlert::count(),
            'unread' => DeviceAlert::where('is_read', false)->count(),
            'critical' => DeviceAlert::where('priority', '>=', 3)->where('is_read', false)->count(),
            'by_type' => DeviceAlert::select('type', \DB::raw('count(*) as count'))
                ->groupBy('type')
                ->pluck('count', 'type'),
        ];

        return response()->json([
            'success' => true,
            'stats' => $stats,
        ]);
    }
}
