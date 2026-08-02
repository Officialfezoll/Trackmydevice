<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class AlertRule extends Model
{
    protected $fillable = [
        'name', 'type', 'threshold', 'is_active',
        'channels', 'sound_enabled', 'priority',
        'email_template', 'sms_template'
    ];

    protected $casts = [
        'threshold' => 'float',
        'is_active' => 'boolean',
        'sound_enabled' => 'boolean',
        'priority' => 'integer',
        'channels' => 'array',
    ];

    public const CHANNEL_APP = 'app';
    public const CHANNEL_SMS = 'sms';
    public const CHANNEL_EMAIL = 'email';

    public const PRIORITY_LOW = 1;
    public const PRIORITY_NORMAL = 2;
    public const PRIORITY_HIGH = 3;
    public const PRIORITY_CRITICAL = 4;

    public function shouldNotifyVia(string $channel): bool
    {
        $channels = $this->channels ?? [self::CHANNEL_APP];
        return in_array($channel, $channels, true);
    }

    public function isCritical(): bool
    {
        return $this->priority >= self::PRIORITY_HIGH;
    }
}