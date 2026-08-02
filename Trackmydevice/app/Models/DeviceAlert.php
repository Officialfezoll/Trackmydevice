<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class DeviceAlert extends Model
{
    protected $fillable = [
        'device_id', 'user_id', 'type', 'message',
        'meta', 'is_read', 'priority', 'sound_played'
    ];

    protected $casts = [
        'is_read'       => 'boolean',
        'sound_played'  => 'boolean',
        'priority'      => 'integer',
        'meta'          => 'array',
        'created_at'    => 'datetime',
        'updated_at'    => 'datetime',
    ];

    public const PRIORITY_LOW = 1;
    public const PRIORITY_NORMAL = 2;
    public const PRIORITY_HIGH = 3;
    public const PRIORITY_CRITICAL = 4;

    public function device()
    {
        return $this->belongsTo(Device::class);
    }

    public function user()
    {
        return $this->belongsTo(User::class);
    }

    public function isCritical(): bool
    {
        return $this->priority >= self::PRIORITY_HIGH;
    }

    public function scopeUnread($query)
    {
        return $query->where('is_read', false);
    }

    public function scopeCritical($query)
    {
        return $query->where('priority', '>=', self::PRIORITY_HIGH);
    }

    /**
     * Get sound configuration for this alert
     */
    public function getSoundConfig(): array
    {
        $rule = \App\Models\AlertRule::where('type', $this->type)->first();

        $soundEnabled = $rule?->sound_enabled ?? false;
        $priority = $this->priority;

        return [
            'sound_enabled' => $soundEnabled,
            'sound_type' => $this->getSoundType(),
            'vibrate' => $priority >= 3,
            'repeat' => $priority >= 4 ? 3 : 1,
        ];
    }

    /**
     * Get sound type based on alert type
     */
    private function getSoundType(): string
    {
        if ($this->priority >= 4) return 'emergency';

        return match ($this->type) {
            'sos' => 'emergency',
            'sim_change' => 'security',
            'geofence_enter', 'geofence_exit' => 'geofence',
            'battery_low' => 'warning',
            'device_offline' => 'offline',
            'speed_alert' => 'speed',
            default => 'default',
        };
    }
}