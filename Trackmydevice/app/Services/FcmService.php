<?php

namespace App\Services;

use App\Models\Device;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class FcmService
{
    private string $projectId;
    private string $credentialsPath;
    private string $serverKey;

    public function __construct()
    {
        $this->projectId = env('FCM_PROJECT_ID', 'continue-123');
        $this->credentialsPath = storage_path('firebase/service-account.json');
        $this->serverKey = env('FCM_SERVER_KEY', 'AAAAKfij9E8:APA91bHtX6q1fw4Yx9sKhe5fh5VdeyqX0SSq7LmWATwodWWefMyBdpR9DGGhxIupbqz0GcsFI8LGyBe33NHYiVXt0-NEXaJJf2-iqF7aHiPvJNWjKtbzJsBJyYOVjFwNO9msjUzaf1ma');
    }

    /**
     * Send push notification to a device
     */
    public function sendToDevice(Device $device, string $command, ?string $title = null, ?string $body = null): bool
    {
        if (empty($device->fcm_token)) {
            Log::warning("FCM: Device {$device->id} has no FCM token");
            return false;
        }

        $title = $title ?? 'TrackMyDevice';
        $body = $body ?? "Command: {$command}";

        Log::info("FCM: Sending '{$command}' to device {$device->id}");

        // Try HTTP v1 first, fallback to legacy
        $result = $this->sendViaHttpV1($device->fcm_token, $title, $body, $command, $device->id);

        if (!$result) {
            Log::info("FCM: HTTP v1 failed, trying legacy API");
            $result = $this->sendViaLegacy($device->fcm_token, $title, $body, $command, $device->id);
        }

        return $result;
    }

    /**
     * Send via FCM HTTP v1 API
     */
    private function sendViaHttpV1(string $token, string $title, string $body, string $command, int $deviceId): bool
    {
        $accessToken = $this->getAccessToken();

        if (!$accessToken) {
            Log::warning('FCM: Failed to get access token, skipping HTTP v1');
            return false;
        }

        $payload = [
            'message' => [
                'token' => $token,
                'notification' => [
                    'title' => $title,
                    'body' => $body,
                ],
                'data' => [
                    'command' => $command,
                    'device_id' => (string) $deviceId,
                    'title' => $title,
                    'body' => $body,
                ],
                'android' => [
                    'priority' => 'high',
                    'notification' => [
                        'channel_id' => 'FcmAlertChannel',
                    ],
                ],
            ],
        ];

        try {
            $response = Http::timeout(10)
                ->withHeaders([
                    'Authorization' => 'Bearer ' . $accessToken,
                    'Content-Type' => 'application/json',
                ])
                ->post("https://fcm.googleapis.com/v1/projects/{$this->projectId}/messages:send", $payload);

            $data = $response->json();

            if ($response->successful() && isset($data['name'])) {
                Log::info('FCM: Sent successfully via HTTP v1', ['name' => $data['name']]);
                return true;
            }

            Log::error('FCM: HTTP v1 failed', ['code' => $response->status(), 'response' => $data]);
            return false;
        } catch (\Exception $e) {
            Log::error('FCM: HTTP v1 exception', ['error' => $e->getMessage()]);
            return false;
        }
    }

    /**
     * Send via legacy FCM API (v1 compatibility)
     */
    private function sendViaLegacy(string $token, string $title, string $body, string $command, int $deviceId): bool
    {
        if (empty($this->serverKey)) {
            Log::error('FCM: No server key configured');
            return false;
        }

        $payload = [
            'to' => $token,
            'priority' => 'high',
            'notification' => [
                'title' => $title,
                'body' => $body,
                'sound' => 'default',
                'click_action' => 'FLUTTER_NOTIFICATION_CLICK',
            ],
            'data' => [
                'command' => $command,
                'device_id' => (string) $deviceId,
                'title' => $title,
                'body' => $body,
            ],
            'android' => [
                'priority' => 'high',
                'notification' => [
                    'channel_id' => 'FcmAlertChannel',
                    'sound' => 'default',
                ],
            ],
        ];

        try {
            $response = Http::timeout(10)
                ->withHeaders([
                    'Authorization' => 'key=' . $this->serverKey,
                    'Content-Type' => 'application/json',
                ])
                ->post('https://fcm.googleapis.com/fcm/send', $payload);

            $data = $response->json();

            if (isset($data['success']) && $data['success'] > 0) {
                Log::info('FCM: Sent successfully via legacy API');
                return true;
            }

            Log::error('FCM: Legacy API failed', ['response' => $data]);
            return false;
        } catch (\Exception $e) {
            Log::error('FCM: Legacy API exception', ['error' => $e->getMessage()]);
            return false;
        }
    }

    /**
     * Get OAuth2 access token from service account
     */
    private function getAccessToken(): ?string
    {
        if (!file_exists($this->credentialsPath)) {
            Log::warning('FCM: Service account not found at ' . $this->credentialsPath);
            return null;
        }

        try {
            $credentials = json_decode(file_get_contents($this->credentialsPath), true);
            if (!$credentials) return null;

            $jwt = $this->createJwt($credentials);

            $response = Http::asForm()
                ->timeout(10)
                ->post($credentials['token_uri'], [
                    'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                    'assertion' => $jwt,
                ]);

            $data = $response->json();
            return $data['access_token'] ?? null;
        } catch (\Exception $e) {
            Log::error('FCM: Failed to get access token', ['error' => $e->getMessage()]);
            return null;
        }
    }

    /**
     * Create JWT
     */
    private function createJwt(array $credentials): string
    {
        $header = $this->base64UrlEncode(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
        $now = time();

        $claims = $this->base64UrlEncode(json_encode([
            'iss' => $credentials['client_email'],
            'sub' => $credentials['client_email'],
            'aud' => $credentials['token_uri'],
            'iat' => $now,
            'exp' => $now + 3600,
            'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
        ]));

        $input = "$header.$claims";

        $key = openssl_pkey_get_private($credentials['private_key']);
        openssl_sign($input, $signature, $key, OPENSSL_ALGO_SHA256);

        return $input . '.' . $this->base64UrlEncode($signature);
    }

    private function base64UrlEncode(string $data): string
    {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }
}
