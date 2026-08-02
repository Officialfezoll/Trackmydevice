<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\AlertRule;
use Illuminate\Http\Request;

class AlertRuleController extends Controller
{
    private function checkAdmin()
    {
        if (!session('user_logged_in') || session('user_role') !== 'admin') {
            return redirect()->route('login');
        }
        return null;
    }

    public function index()
    {
        if ($r = $this->checkAdmin()) return $r;
        $rules = AlertRule::orderBy('priority', 'desc')->get();
        return view('admin.alert_rules.index', compact('rules'));
    }

    public function store(Request $request)
    {
        if ($r = $this->checkAdmin()) return $r;
        $request->validate([
            'name'      => 'required|string|max:255',
            'type'      => 'required|string|in:sim_change,battery_low,offline,geofence_enter,geofence_exit,speed_exceed,sos',
            'threshold' => 'nullable|numeric',
            'is_active' => 'nullable|boolean',
            'channels'  => 'nullable|array',
            'channels.*'=> 'string|in:app,sms,email',
            'sound_enabled' => 'nullable|boolean',
            'priority'  => 'nullable|integer|between:1,4',
        ]);

        $rule = AlertRule::create([
            'name'         => $request->name,
            'type'         => $request->type,
            'threshold'    => $request->threshold,
            'is_active'    => $request->boolean('is_active', true),
            'channels'     => $request->channels ?? ['app'],
            'sound_enabled'=> $request->boolean('sound_enabled', true),
            'priority'     => $request->priority ?? AlertRule::PRIORITY_NORMAL,
        ]);

        return redirect()->route('admin.alert_rules.index')
            ->with('success', 'Alert rule created successfully.');
    }

    public function update(Request $request, $id)
    {
        if ($r = $this->checkAdmin()) return $r;
        $rule = AlertRule::findOrFail($id);

        $request->validate([
            'name'      => 'sometimes|string|max:255',
            'type'      => 'sometimes|string|in:sim_change,battery_low,offline,geofence_enter,geofence_exit,speed_exceed,sos',
            'threshold' => 'nullable|numeric',
            'is_active' => 'nullable|boolean',
            'channels'  => 'nullable|array',
            'channels.*'=> 'string|in:app,sms,email',
            'sound_enabled' => 'nullable|boolean',
            'priority'  => 'nullable|integer|between:1,4',
        ]);

        $data = $request->only([
            'name', 'type', 'threshold', 'is_active',
            'channels', 'sound_enabled', 'priority'
        ]);

        // Handle channels array
        if ($request->has('channels')) {
            $data['channels'] = $request->channels;
        }

        $rule->update($data);

        return redirect()->route('admin.alert_rules.index')
            ->with('success', 'Alert rule updated successfully.');
    }
}