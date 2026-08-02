<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class AiPredictConfig extends Model
{
    protected $fillable = [
        'name', 'cf_account_id', 'cf_api_token', 'model_id', 'system_prompt', 'is_active'
    ];

    protected $casts = ['is_active' => 'boolean'];

    protected $hidden = ['cf_api_token'];
}