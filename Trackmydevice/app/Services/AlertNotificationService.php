<?php

namespace App\Services;

use App\Models\Device;
use App\Models\DeviceAlert;
use App\Models\AlertRule;
use App\Models\User;
use App\Mail\AlertNotificationMail;
use Illuminate\Support\Facades\Mail;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Http;

class AlertNotificationService
{
    /**
     * Send alert through all configured channels
     */
    public function sendAlert(DeviceAlert $alert): void
    {
        $device = $alert->device;
        $user = $alert->user;
        $rule = AlertRule::where('type', $alert->type)->first();

        $channels = $rule?->channels ?? [AlertRule::CHANNEL_APP];

        foreach ($channels as $channel) {
            try {
                match ($channel) {
                    AlertRule::CHANNEL_APP => $this->sendAppNotification($alert),
                    AlertRule::CHANNEL_SMS => $this->sendSmsNotification($alert, $user),
                    AlertRule::CHANNEL_EMAIL => $this->sendEmailNotification($alert, $user),
                    default => Log::warning("Unknown alert channel: {$channel}"),
                };
            } catch (\Exception $e) {
                Log::error("Failed to send {$channel} notification for alert {$alert->id}: " . $e->getMessage());
            }
        }
    }

    /**
     * App notification is already handled by marking the alert in DB
     * FCM push will deliver it to the admin/customer app
     */
    private function sendAppNotification(DeviceAlert $alert): void
    {
        // App notification is triggered when user checks /alerts endpoint
        // If FCM push is desired, send via FCM here
        Log::info("App notification created for alert {$alert->id}");
    }

    /**
     * Send SMS notification
     */
    private function sendSmsNotification(DeviceAlert $alert, ?User $user): void
    {
        if (!$user || !$user->phone) {
            Log::warning("Cannot send SMS: user has no phone number");
            return;
        }

        $message = $this->buildSmsMessage($alert);
        $this->sendSms($user->phone, $message);

        Log::info("SMS sent to {$user->phone} for alert {$alert->id}");
    }

    /**
     * Send Email notification
     */
    private function sendEmailNotification(DeviceAlert $alert, ?User $user): void
    {
        if (!$user || !$user->email) {
            Log::warning("Cannot send email: user has no email");
            return;
        }

        Mail::to($user->email)->send(new AlertNotificationMail($alert));

        Log::info("Email sent to {$user->email} for alert {$alert->id}");
    }

    /**
     * Build SMS message from alert
     */
    private function buildSmsMessage(DeviceAlert $alert): string
    {
        $device = $alert->device;
        $rule = AlertRule::where('type', $alert->type)->first();

        $template = $rule?->sms_template ?? $this->getDefaultSmsTemplate($alert->type);

        $replacements = [
            '{device}' => $device->name ?? 'Unknown Device',
            '{alert_type}' => $this->getAlertTypeLabel($alert->type),
            '{message}' => $alert->message,
            '{time}' => $alert->created_at->format('H:i'),
        ];

        return str_replace(array_keys($replacements), array_values($replacements), $template);
    }

    /**
     * Get default SMS template by alert type
     */
    private function getDefaultSmsTemplate(string $type): string
    {
        return match ($type) {
            'geofence_enter' => '🚨 {device}: Entered {message}',
            'geofence_exit' => '📍 {device}: Exited {message}',
            'battery_low' => '🔋 {device}: {message}',
            'sim_change' => '⚠️ {device}: {message}',
            'device_offline' => '📴 {device}: Went offline',
            'speed_alert' => '⚡ {device}: Speed alert - {message}',
            'sos' => '🆘 {device}: SOS Alert! {message}',
            default => '⚠️ {device}: {message}',
        };
    }

    /**
     * Get human-readable alert type label
     */
    private function getAlertTypeLabel(string $type): string
    {
        return match ($type) {
            'geofence_enter' => 'Geofence Entry',
            'geofence_exit' => 'Geofence Exit',
            'battery_low' => 'Battery Low',
            'sim_change' => 'SIM Changed',
            'device_offline' => 'Offline',
            'speed_alert' => 'Speed Alert',
            'sos' => 'SOS',
            default => 'Alert',
        };
    }

    /**
     * Send SMS via configured provider
     */
    private function sendSms(string $phone, string $message): bool
    {
        $provider = env('SMS_PROVIDER', 'africastalking'); // africastalking, twilio, vonage

        return match ($provider) {
            'africastalking' => $this->sendViaAfricasTalking($phone, $message),
            'twilio' => $this->sendViaTwilio($phone, $message),
            'vonage' => $this->sendViaVonage($phone, $message),
            default => $this->sendViaGeneric($phone, $message),
        };
    }

    /**
     * Send SMS via Africa's Talking API
     */
    private function sendViaAfricasTalking(string $phone, string $message): bool
    {
        $apiKey = env('SMS_API_KEY');
        $username = env('SMS_USERNAME', 'sandbox');
        $senderId = env('SMS_SENDER_ID', 'Fadhili');

        if (!$apiKey || !$username) {
            Log::warning("Africa's Talking not configured: Set SMS_API_KEY and SMS_USERNAME in .env");
            return false;
        }

        try {
            $response = Http::withHeaders([
                'apikey' => $apiKey,
                'Content-Type' => 'application/x-www-form-urlencoded',
            ])->post('https://api.africastalking.com/version1/messaging', [
                'username' => $username,
                'to' => $this->normalizePhone($phone),
                'message' => $message,
                'from' => $senderId,
            ]);

            if ($response->successful()) {
                $data = $response->json();
                if (isset($data['SMSMessageData']['Recipients'][0]['status']) === 'Success') {
                    return true;
                }
            }

            Log::error("Africa's Talking SMS failed: " . $response->body());
            return false;
        } catch (\Exception $e) {
            Log::error("Africa's Talking SMS error: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Send SMS via Twilio
     */
    private function sendViaTwilio(string $phone, string $message): bool
    {
        $accountSid = env('TWILIO_ACCOUNT_SID');
        $authToken = env('TWILIO_AUTH_TOKEN');
        $fromNumber = env('TWILIO_FROM_NUMBER');

        if (!$accountSid || !$authToken || !$fromNumber) {
            Log::warning("Twilio not configured: Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_FROM_NUMBER in .env");
            return false;
        }

        try {
            $response = Http::withBasicAuth($accountSid, $authToken)
                ->post("https://api.twilio.com/2010-04-01/Accounts/{$accountSid}/Messages.json", [
                    'To' => $this->normalizePhone($phone),
                    'From' => $fromNumber,
                    'Body' => $message,
                ]);

            return $response->successful();
        } catch (\Exception $e) {
            Log::error("Twilio SMS error: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Send SMS via Vonage (Nexmo)
     */
    private function sendViaVonage(string $phone, string $message): bool
    {
        $apiKey = env('VONAGE_API_KEY');
        $apiSecret = env('VONAGE_API_SECRET');
        $fromNumber = env('VONAGE_FROM_NUMBER');

        if (!$apiKey || !$apiSecret || !$fromNumber) {
            Log::warning("Vonage not configured: Set VONAGE_API_KEY, VONAGE_API_SECRET, and VONAGE_FROM_NUMBER in .env");
            return false;
        }

        try {
            $response = Http::asForm()
                ->post('https://rest.nexmo.com/sms/json', [
                    'api_key' => $apiKey,
                    'api_secret' => $apiSecret,
                    'to' => $this->normalizePhone($phone),
                    'from' => $fromNumber,
                    'text' => $message,
                ]);

            return $response->successful();
        } catch (\Exception $e) {
            Log::error("Vonage SMS error: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Send SMS via generic HTTP API
     */
    private function sendViaGeneric(string $phone, string $message): bool
    {
        $apiUrl = env('SMS_API_URL');
        $apiKey = env('SMS_API_KEY');

        if (!$apiUrl || !$apiKey) {
            Log::warning("SMS not configured: Set SMS_API_URL and SMS_API_KEY in .env");
            return false;
        }

        try {
            $response = Http::post($apiUrl, [
                'to' => $this->normalizePhone($phone),
                'message' => $message,
                'api_key' => $apiKey,
            ]);

            return $response->successful();
        } catch (\Exception $e) {
            Log::error("SMS send failed: " . $e->getMessage());
            return false;
        }
    }

    /**
     * Normalize phone number (E.164 format)
     */
    private function normalizePhone(string $phone): string
    {
        $phone = preg_replace('/[^0-9+]/', '', $phone);

        if (!str_starts_with($phone, '+')) {
            $phone = '+' . $phone; // Assume E.164 format
        }

        return $phone;
    }

    /**
     * Send critical alert with sound
     */
    public function sendCriticalAlert(DeviceAlert $alert): void
    {
        // Mark alert with critical priority
        $alert->update(['priority' => AlertRule::PRIORITY_CRITICAL]);

        // Send immediately through all channels
        $this->sendAlert($alert);
    }

    /**
     * Queue alert for batch processing (non-critical)
     */
    public function queueAlert(DeviceAlert $alert): void
    {
        // For non-critical alerts, you could queue them
        // dispatch(new SendAlertNotification($alert));
        $this->sendAlert($alert);
    }
}