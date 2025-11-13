package com.wayve.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.wayve.app.service.ContinuousAudioMonitorService

/**
 * Helper object to easily start/stop the Continuous Audio Monitor service
 * 
 * Usage in MainActivity or any Activity:
 * - MonitorStarter.startMonitoring(this)
 * - MonitorStarter.stopMonitoring(this)
 */
object MonitorStarter {
    
    private const val TAG = "MonitorStarter"
    
    /**
     * Start continuous audio monitoring
     * This will launch the always-on detection service
     * Returns true if started successfully, false if permission denied
     */
    fun startMonitoring(context: Context): Boolean {
        // Check for microphone permission first
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasMicPermission) {
            Log.w(TAG, "⚠️ Cannot start continuous monitoring - RECORD_AUDIO permission not granted")
            return false
        }
        
        try {
            val intent = Intent(context, ContinuousAudioMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
                Log.d(TAG, "✅ Started continuous monitoring (foreground service)")
            } else {
                context.startService(intent)
                Log.d(TAG, "✅ Started continuous monitoring (background service)")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start continuous monitoring", e)
            return false
        }
    }
    
    /**
     * Stop continuous audio monitoring
     */
    fun stopMonitoring(context: Context) {
        try {
            val intent = Intent(context, ContinuousAudioMonitorService::class.java)
            val stopped = context.stopService(intent)
            if (stopped) {
                Log.d(TAG, "✅ Stopped continuous monitoring")
            } else {
                Log.d(TAG, "⚠️ Service was not running")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop continuous monitoring", e)
        }
    }
    
    /**
     * Check if service is currently running
     */
    fun isMonitoring(): Boolean {
        return ContinuousAudioMonitorService.isServiceRunning
    }
}

