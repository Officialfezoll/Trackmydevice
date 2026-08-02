<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Device;
use App\Services\AiPredictionService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;

class PredictionController extends Controller
{
    private AiPredictionService $predictionService;

    public function __construct(AiPredictionService $predictionService)
    {
        $this->predictionService = $predictionService;
    }

    /**
     * Get next location prediction
     */
    public function predict(Device $device)
    {
        $this->authorizeDevice($device);

        $minutesAhead = request()->input('minutes', 30);
        $prediction = $this->predictionService->predictNextLocation($device, $minutesAhead);

        return response()->json([
            'success' => true,
            'prediction' => $prediction,
            'has_enough_data' => $prediction !== null,
        ]);
    }

    /**
     * Detect anomalies
     */
    public function anomalies(Device $device)
    {
        $this->authorizeDevice($device);

        $anomalies = $this->predictionService->detectAnomalies($device);

        return response()->json([
            'success' => true,
            'anomalies' => $anomalies,
            'anomaly_count' => count($anomalies),
        ]);
    }

    /**
     * Get AI-powered insights
     */
    public function insights(Device $device)
    {
        $this->authorizeDevice($device);

        $prediction = $this->predictionService->predictNextLocation($device);
        $anomalies = $this->predictionService->detectAnomalies($device);
        $aiInsight = $this->predictionService->getAiPrediction($device);

        return response()->json([
            'success' => true,
            'prediction' => $prediction,
            'anomalies' => $anomalies,
            'ai_insight' => $aiInsight,
        ]);
    }

    /**
     * Batch prediction for all user devices
     */
    public function batchPredict()
    {
        $userId = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        $query = Device::where('is_active', true);
        if (!$isAdmin) {
            $query->where('user_id', $userId);
        }

        $devices = $query->get();
        $results = [];

        foreach ($devices as $device) {
            $prediction = $this->predictionService->predictNextLocation($device);
            $anomalies = $this->predictionService->detectAnomalies($device);

            $results[] = [
                'device_id' => $device->id,
                'device_name' => $device->name,
                'prediction' => $prediction,
                'anomalies' => $anomalies,
                'has_anomalies' => !empty($anomalies),
            ];
        }

        return response()->json([
            'success' => true,
            'devices' => $results,
            'summary' => [
                'total_devices' => count($results),
                'devices_with_predictions' => collect($results)->whereNotNull('prediction')->count(),
                'devices_with_anomalies' => collect($results)->where('has_anomalies', true)->count(),
            ],
        ]);
    }

    private function authorizeDevice(Device $device): void
    {
        $userId = session('user_id');
        $isAdmin = session('user_role') === 'admin';

        if (!$isAdmin && $device->user_id !== $userId) {
            abort(403, 'Unauthorized access to device');
        }
    }
}