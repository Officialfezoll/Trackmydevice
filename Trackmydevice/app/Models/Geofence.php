<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Geofence extends Model
{
    protected $fillable = [
        'name', 'polygon_points', 'alert_on_enter', 'alert_on_exit',
        'apply_to_all', 'is_active', 'created_by',
    ];

    protected $casts = [
        'polygon_points'  => 'array',
        'alert_on_enter'  => 'boolean',
        'alert_on_exit'   => 'boolean',
        'apply_to_all'    => 'boolean',
        'is_active'       => 'boolean',
    ];

    public function devices()
    {
        return $this->belongsToMany(Device::class, 'device_geofence');
    }

    public function creator()
    {
        return $this->belongsTo(User::class, 'created_by');
    }
}