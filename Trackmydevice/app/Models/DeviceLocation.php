<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class DeviceLocation extends Model
{
    protected $fillable = [
        'device_id', 'lat', 'lng', 'accuracy', 'altitude',
        'speed', 'bearing', 'source', 'recorded_at',
    ];

    protected $casts = [
        'lat'         => 'float',
        'lng'         => 'float',
        'accuracy'    => 'float',
        'altitude'    => 'float',
        'speed'       => 'float',
        'bearing'     => 'float',
        'recorded_at' => 'datetime',
    ];

    public function device()
    {
        return $this->belongsTo(Device::class);
    }
}