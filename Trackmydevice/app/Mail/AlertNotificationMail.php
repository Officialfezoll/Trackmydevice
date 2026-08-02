<?php

namespace App\Mail;

use App\Models\DeviceAlert;
use Illuminate\Mail\Mailable;
use Illuminate\Support\Facades\Log;

class AlertNotificationMail extends Mailable
{
    public DeviceAlert $alert;
    public string $deviceName;
    public string $alertType;
    public string $message;
    public string $time;

    public function __construct(DeviceAlert $alert)
    {
        $this->alert = $alert;
        $this->deviceName = $alert->device?->name ?? 'Unknown Device';
        $this->alertType = $this->getAlertTypeLabel($alert->type);
        $this->message = $alert->message;
        $this->time = $alert->created_at->format('M d, Y H:i');
    }

    public function build(): self
    {
        return $this
            ->subject("[Fadhili Alert] {$this->alertType} - {$this->deviceName}")
            ->view('emails.alert-notification')
            ->with([
                'deviceName' => $this->deviceName,
                'alertType' => $this->alertType,
                'message' => $this->message,
                'time' => $this->time,
                'severity' => $this->getSeverityColor($this->alert->type),
            ]);
    }

    private function getAlertTypeLabel(string $type): string
    {
        return match ($type) {
            'geofence_enter' => 'Geofence Entry',
            'geofence_exit' => 'Geofence Exit',
            'battery_low' => 'Battery Low',
            'sim_change' => 'SIM Changed',
            'device_offline' => 'Device Offline',
            'speed_alert' => 'Speed Alert',
            'sos' => 'SOS Emergency',
            default => 'Alert',
        };
    }

    private function getSeverityColor(string $type): string
    {
        return match ($type) {
            'sos' => 'red',
            'sim_change' => 'orange',
            'device_offline' => 'gray',
            'battery_low' => 'yellow',
            default => 'blue',
        };
    }
}