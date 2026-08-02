<?php

namespace App\Services;

use App\Models\DeviceAlert;
use App\Models\AlertRule;
use Illuminate\Support\Facades\Log;

class SoundAlertService
{
    /**
     * Get sound configuration for an alert
     */
    public function getSoundConfig(DeviceAlert $alert): array
    {
        $rule = AlertRule::where('type', $alert->type)->first();

        $soundEnabled = $rule?->sound_enabled ?? false;
        $priority = $alert->priority;

        // Determine sound type based on alert type and priority
        $soundType = $this->getSoundType($alert->type, $priority);

        return [
            'sound_enabled' => $soundEnabled,
            'sound_type' => $soundType,
            'sound_url' => $soundEnabled ? $this->getSoundUrl($soundType) : null,
            'vibrate' => $priority >= 3, // Vibrate for high priority
            'repeat' => $priority >= 4 ? 3 : 1, // Repeat critical alerts
            'priority' => $priority,
        ];
    }

    /**
     * Get sound type based on alert type
     */
    private function getSoundType(string $type, int $priority): string
    {
        if ($priority >= 4) return 'emergency'; // Critical

        return match ($type) {
            'sos' => 'emergency',
            'sim_change' => 'security',
            'geofence_enter', 'geofence_exit' => 'geofence',
            'battery_low' => 'warning',
            'device_offline' => 'offline',
            'speed_alert' => 'speed',
            default => 'default',
        };
    }

    /**
     * Get sound URL for the sound type
     */
    private function getSoundUrl(string $soundType): string
    {
        $baseUrl = asset('sounds');

        return match ($soundType) {
            'emergency' => "{$baseUrl}/emergency.mp3",
            'security' => "{$baseUrl}/security.mp3",
            'geofence' => "{$baseUrl}/geofence.mp3",
            'warning' => "{$baseUrl}/warning.mp3",
            'offline' => "{$baseUrl}/offline.mp3",
            'speed' => "{$baseUrl}/speed.mp3",
            default => "{$baseUrl}/default.mp3",
        };
    }

    /**
     * Generate JavaScript code for playing alert sound
     */
    public function getJavaScriptSoundCode(DeviceAlert $alert): string
    {
        $config = $this->getSoundConfig($alert);

        if (!$config['sound_enabled']) {
            return '';
        }

        $soundUrl = $config['sound_url'];
        $repeat = $config['repeat'];
        $vibrate = $config['vibrate'] ? 'true' : 'false';

        return <<<JS
// Play alert sound
(function() {
    const audio = new Audio('{$soundUrl}');
    audio.loop = false;
    let played = 0;
    const maxPlays = {$repeat};

    function play() {
        if (played < maxPlays) {
            audio.play().then(() => {
                played++;
                if (played < maxPlays) {
                    audio.onended = play;
                }
            }).catch(e => console.log('Sound play failed:', e));
        }
    }

    play();

    // Vibrate if supported
    if ({$vibrate} && navigator.vibrate) {
        navigator.vibrate([200, 100, 200, 100, 200]);
    }
})();
JS;
    }
}
