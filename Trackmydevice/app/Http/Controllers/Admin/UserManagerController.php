<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;

class UserManagerController extends Controller
{
    private function checkAdmin()
    {
        if (!session('user_logged_in') || session('user_role') !== 'admin') {
            return redirect()->route('login');
        }
        return null;
    }

    public function index(Request $request)
    {
        if ($r = $this->checkAdmin()) return $r;
        $query = User::where('role', 'customer')->withCount('devices');
        if ($request->search) {
            $query->where(function ($q) use ($request) {
                $q->where('name', 'like', "%{$request->search}%")
                  ->orWhere('email', 'like', "%{$request->search}%");
            });
        }
        $users = $query->orderBy('created_at', 'desc')->paginate(20);
        return view('admin.users.index', compact('users'));
    }

    public function update(Request $request, $id)
    {
        if ($r = $this->checkAdmin()) return $r;
        $user = User::findOrFail($id);
        $request->validate([
            'name'      => 'required|string|max:100',
            'phone'     => 'nullable|string|max:20',
            'is_active' => 'nullable|boolean',
        ]);
        $user->update($request->only(['name', 'phone', 'is_active']));
        return redirect()->route('admin.users.index')->with('success', 'User updated.');
    }
}