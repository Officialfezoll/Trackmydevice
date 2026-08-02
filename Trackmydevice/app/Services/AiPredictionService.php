<?php

namespace App\Services;

use App\Models\Device;
use App\Models\DeviceLocation;
use App\Models\AiPredictConfig;
use Carbon\Carbon;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Cache;

class AiPredictionService
{
    private const CACHE_PREFIX = 'ai_prediction:';
    private const HISTORY_DAYS = 7;
    private const MIN_HISTORY_POINTS = 10;

    /**
     * Predict next location based on historical patterns
     */
    public function predictNextLocation(Device $device, int $minutesAhead = 30): ?array
    {
        // Get historical route data
        $history = $this->getHistoricalRoute($device);

        if ($history['points'] < self::MIN_HISTORY_POINTS) {
            return null; // Not enough data
        }

        // Calculate movement patterns
        $pattern = $this->analyzeMovementPattern($history['locations']);

        if (!$pattern) {
            return null;
        }

        // Predict position
        $lastLocation = end($history['locations']);
        $predictedLat = $lastLocation->lat + ($pattern['avg_dlat'] * $minutesAhead);
        $predictedLng = $lastLocation->lng + ($pattern['avg_dlng'] * $minutesAhead);

        // Calculate confidence based on pattern consistency
        $confidence = $this->calculatePredictionConfidence($history['locations'], $pattern);

        return [
            'device_id'   => $device->id,
            'current_lat' => $lastLocation->lat,
            'current_lng' => $lastLocation->lng,
            'predicted_lat' => round($predictedLat, 6),
            'predicted_lng' => round($predictedLng, 6),
            'confidence'  => $confidence,
            'estimated_arrival' => Carbon::now()->addMinutes($minutesAhead)->toIso8601String(),
            'pattern_type' => $pattern['type'],
            'speed_kmh'   => $pattern['avg_speed'],
        ];
    }

    /**
     * Detect anomalies in device movement
     */
    public function detectAnomalies(Device $device, array $recentLocations = []): array
    {
        $anomalies = [];

        // Get historical data for comparison
        $history = $this->getHistoricalRoute($device);

        if ($history['points'] < self::MIN_HISTORY_POINTS) {
            return []; // Not enough data
        }

        // If no recent locations provided, get last hour
        if (empty($recentLocations)) {
            $recentLocations = DeviceLocation::where('device_id', $device->id)
                ->where('recorded_at', '>=', Carbon::now()->subHours(2))
                ->orderBy('recorded_at', 'asc')
                ->get()
                ->toArray();
        }

        if (count($recentLocations) < 2) {
            return [];
        }

        // Check for various anomalies
        $anomalies = array_merge($anomalies, $this->checkRouteDeviation($device, $recentLocations, $history));
        $anomalies = array_merge($anomalies, $this->checkSpeedAnomaly($device, $recentLocations));
        $anomalies = array_merge($anomalies, $this->checkSuddenStop($device, $recentLocations));
        $anomalies = array_merge($anomalies, $this->checkUnusualHours($device, $recentLocations, $history));

        return $anomalies;
    }

    /**
     * Get historical route data
     */
    private function getHistoricalRoute(Device $device): array
    {
        $cacheKey = self::CACHE_PREFIX . "history:{$device->id}";

        $cached = Cache::get($cacheKey);
        if ($cached) {
            return $cached;
        }

        $locations = DeviceLocation::where('device_id', $device->id)
            ->where('recorded_at', '>=', Carbon::now()->subDays(self::HISTORY_DAYS))
            ->orderBy('recorded_at', 'asc')
            ->get(['lat', 'lng', 'speed', 'source', 'recorded_at']);

        $result = [
            'locations' => $locations,
            'points' => $locations->count(),
        ];

        Cache::put($cacheKey, $result, 3600); // Cache for 1 hour

        return $result;
    }

    /**
     * Analyze movement patterns from historical data
     */
    private function analyzeMovementPattern($locations): ?array
    {
        if (count($locations) < 2) return null;

        $totalDistance = 0;
        $totalSpeed = 0;
        $totalDlat = 0;
        $totalDlng = 0;
        $validPoints = 0;
        $movingPoints = 0;

        for ($i = 1; $i < count($locations); $i++) {
            $prev = $locations[$i - 1];
            $curr = $locations[$i];

            // Calculate distance
            $distance = $this->haversineDistance(
                $prev->lat, $prev->lng,
                $curr->lat, $curr->lng
            );
            $totalDistance += $distance;

            // Calculate time difference
            $timeDiff = $curr->recorded_at->diffInMinutes($prev->recorded_at);
            if ($timeDiff > 0) {
                $speed = ($distance / $timeDiff) * 60; // km/h
                if ($speed > 0 && $speed < 200) { // Filter outliers
                    $totalSpeed += $speed;
                    $movingPoints++;
                }
            }

            // Direction
            $totalDlat += ($curr->lat - $prev->lat);
            $totalDlng += ($curr->lng - $prev->lng);
            $validPoints++;
        }

        if ($validPoints === 0) return null;

        return [
            'type' => $this->classifyPattern($totalSpeed / max(1, $movingPoints)),
            'avg_speed' => round($totalSpeed / max(1, $movingPoints), 1),
            'avg_dlat' => $totalDlat / $validPoints,
            'avg_dlng' => $totalDlng / $validPoints,
            'total_distance' => round($totalDistance, 2),
        ];
    }

    /**
     * Classify movement pattern type
     */
    private function classifyPattern(float $avgSpeed): string
    {
        if ($avgSpeed < 1) return 'stationary';
        if ($avgSpeed < 10) return 'walking';
        if ($avgSpeed < 40) return 'driving';
        if ($avgSpeed < 100) return 'highway';
        return 'unknown';
    }

    /**
     * Calculate prediction confidence
     */
    private function calculatePredictionConfidence($locations, array $pattern): float
    {
        if (count($locations) < 10) return 0.3;

        // Calculate variance in speed
        $speeds = [];
        for ($i = 1; $i < count($locations); $i++) {
            $distance = $this->haversineDistance(
                $locations[$i-1]->lat, $locations[$i-1]->lng,
                $locations[$i]->lat, $locations[$i]->lng
            );
            $timeDiff = max(1, $locations[$i]->recorded_at->diffInMinutes($locations[$i-1]->recorded_at));
            $speed = ($distance / $timeDiff) * 60;
            if ($speed > 0 && $speed < 200) {
                $speeds[] = $speed;
            }
        }

        if (empty($speeds)) return 0.4;

        $mean = array_sum($speeds) / count($speeds);
        $variance = 0;
        foreach ($speeds as $speed) {
            $variance += pow($speed - $mean, 2);
        }
        $stdDev = sqrt($variance / count($speeds));

        // Lower variance = higher confidence
        $cv = $mean > 0 ? $stdDev / $mean : 1; // Coefficient of variation
        $confidence = max(0.2, min(0.95, 1 - $cv));

        return round($confidence, 2);
    }

    /**
     * Check if current route deviates from historical patterns
     */
    private function checkRouteDeviation(Device $device, array $recentLocations, array $history): array
    {
        $anomalies = [];

        if (count($history['locations']) < 5 || count($recentLocations) < 2) {
            return $anomalies;
        }

        // Calculate centroid of historical locations
        $centroid = $this->calculateCentroid($history['locations']);

        // Check recent locations against centroid
        foreach ($recentLocations as $location) {
            $distance = $this->haversineDistance(
                $location['lat'], $location['lng'],
                $centroid['lat'], $centroid['lng']
            );

            // If more than 5km from usual area, it's unusual
            if ($distance > 5) {
                $anomalies[] = [
                    'type' => 'route_deviation',
                    'severity' => $distance > 20 ? 'high' : 'medium',
                    'message' => "Device is {$distance}km away from usual area",
                    'distance_km' => round($distance, 2),
                    'detected_at' => Carbon::now()->toIso8601String(),
                ];
                break; // Only report once
            }
        }

        return $anomalies;
    }

    /**
     * Check for unusual speed patterns
     */
    private function checkSpeedAnomaly(Device $device, array $locations): array
    {
        $anomalies = [];

        for ($i = 1; $i < count($locations); $i++) {
            $prev = $locations[$i - 1];
            $curr = $locations[$i];

            $distance = $this->haversineDistance(
                $prev['lat'], $prev['lng'],
                $curr['lat'], $curr['lng']
            );

            $timeDiff = max(1, Carbon::parse($curr['recorded_at'])->diffInMinutes(Carbon::parse($prev['recorded_at'])));
            $speed = ($distance / $timeDiff) * 60; // km/h

            // Speed anomaly: > 200 km/h is impossible for most vehicles
            if ($speed > 200) {
                $anomalies[] = [
                    'type' => 'speed_anomaly',
                    'severity' => 'high',
                    'message' => "Unusual speed detected: " . round($speed, 1) . " km/h",
                    'speed_kmh' => round($speed, 1),
                    'detected_at' => Carbon::now()->toIso8601String(),
                ];
            }
        }

        return $anomalies;
    }

    /**
     * Check for sudden stops (potential theft or breakdown)
     */
    private function checkSuddenStop(Device $device, array $locations): array
    {
        $anomalies = [];

        if (count($locations) < 3) return $anomalies;

        // Check last few points
        $lastPoints = array_slice($locations, -3);

        $wasMoving = false;
        $stopDuration = 0;

        for ($i = 1; $i < count($lastPoints); $i++) {
            $prev = $lastPoints[$i - 1];
            $curr = $lastPoints[$i];

            $distance = $this->haversineDistance(
                $prev['lat'], $prev['lng'],
                $curr['lat'], $curr['lng']
            );

            $timeDiff = Carbon::parse($curr['recorded_at'])->diffInMinutes(Carbon::parse($prev['recorded_at']));

            if ($distance < 0.05 && $timeDiff > 5) { // Less than 50m in 5+ minutes
                if ($wasMoving) {
                    $stopDuration += $timeDiff;
                }
            } else if ($distance > 0.1) {
                $wasMoving = true;
            }
        }

        if ($stopDuration > 30) { // Stopped for more than 30 minutes
            $anomalies[] = [
                'type' => 'sudden_stop',
                'severity' => 'medium',
                'message' => "Device stopped for {$stopDuration} minutes",
                'duration_minutes' => $stopDuration,
                'detected_at' => Carbon::now()->toIso8601String(),
            ];
        }

        return $anomalies;
    }

    /**
     * Check if device is moving at unusual hours
     */
    private function checkUnusualHours(Device $device, array $locations, array $history): array
    {
        $anomalies = [];

        if (empty($locations)) return $anomalies;

        $lastLocation = end($locations);
        $hour = Carbon::parse($lastLocation['recorded_at'])->hour;

        // Check if moving between 2am and 5am (unusual hours)
        if ($hour >= 2 && $hour <= 5) {
            // Check if this is unusual for this device
            $historicalHours = $this->getTypicalMovementHours($history['locations']);

            if (!in_array($hour, $historicalHours)) {
                $anomalies[] = [
                    'type' => 'unusual_hours',
                    'severity' => 'low',
                    'message' => "Device moving during unusual hours ({$hour}:00)",
                    'hour' => $hour,
                    'detected_at' => Carbon::now()->toIso8601String(),
                ];
            }
        }

        return $anomalies;
    }

    /**
     * Get typical movement hours from history
     */
    private function getTypicalMovementHours($locations): array
    {
        $hours = [];

        foreach ($locations as $location) {
            $hour = $location->recorded_at->hour;
            $distance = 0;

            // Simple check - if location changes, it's moving
            $hours[$hour] = ($hours[$hour] ?? 0) + 1;
        }

        // Return hours with significant activity
        return array_keys(array_filter($hours, fn($count) => $count > 2));
    }

    /**
     * Calculate centroid of locations
     */
    private function calculateCentroid($locations): array
    {
        $sumLat = 0;
        $sumLng = 0;

        foreach ($locations as $location) {
            $sumLat += $location->lat;
            $sumLng += $location->lng;
        }

        return [
            'lat' => $sumLat / count($locations),
            'lng' => $sumLng / count($locations),
        ];
    }

    /**
     * Haversine distance calculation (km)
     */
    private function haversineDistance(float $lat1, float $lng1, float $lat2, float $lng2): float
    {
        $earthRadius = 6371; // km

        $dLat = deg2rad($lat2 - $lat1);
        $dLng = deg2rad($lng2 - $lng1);

        $a = sin($dLat / 2) * sin($dLat / 2) +
             cos(deg2rad($lat1)) * cos(deg2rad($lat2)) *
             sin($dLng / 2) * sin($dLng / 2);

        $c = 2 * atan2(sqrt($a), sqrt(1 - $a));

        return $earthRadius * $c;
    }

    /**
     * Use OpenAI compatible API for advanced predictions
     */
    public function getAiPrediction(Device $device): ?string
    {
        $apiKey = env('OPENAI_API_KEY');
        $apiUrl = env('OPENAI_API_URL', 'https://ai.suddiahmad7.workers.dev/v1');
        $model = env('OPENAI_MODEL', 'gpt-3.5-turbo');

        if (!$apiKey) {
            Log::warning("OpenAI API key not configured in .env");
            return null;
        }

        try {
            $history = $this->getHistoricalRoute($device);
            $recentLocations = DeviceLocation::where('device_id', $device->id)
                ->where('recorded_at', '>=', Carbon::now()->subHours(6))
                ->orderBy('recorded_at', 'desc')
                ->limit(10)
                ->get();

            $prompt = $this->buildAiPrompt($device, $history, $recentLocations);
            $systemPrompt = "You are a GPS tracking AI assistant. Analyze device movement data, detect anomalies, predict routes, and provide safety assessments. Be concise and actionable. Respond in the same language as the user's device name suggests (e.g., Swahili if device names are Swahili).";

            $response = Http::withToken($apiKey)
                ->timeout(30)
                ->post("{$apiUrl}/chat/completions", [
                    'model' => $model,
                    'messages' => [
                        ['role' => 'system', 'content' => $systemPrompt],
                        ['role' => 'user', 'content' => $prompt],
                    ],
                    'temperature' => 0.7,
                    'max_tokens' => 500,
                ]);

            if ($response->successful()) {
                $data = $response->json();
                return $data['choices'][0]['message']['content'] ?? null;
            }

            Log::error("OpenAI API error: " . $response->body());
        } catch (\Exception $e) {
            Log::error("AI prediction error: " . $e->getMessage());
        }

        return null;
    }

    /**
     * Build AI prompt for route analysis
     */
    private function buildAiPrompt(Device $device, array $history, $recentLocations): string
    {
        $locationSummary = "Recent locations (last 6 hours):\n";
        foreach ($recentLocations as $loc) {
            $locationSummary .= "- " . $loc->recorded_at->format('H:i') . ": {$loc->lat}, {$loc->lng}\n";
        }

        $historySummary = count($history['locations']) > 0
            ? "Historical data points: {$history['points']} locations over the past week"
            : "No historical data available";

        return "Analyze this GPS tracking data for device '{$device->name}' (ID: {$device->id}, Type: {$device->type}).

{$locationSummary}

{$historySummary}

Provide a concise analysis covering:
1. Current movement pattern (stationary/walking/driving/highway)
2. Any unusual behavior detected (route deviation, speed anomalies, unusual hours)
3. Predicted next location area
4. Safety assessment and recommendations

Keep response under 200 words. Be specific with coordinates when predicting.";
    }

    /**
     * Use OpenAI compatible API for chatbot responses
     */
    public function chat(string $userMessage, ?Device $device = null): string
    {
        $apiKey = env('OPENAI_API_KEY');
        $apiUrl = env('OPENAI_API_URL', 'https://ai.suddiahmad7.workers.dev/v1');
        $model = env('OPENAI_MODEL', 'gpt-3.5-turbo');

        if (!$apiKey) {
            return 'AI chatbot is not configured. Please set OPENAI_API_KEY in .env';
        }

        $systemPrompt = $this->getSystemPrompt($device);

        try {
            $response = Http::withToken($apiKey)
                ->timeout(30)
                ->post("{$apiUrl}/chat/completions", [
                    'model' => $model,
                    'messages' => [
                        ['role' => 'system', 'content' => $systemPrompt],
                        ['role' => 'user', 'content' => $userMessage],
                    ],
                    'temperature' => 0.7,
                    'max_tokens' => 300,
                ]);

            if ($response->successful()) {
                $data = $response->json();
                return $data['choices'][0]['message']['content'] ?? 'No response from AI.';
            }

            Log::error("OpenAI chat error: " . $response->body());
        } catch (\Exception $e) {
            Log::error("AI chat error: " . $e->getMessage());
        }

        return 'AI service is currently unavailable. Please try again later.';
    }

    private function getSystemPrompt(?Device $device = null): string
    {
        $prompt = "You are TrackBot, a GPS device tracking assistant for the TrackMyDevice system.

CRITICAL RULES:
1. NEVER assume or predict what the user wants. If the request is unclear, ASK for clarification.
2. NEVER fabricate device data. Only report what you know from the device context provided.
3. If you don't know something, say so honestly. Don't guess.
4. Respond in the SAME language the user uses (Swahili, English, etc.).
5. Keep responses SHORT and CONCISE - 1-3 sentences maximum.
6. For commands (alarm, lock, unlock, locate, silent, etc.), just confirm the action - don't explain how it works.
7. If user says 'no', 'hapana', 'sivyo' - acknowledge and stop. Don't assume they meant something else.
8. If user says something you don't understand, ask: 'Sorry, I didn\'t understand. Can you rephrase?'
9. Never say 'It seems you meant...' - you are not a mind reader. ASK instead.

CAPABILITIES (you can help with):
- Device status: 'status', 'battery level', 'is device online?'
- Commands: 'send alarm', 'stop alarm', 'lock', 'unlock', 'locate', 'silent', 'hide app'
- Alerts: 'show alerts', 'any warnings?'
- General: 'help', 'what can you do?'

DEVICE CONTEXT (if available):";

        if ($device) {
            $prompt .= "\n- Name: {$device->name}";
            $prompt .= "\n- Type: {$device->type}";
            $prompt .= "\n- Battery: " . ($device->battery_level ?? 'unknown') . "%";
            $prompt .= "\n- Online: " . ($device->is_online ? 'Yes' : 'No');
            $prompt .= "\n- Status: {$device->status}";
            if ($device->last_lat && $device->last_lng) {
                $prompt .= "\n- Last location: {$device->last_lat}, {$device->last_lng}";
            }
            if ($device->last_seen_at) {
                $prompt .= "\n- Last seen: {$device->last_seen_at->diffForHumans()}";
            }
        } else {
            $prompt .= "\nNo device selected. Ask user to select a device first.";
        }

        return $prompt;
    }
}