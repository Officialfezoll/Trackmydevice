<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Models\DeviceAlert;
use App\Models\DeviceCommand;
use App\Models\Geofence;
use App\Services\FcmService;
use Illuminate\Http\Request;

class DeviceStatusController extends Controller
{
    public function updateFcmToken(Request $request)
    {
        $request->validate(['fcm_token' => 'required|string']);
        $device = $request->attributes->get('authenticated_device');
        $device->update(['fcm_token' => $request->fcm_token]);
        return response()->json(['success' => true]);
    }

    public function pendingAlerts(Request $request)
    {
        $device = $request->attributes->get('authenticated_device');
        $alerts = DeviceAlert::where('device_id', $device->id)
            ->where('is_read', false)
            ->orderBy('created_at', 'desc')
            ->take(20)
            ->get()
            ->map(function ($alert) {
                return [
                    'id' => $alert->id,
                    'type' => $alert->type,
                    'message' => $alert->message,
                    'meta' => $alert->meta,
                    'priority' => $alert->priority,
                    'sound_config' => $alert->getSoundConfig(),
                    'created_at' => $alert->created_at->toIso8601String(),
                ];
            });

        return response()->json(['alerts' => $alerts]);
    }

    public function pendingCommands(Request $request)
    {
        $device = $request->attributes->get('authenticated_device');
        $commands = DeviceCommand::where('device_id', $device->id)
            ->where('status', 'queued')
            ->orderBy('created_at', 'asc')
            ->take(10)
            ->get(['id', 'command', 'description', 'created_at'])
            ->map(function ($cmd) {
                // Extract PIN from description for set_pin commands
                $data = [
                    'id' => $cmd->id,
                    'command' => $cmd->command,
                    'description' => $cmd->description,
                    'created_at' => $cmd->created_at->toIso8601String(),
                ];
                if ($cmd->command === 'set_pin' && preg_match('/PIN: (.+)/', $cmd->description, $m)) {
                    $data['pin'] = trim($m[1]);
                }
                return $data;
            });

        // Mark as executed
        DeviceCommand::where('device_id', $device->id)
            ->where('status', 'queued')
            ->update([
                'status' => 'executed',
                'executed_at' => now()
            ]);

        return response()->json(['commands' => $commands]);
    }

    public function sendCommand(Request $request)
    {
        $request->validate([
            'device_id' => 'required|integer',
            'command' => 'required|string',
        ]);

        $device = Device::find($request->device_id);
        if (!$device) {
            return response()->json(['success' => false, 'message' => 'Device not found'], 404);
        }

        // Store command for device to poll
        DeviceCommand::create([
            'device_id' => $device->id,
            'user_id' => session('user_id'),
            'command' => $request->command,
            'description' => $this->getCommandLabel($request->command),
            'status' => 'queued',
            'sent_by' => session('user_name', 'System'),
        ]);

        \Log::info("FCM: Sending command '{$request->command}' to device {$device->id}, FCM token: " .
            ($device->fcm_token ? substr($device->fcm_token, 0, 20) . '...' : 'NULL'));

        // Send FCM push notification for immediate delivery
        $fcmService = new FcmService();
        $commandLabel = $this->getCommandLabel($request->command);
        $fcmSent = $fcmService->sendToDevice(
            $device,
            $request->command,
            'TrackMyDevice Command',
            $commandLabel
        );

        \Log::info("FCM: Command sent result: " . ($fcmSent ? 'SUCCESS' : 'FAILED'));

        return response()->json([
            'success' => true,
            'message' => $fcmSent ? 'Command sent via FCM' : 'Command stored (FCM failed)',
            'fcm_sent' => $fcmSent
        ]);
    }

    private function getCommandLabel(string $command): string
    {
        return match($command) {
            'alarm' => '🔔 ALARM triggered! Find your device now!',
            'alarm_off' => '🔕 Alarm deactivated',
            'locate' => '📍 Location request sent',
            'lock' => '🔒 Device lock requested',
            'unlock' => '🔓 Device unlock requested',
            'wipe' => '⚠️ Remote wipe requested',
            'restart' => '🔄 Restarting tracking service...',
            'reboot' => '🔄 Rebooting device...',
            'screenshot' => '📸 Screenshot capture requested',
            'gps_on' => '🛰️ GPS enabled',
            'gps_off' => '🛰️ GPS disabled',
            'data_on' => '📶 Mobile data enabled',
            'data_off' => '📶 Mobile data disabled',
            'silent' => '🔇 Silent mode activated',
            'normal' => '🔊 Normal mode restored',
            'stealth_on' => '👻 App hidden from drawer',
            'stealth_off' => '👁️ App visible in drawer',
            default => "Command received: {$command}",
        };
    }

    public function geofences(Request $request)
    {
        $device = $request->attributes->get('authenticated_device');

        $geofences = Geofence::where('is_active', true)
            ->where(function ($q) use ($device) {
                $q->where('apply_to_all', true)
                  ->orWhereHas('devices', fn($q2) => $q2->where('device_id', $device->id));
            })
            ->get(['id', 'name', 'polygon_points', 'alert_on_enter', 'alert_on_exit']);

        return response()->json(['geofences' => $geofences]);
    }
}