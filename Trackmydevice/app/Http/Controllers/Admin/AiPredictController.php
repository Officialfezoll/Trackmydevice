<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\AiPredictConfig;
use App\Services\AiPredictionService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;

class AiPredictController extends Controller
{
    private AiPredictionService $aiService;

    public function __construct(AiPredictionService $aiService)
    {
        $this->aiService = $aiService;
    }

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
        $configs = AiPredictConfig::orderBy('created_at', 'desc')->get();
        $activeConfig = AiPredictConfig::where('is_active', true)->first();
        $currentConfig = [
            'api_url' => env('OPENAI_API_URL', 'https://ai.suddiahmad7.workers.dev/v1'),
            'api_key' => env('OPENAI_API_KEY', ''),
            'model' => env('OPENAI_MODEL', 'gpt-4o-mini'),
            'system_prompt' => $activeConfig?->system_prompt ?? '',
        ];
        return view('admin.ai_predict.index', compact('configs', 'currentConfig'));
    }

    public function store(Request $request)
    {
        if ($r = $this->checkAdmin()) return $r;
        $request->validate([
            'name'             => 'required|string|max:255',
            'api_url'          => 'required|url',
            'api_key'          => 'required|string',
            'model'            => 'required|string',
            'system_prompt'    => 'nullable|string',
            'is_active'        => 'nullable|boolean',
        ]);

        AiPredictConfig::create([
            'name'          => $request->name,
            'cf_account_id' => $request->api_url, // Reusing field for API URL
            'cf_api_token'  => $request->api_key,
            'model_id'      => $request->model,
            'system_prompt' => $request->system_prompt ?? 'You are a device tracking AI assistant.',
            'is_active'     => $request->boolean('is_active', true),
        ]);

        return redirect()->route('admin.ai_predict.index')->with('success', 'AI config saved.');
    }

    public function update(Request $request, $id)
    {
        if ($r = $this->checkAdmin()) return $r;
        $config = AiPredictConfig::findOrFail($id);
        $config->update($request->only(['name', 'system_prompt', 'is_active']));
        return redirect()->route('admin.ai_predict.index')->with('success', 'AI config updated.');
    }

    /**
     * Update .env file with new OpenAI settings
     */
    public function updateEnv(Request $request)
    {
        if ($r = $this->checkAdmin()) return $r;

        $request->validate([
            'api_url' => 'required|url',
            'api_key' => 'required|string',
            'model'   => 'required|string',
        ]);

        $envPath = base_path('.env');
        $envContent = file_get_contents($envPath);

        // Update or add OPENAI_API_URL
        if (preg_match('/^OPENAI_API_URL=.*/m', $envContent)) {
            $envContent = preg_replace('/^OPENAI_API_URL=.*/m', "OPENAI_API_URL={$request->api_url}", $envContent);
        } else {
            $envContent .= "\nOPENAI_API_URL={$request->api_url}";
        }

        // Update or add OPENAI_API_KEY
        if (preg_match('/^OPENAI_API_KEY=.*/m', $envContent)) {
            $envContent = preg_replace('/^OPENAI_API_KEY=.*/m', "OPENAI_API_KEY={$request->api_key}", $envContent);
        } else {
            $envContent .= "\nOPENAI_API_KEY={$request->api_key}";
        }

        // Update or add OPENAI_MODEL
        if (preg_match('/^OPENAI_MODEL=.*/m', $envContent)) {
            $envContent = preg_replace('/^OPENAI_MODEL=.*/m', "OPENAI_MODEL={$request->model}", $envContent);
        } else {
            $envContent .= "\nOPENAI_MODEL={$request->model}";
        }

        file_put_contents($envPath, $envContent);

        // Save system_prompt to database
        if ($request->has('system_prompt')) {
            $config = AiPredictConfig::firstOrCreate(
                ['name' => 'default'],
                [
                    'cf_account_id' => $request->api_url,
                    'cf_api_token' => $request->api_key,
                    'model_id' => $request->model,
                    'system_prompt' => $request->system_prompt,
                    'is_active' => true,
                ]
            );
            $config->update(['system_prompt' => $request->system_prompt]);
        }

        return redirect()->route('admin.ai_predict.index')->with('success', 'OpenAI API and system prompt updated successfully.');
    }

    public static function query(string $userMessage): string
    {
        $apiKey = env('OPENAI_API_KEY');
        $apiUrl = env('OPENAI_API_URL', 'https://ai.suddiahmad7.workers.dev/v1');
        $model = env('OPENAI_MODEL', 'gpt-3.5-turbo');

        if (!$apiKey) return 'AI prediction is not configured.';

        try {
            $response = Http::withToken($apiKey)
                ->timeout(15)
                ->post("{$apiUrl}/chat/completions", [
                    'model' => $model,
                    'messages' => [
                        ['role' => 'system', 'content' => 'You are a device tracking AI assistant. Analyze GPS data, detect anomalies, and provide insights.'],
                        ['role' => 'user',   'content' => $userMessage],
                    ],
                    'temperature' => 0.7,
                    'max_tokens' => 300,
                ]);

            if ($response->successful()) {
                $data = $response->json();
                return $data['choices'][0]['message']['content'] ?? 'No response from AI.';
            }
        } catch (\Exception $e) {
            // Silent fail
        }

        return 'AI prediction unavailable.';
    }
}