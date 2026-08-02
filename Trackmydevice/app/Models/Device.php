<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Factories\HasFactory;

class Device extends Model
{
    use HasFactory;

    protected $fillable = [
        'user_id', 'uuid', 'short_code', 'name', 'type',
        'imei', 'mac_address', 'sim_number', 'model', 'os_version',
        'app_version', 'api_token', 'fcm_token',
        'is_active', 'is_registered', 'is_online', 'is_charging',
        'status', 'battery_level', 'last_lat', 'last_lng',
        'last_seen_at', 'geofence_states', 'notes',
    ];

    protected $casts = [
        'is_active'      => 'boolean',
        'is_registered'  => 'boolean',
        'is_online'      => 'boolean',
        'is_charging'    => 'boolean',
        'battery_level'  => 'integer',
        'last_lat'       => 'float',
        'last_lng'       => 'float',
        'last_seen_at'   => 'datetime',
        'geofence_states'=> 'array',
    ];

    public function user()
    {
        return $this->belongsTo(User::class);
    }

    public function locations()
    {
        return $this->hasMany(DeviceLocation::class);
    }

    public function latestLocation()
    {
        return $this->hasOne(DeviceLocation::class)->latestOfMany('recorded_at');
    }

    public function alerts()
    {
        return $this->hasMany(DeviceAlert::class);
    }

    public function geofences()
    {
        return $this->belongsToMany(Geofence::class, 'device_geofence');
    }

    public function commands()
    {
        return $this->hasMany(DeviceCommand::class);
    }

    public function pendingCommands()
    {
        return $this->hasMany(DeviceCommand::class)->where('status', 'queued');
    }
}