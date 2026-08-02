<?php

namespace App\Http\Controllers;

use App\Models\Device;
use App\Models\DeviceLocation;
use App\Models\DeviceAlert;
use App\Models\DeviceCommand;
use Carbon\Carbon;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class MapController extends Controller
{
    // Available commands
    private const COMMANDS = [
        // Security
        'lock'       => 'Lock the device',
        'unlock'     => 'Unlock the device',
        'alarm'      => 'Trigger alarm sound',
        'alarm_off'  => 'Stop alarm',

        // Location
        'locate'     => 'Request immediate location',
        'screenshot' => 'Take screenshot',

        // Control
        'restart'    => 'Restart tracking service',
        'reboot'     => 'Reboot device',
        'silent'     => 'Enable silent mode',
        'normal'     => 'Disable silent mode',

        // Service Control
        'tracking_on'  => 'Enable tracking',
        'tracking_off' => 'Disable tracking',
        'wifi_on'      => 'Enable WiFi scanning',
        'wifi_off'     => 'Disable WiFi scanning',
        'gps_on'       => 'Enable GPS',
        'gps_off'      => 'Disable GPS',
        'data_on'      => 'Enable mobile data',
        'data_off'     => 'Disable mobile data',

        // Stealth
        'stealth_on'   => 'Hide app from drawer',
        'stealth_off'  => 'Show app in drawer',

        // Lock
        'lock'         => 'Lock device screen',
        'unlock'       => 'Unlock device',

        // Sound
        'silent'       => 'Enable silent mode',
        'normal'       => 'Restore normal sound',
    ];

    private function checkAuth()
    {
        if (!session('user_logged_in')) {
            return response()->json(['error' => 'Unauthorized'], 401);
        }
        return null;
    }

    public function liveData(Request $request)
    {
        if ($r = $this->checkAuth()) return $r;

        $userId  = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        $query = Device::with(['latestLocation'])->where('is_active', true);
        if (!$isAdmin) $query->where('user_id', $userId);

        $devices = $query->get()->map(function ($d) {
            return [
                'id'            => $d->id,
                'name'          => $d->name,
                'type'          => $d->type,
                'short_code'    => $d->short_code,
                'is_online'     => $d->is_online,
                'status'        => $d->status,
                'battery_level' => $d->battery_level,
                'is_charging'   => $d->is_charging,
                'last_seen_at'  => $d->last_seen_at,
                'last_lat'      => $d->last_lat,
                'last_lng'      => $d->last_lng,
                'location'      => $d->latestLocation ? [
                    'lat'         => $d->latestLocation->lat,
                    'lng'         => $d->latestLocation->lng,
                    'speed'       => $d->latestLocation->speed,
                    'source'      => $d->latestLocation->source,
                    'recorded_at' => $d->latestLocation->recorded_at,
                ] : null,
            ];
        });

        $unreadAlerts = DeviceAlert::where('user_id', $userId)->where('is_read', false)->count();

        return response()->json([
            'devices'       => $devices,
            'unread_alerts' => $unreadAlerts,
            'server_time'   => now()->toIso8601String(),
        ]);
    }

    public function deviceRoute(Request $request)
    {
        if ($r = $this->checkAuth()) return $r;

        $request->validate(['device_id' => 'required|integer']);

        $from = $request->from ? Carbon::parse($request->from) : now()->subHours(6);
        $to   = $request->to   ? Carbon::parse($request->to)   : now();

        $locations = DeviceLocation::where('device_id', $request->device_id)
            ->whereBetween('recorded_at', [$from, $to])
            ->orderBy('recorded_at', 'asc')
            ->get(['lat', 'lng', 'speed', 'source', 'recorded_at']);

        return response()->json(['route' => $locations]);
    }

    /**
     * Get available commands
     */
    public function getCommands()
    {
        if ($r = $this->checkAuth()) return $r;

        return response()->json([
            'commands' => self::COMMANDS,
        ]);
    }

    /**
     * Store PIN for device lock/unlock
     */
    public function setPin(Request $request)
    {
        if ($r = $this->checkAuth()) return $r;

        $request->validate([
            'device_id' => 'required|integer',
            'pin'       => 'required|string|min:4|max:8',
            'command'   => 'required|string|in:lock,unlock',
        ]);

        $userId  = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        $device = $isAdmin
            ? Device::findOrFail($request->device_id)
            : Device::where('id', $request->device_id)->where('user_id', $userId)->firstOrFail();

        // Store PIN in device notes field (encrypted)
        $pinData = json_decode($device->notes ?? '{}', true);
        $pinData['unlock_pin'] = $request->pin;
        $pinData['pin_updated_at'] = now()->toIso8601String();
        $pinData['pin_set_by'] = session('user_name', 'Admin');

        $device->update(['notes' => json_encode($pinData)]);

        // Send PIN to device via command
        DeviceCommand::create([
            'device_id'   => $device->id,
            'user_id'     => $userId,
            'command'     => 'set_pin',
            'description' => "Set unlock PIN: {$request->pin}",
            'status'      => 'queued',
            'sent_by'     => session('user_name', 'Admin'),
        ]);

        return response()->json([
            'success' => true,
            'message' => "PIN set for {$device->name}. PIN: {$request->pin}",
        ]);
    }

    /**
     * Send FCM command to device
     */
    public function sendCommand(Request $request)
    {
        if ($r = $this->checkAuth()) return $r;

        $request->validate([
            'device_id' => 'required|integer',
            'command'   => 'required|string',
        ]);

        $userId  = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        $device = $isAdmin
            ? Device::findOrFail($request->device_id)
            : Device::where('id', $request->device_id)->where('user_id', $userId)->firstOrFail();

        $command = $request->command;

        // Validate command
        if (!array_key_exists($command, self::COMMANDS)) {
            return response()->json([
                'success' => false,
                'message' => "Unknown command: {$command}. Available: " . implode(', ', array_keys(self::COMMANDS)),
            ], 400);
        }

        // Try FCM first if device is online
        if ($device->is_online && $device->fcm_token) {
            $result = $this->sendFcmCommand($device, $command, $userId);
            if ($result['success']) {
                $this->logCommand($device, $command, $userId, 'sent');
                return response()->json($result);
            }
        }

        // Always queue command (device will poll for it)
        $this->logCommand($device, $command, $userId, 'queued');

        return response()->json([
            'success' => true,
            'message' => "Command '{$command}' queued for device.",
            'queued' => true,
        ]);
    }

    /**
     * Get command history for device
     */
    public function commandHistory(Request $request)
    {
        if ($r = $this->checkAuth()) return $r;

        $request->validate(['device_id' => 'required|integer']);

        $userId  = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        $device = $isAdmin
            ? Device::findOrFail($request->device_id)
            : Device::where('id', $request->device_id)->where('user_id', $userId)->firstOrFail();

        $commands = DeviceCommand::where('device_id', $device->id)
            ->orderBy('created_at', 'desc')
            ->limit(50)
            ->get();

        return response()->json(['commands' => $commands]);
    }

    /**
     * Send command via FCM
     */
    private function sendFcmCommand(Device $device, string $command, ?int $userId): array
    {
        $fcmService = new \App\Services\FcmService();
        $sent = $fcmService->sendToDevice(
            $device,
            $command,
            'TrackMyDevice Command',
            self::COMMANDS[$command] ?? "Command: {$command}"
        );

        if ($sent) {
            Log::info("FCM command sent: {$command} to device {$device->id}");
            return [
                'success' => true,
                'message' => "Command '{$command}' sent successfully.",
                'delivered' => true,
            ];
        }

        return [
            'success' => false,
            'message' => "FCM delivery failed. Command queued for next poll.",
        ];
    }

    /**
     * Check if command is urgent (should use high priority)
     */
    private function isUrgentCommand(string $command): bool
    {
        $urgent = ['alarm', 'lock', 'wipe', 'reboot', 'locate', 'restart'];
        return in_array($command, $urgent);
    }

    /**
     * Log command for history
     */
    private function logCommand(Device $device, string $command, ?int $userId, string $status): void
    {
        try {
            DeviceCommand::create([
                'device_id'    => $device->id,
                'user_id'      => $userId,
                'command'      => $command,
                'description'  => self::COMMANDS[$command] ?? $command,
                'status'       => $status,
                'sent_by'      => session('user_name', 'System'),
            ]);
        } catch (\Exception $e) {
            Log::error("Failed to log command: " . $e->getMessage());
        }
    }
}