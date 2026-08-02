<?php

namespace App\Http\Controllers;

use App\Models\Device;
use App\Models\DeviceAlert;
use App\Models\DeviceCommand;
use App\Services\AiPredictionService;
use App\Services\FcmService;
use Illuminate\Http\Request;

class ChatbotController extends Controller
{
    private AiPredictionService $aiService;

    private $intents = [
        'status'    => ['status', 'online', 'offline', 'how is', 'check device', 'hali', 'mtandaoni', 'state', 'condition'],
        'battery'   => ['battery', 'charge', 'power', 'betri', 'chaji', 'nguvu', 'energy'],
        'location'  => ['where', 'location', 'position', 'coords', 'gps', 'wapi', 'mahali', 'address', 'place'],
        'alerts'    => ['alert', 'notification', 'warning', 'sim', 'change', 'taarifa', 'onyo', 'tahadhari'],
        'route'     => ['route', 'history', 'path', 'traveled', 'movement', 'njia', 'safari', 'travel'],
        'send_alarm'=> ['send alarm', 'trigger alarm', 'piga alarm', 'anza alarm', 'washa alarm', 'tuma alarm', 'anzisha', 'arifa'],
        'stop_alarm'=> ['stop alarm', 'zima alarm', 'off alarm', 'alarm off', 'alarm stop', 'simamisha', 'kata'],
        'lock'      => ['lock device', 'lock my', 'funga kifaa', 'lock screen', 'funga simu', 'funga', 'lock now'],
        'unlock'    => ['unlock device', 'unlock my', 'fungua kifaa', 'fungua simu', 'fungua', 'open device'],
        'locate'    => ['locate device', 'find device', 'force location', 'tafuta kifaa', 'tafuta simu', 'find my', 'track'],
        'restart'   => ['restart service', 'restart device', 'anza upya', 'reboot', 'reset'],
        'silent'    => ['silent mode', 'mute device', 'nyamazisha', 'sound off', 'piga chini', 'anyamazisha'],
        'stealth_on'  => ['stealth mode', 'hide app', 'ficha app', 'disguise', 'ficha', 'sakinisha', 'hide'],
        'stealth_off' => ['show app', 'unhide app', 'onyesha app', 'visible', 'onyesha', 'show'],
        'help'      => ['help', 'what can', 'options', 'menu', 'msaada', 'nini', 'nini unaweza', 'msaada gani'],
        'predict'   => ['predict', 'ai', 'forecast', 'analyse', 'analyze', 'tabiri', 'uchambuzi', 'uchambuzi wa'],
        'normal'    => ['normal mode', 'unsilent', 'restore sound', 'rudi kawaida', 'sauti', 'sound on'],
    ];

    private $commandMap = [
        'send_alarm' => 'alarm',
        'stop_alarm' => 'alarm_off',
        'lock'       => 'lock',
        'unlock'     => 'unlock',
        'locate'     => 'locate',
        'restart'    => 'restart',
        'silent'     => 'silent',
        'normal'     => 'normal',
        'stealth_on' => 'stealth_on',
        'stealth_off'=> 'stealth_off',
    ];

    private $commandLabels = [
        'alarm'      => '🔔 Alarm triggered on device',
        'alarm_off'  => '🔕 Alarm stopped on device',
        'lock'       => '🔒 Lock command sent',
        'unlock'     => '🔓 Unlock command sent',
        'locate'     => '📍 Location request sent',
        'restart'    => '🔄 Restart command sent',
        'silent'     => '🔇 Silent mode activated',
        'normal'     => '🔊 Normal mode restored',
        'stealth_on' => '👻 App hidden from drawer',
        'stealth_off'=> '👁️ App visible in drawer',
    ];

    public function __construct(AiPredictionService $aiService)
    {
        $this->aiService = $aiService;
    }

    public function respond(Request $request)
    {
        if (!session('user_logged_in')) {
            return response()->json(['error' => 'Unauthorized'], 401);
        }

        $request->validate(['message' => 'required|string|max:500']);
        $msg    = strtolower(trim($request->message));
        $intent = $this->detectIntent($msg);
        $deviceId = $request->device_id ?? null;

        // For predict intent, use AI chatbot directly
        if ($intent === 'predict' || $request->context === 'prediction') {
            $device = $deviceId ? Device::find($deviceId) : null;
            $reply = $this->aiService->chat($request->message, $device);
            return response()->json(['reply' => $reply, 'intent' => $intent]);
        }

        // For command intents, execute the command
        if (array_key_exists($intent, $this->commandMap)) {
            $reply = $this->executeChatCommand($intent, $deviceId);
            return response()->json(['reply' => $reply, 'intent' => $intent]);
        }

        $reply = $this->buildReply($intent, $request->message, $deviceId);

        return response()->json(['reply' => $reply, 'intent' => $intent]);
    }

    private function executeChatCommand(string $intent, ?int $deviceId): string
    {
        $command = $this->commandMap[$intent];
        $userId  = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        if (!$deviceId) {
            // Try to find the user's first device
            $device = Device::where('user_id', $userId)->where('is_active', true)->first();
            if (!$device && $isAdmin) {
                $device = Device::where('is_active', true)->first();
            }
            if (!$device) {
                return "No device found. Please select a device first.";
            }
        } else {
            $device = $isAdmin
                ? Device::find($deviceId)
                : Device::where('id', $deviceId)->where('user_id', $userId)->first();

            if (!$device) {
                return "Device not found or not accessible.";
            }
        }

        // Create command record
        DeviceCommand::create([
            'device_id'   => $device->id,
            'user_id'     => $userId,
            'command'     => $command,
            'description' => $this->commandLabels[$command] ?? $command,
            'status'      => 'queued',
            'sent_by'     => session('user_name', 'Chatbot'),
        ]);

        // Try FCM first
        $fcmService = new FcmService();
        $fcmSent = $fcmService->sendToDevice(
            $device,
            $command,
            'TrackMyDevice Command',
            $this->commandLabels[$command] ?? "Command: {$command}"
        );

        $label = $this->commandLabels[$command] ?? "Command: {$command}";

        if ($fcmSent) {
            return "{$label}\n\nDevice: **{$device->name}**\nStatus: Delivered via FCM (real-time)";
        }

        return "{$label}\n\nDevice: **{$device->name}**\nStatus: Queued (device will pick up on next poll)";
    }

    private function detectIntent(string $msg): string
    {
        foreach ($this->intents as $intent => $keywords) {
            foreach ($keywords as $kw) {
                if (str_contains($msg, $kw)) return $intent;
            }
        }
        return 'unknown';
    }

    private function buildReply(string $intent, string $msg, ?int $deviceId): string
    {
        $userId  = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        switch ($intent) {
            case 'status':
                if ($deviceId) {
                    $device = Device::find($deviceId);
                    if ($device) {
                        $onlineStatus = $device->is_online ? '🟢 Online' : '🔴 Offline';
                        $battery = $device->battery_level ?? '--';
                        return "Device **{$device->name}**: {$onlineStatus}\nBattery: {$battery}%\nLast seen: " . ($device->last_seen_at ? $device->last_seen_at->diffForHumans() : 'Never');
                    }
                }
                $devices = Device::where('user_id', $userId)->where('is_active', true)->get();
                if ($devices->isEmpty()) return "No devices found.";
                $reply = "Your devices:\n";
                foreach ($devices as $d) {
                    $icon = $d->is_online ? '🟢' : '🔴';
                    $reply .= "• {$icon} **{$d->name}** — " . ($d->battery_level ?? '--') . "%\n";
                }
                return $reply;

            case 'battery':
                if ($deviceId) {
                    $device = Device::find($deviceId);
                    if ($device) {
                        $charging = $device->is_charging ? '⚡ Charging' : '🔋 On battery';
                        return "Battery: **{$device->battery_level}%** — {$charging}";
                    }
                }
                $devices = Device::where('user_id', $userId)->where('is_active', true)->get();
                if ($devices->isEmpty()) return "No devices found.";
                $reply = "Battery levels:\n";
                foreach ($devices as $d) {
                    $reply .= "• **{$d->name}**: " . ($d->battery_level ?? '--') . "%\n";
                }
                return $reply;

            case 'location':
                if ($deviceId) {
                    $device = Device::find($deviceId);
                    if ($device && $device->last_lat) {
                        return "Last known position: **{$device->last_lat}, {$device->last_lng}**\nThe map is showing the current pin. 📍";
                    }
                }
                return "Select a device on the map to see its current location.";

            case 'alerts':
                $count = DeviceAlert::where('user_id', $userId)->where('is_read', false)->count();
                $recent = DeviceAlert::where('user_id', $userId)->orderBy('created_at', 'desc')->first();
                $reply = "You have **{$count}** unread alerts.";
                if ($recent) {
                    $reply .= "\nLatest: {$recent->message} ({$recent->created_at->diffForHumans()})";
                }
                return $reply;

            case 'route':
                return "To view a device route, select the device then click the **📍 Route** toggle button. You can filter by time range.";

            case 'help':
                return "I can help you with:\n• **Status** — 'check device status'\n• **Battery** — 'battery level'\n• **Location** — 'where is my device'\n• **Alerts** — 'show alerts'\n\n**Commands** (executed immediately):\n• 🔔 **Alarm** — 'send alarm'\n• 🔕 **Stop alarm** — 'stop alarm'\n• 🔒 **Lock** — 'lock device'\n• 📍 **Locate** — 'locate device'\n• 🔄 **Restart** — 'restart service'\n• 🔇 **Silent** — 'silent mode'\n• 👻 **Hide app** — 'stealth mode'\n• 👁️ **Show app** — 'show app'\n• 🤖 **AI Predict** — 'predict anomaly'\n\nYou can also ask in Kiswahili!";

            case 'predict':
                $device = $deviceId ? Device::find($deviceId) : null;
                return $this->aiService->chat($msg, $device);

            default:
                $device = $deviceId ? Device::find($deviceId) : null;
                return $this->aiService->chat($msg, $device);
        }
    }
}
