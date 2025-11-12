package com.wayve.app.service

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wayve.app.MainActivity
import com.wayve.app.R
import com.wayve.app.data.NowPlayingData
import com.wayve.app.data.NowPlayingParser
import com.wayve.app.data.NowPlayingTrack
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Now Playing Detection Service - Notification Listener
 * Monitors Google Pixel's Now Playing notifications for ambient music detection
 * Captures songs detected from external sources (speakers, TV, radio, etc.)
 */
class NowPlayingMonitorService : NotificationListenerService() {
    
    private val TAG = "NowPlayingMonitor"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val addedTracks = mutableSetOf<String>()
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        var isServiceRunning = false
        var isAutoDetectionEnabled = false
        var onNewTrackDetected: ((NowPlayingTrack) -> Unit)? = null
        
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "wayve_nowplaying_monitor"
    }
    
    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Log.d(TAG, "🎵 Now Playing Detection started - Monitoring ambient music!")
        
        // Create notification channel
        createNotificationChannel()
        
        // Start as foreground service
        startForegroundService()
        
        // Acquire wake lock to work during device sleep
        acquireWakeLock()
        
        // Load existing tracks
        loadExistingTracks()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        
        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        scope.cancel()
        Log.d(TAG, "NowPlayingMonitorService stopped")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
    
    /**
     * Called when the NotificationListenerService is connected
     * This is where we can get existing/intercepted notifications
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "✅ NotificationListener connected!")
        
        // Get all active notifications (including intercepted ones)
        try {
            val activeNotifications = activeNotifications
            Log.d(TAG, "Found ${activeNotifications?.size ?: 0} active notifications")
            
            activeNotifications?.forEach { sbn ->
                if (sbn.packageName == "com.google.android.as") {
                    Log.d(TAG, "Found existing Now Playing notification on connect")
                    processNowPlayingNotification(sbn)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active notifications", e)
        }
    }
    
    /**
     * Called when any notification is posted
     * This captures Now Playing notifications (Pixel devices only)
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!isAutoDetectionEnabled) return
        
        sbn?.let { notification ->
            val pkg = notification.packageName
            
            // Capture Now Playing notifications on Pixel devices
            if (pkg == "com.google.android.as") {
                Log.d(TAG, "🎵 Now Playing notification detected!")
                processNowPlayingNotification(notification)
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Log removal for debugging
        if (sbn?.packageName == "com.google.android.as" && isAutoDetectionEnabled) {
            Log.d(TAG, "Now Playing notification removed")
        }
    }
    
    /**
     * Process a Now Playing notification to extract song information
     */
    private fun processNowPlayingNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification
            val extras = notification.extras
            
            // Log all extras for debugging
            Log.d(TAG, "=== Now Playing Notification Extras ===")
            extras.keySet().forEach { key ->
                val value = extras.get(key)
                Log.d(TAG, "  $key = $value")
            }
            
            // Extract title and artist from notification
            // Now Playing uses standard notification fields
            val title = extras.getCharSequence("android.title")?.toString()
            val text = extras.getCharSequence("android.text")?.toString()
            val subText = extras.getCharSequence("android.subText")?.toString()
            val infoText = extras.getCharSequence("android.infoText")?.toString()
            val bigText = extras.getCharSequence("android.bigText")?.toString()
            
            Log.d(TAG, "Title: $title")
            Log.d(TAG, "Text: $text")
            Log.d(TAG, "SubText: $subText")
            Log.d(TAG, "InfoText: $infoText")
            Log.d(TAG, "BigText: $bigText")
            
            // Now Playing notification structure:
            // - Title format: "[Song Name] by [Artist Name]"
            // - Text: "Tap to see your song history" (generic, not useful)
            // We need to parse the title to extract song and artist
            val songTitle: String?
            val songArtist: String
            
            if (title != null && title.contains(" by ", ignoreCase = true)) {
                // Parse "Song by Artist" format
                val parts = title.split(" by ", ignoreCase = true, limit = 2)
                songTitle = parts[0].trim()
                songArtist = if (parts.size > 1) parts[1].trim() else "Unknown Artist"
                
                Log.d(TAG, "✅ Parsed -> Song: '$songTitle' | Artist: '$songArtist'")
            } else {
                // Fallback to old method if format is different
                songTitle = title?.takeIf { it.isNotBlank() }
                songArtist = text?.takeIf { it.isNotBlank() && it != "Tap to see your song history" } 
                    ?: subText?.takeIf { it.isNotBlank() } 
                    ?: "Unknown Artist"
                
                Log.d(TAG, "⚠️ Non-standard format -> Song: '$songTitle' | Artist: '$songArtist'")
            }
            
            if (songTitle != null) {
                // Check if this is a valid song detection
                if (isValidSongDetection(songTitle, songArtist)) {
                    val track = NowPlayingTrack(
                        title = songTitle,
                        artist = songArtist,
                        time = getCurrentTime(),
                        date = LocalDate.now().toString(),
                        favorited = false
                    )
                    
                    // Check if we already added this track
                    val trackKey = "${track.title}|${track.artist}"
                    if (!addedTracks.contains(trackKey)) {
                        addedTracks.add(trackKey)
                        
                        Log.d(TAG, "✅ NEW TRACK DETECTED: ${track.title} by ${track.artist}")
                        
                        // Add to library
                        addTrackToLibrary(track)
                        
                        // Notify callback
                        onNewTrackDetected?.invoke(track)
                        
                        // Update notification
                        updateNotification(track)
                    } else {
                        Log.d(TAG, "Track already in library: $trackKey")
                    }
                } else {
                    Log.d(TAG, "Invalid song detection (system message): $songTitle")
                }
            } else {
                Log.d(TAG, "No valid song title found in notification")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Now Playing notification", e)
        }
    }
    
    /**
     * Create notification channel for foreground service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Now Playing Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors for Now Playing songs in the background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Start the service in foreground mode
     */
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wayve Now Playing Detection")
            .setContentText("Monitoring ambient music...")
            .setSmallIcon(R.drawable.music)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * Acquire wake lock to keep service running during device sleep
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Wayve::NowPlayingMonitorWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours
        Log.d(TAG, "Wake lock acquired")
    }
    
    /**
     * Update foreground notification with current song
     */
    private fun updateNotification(track: NowPlayingTrack) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎵 Detected!")
            .setContentText("${track.title} - ${track.artist}")
            .setSmallIcon(R.drawable.music)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Check if the detection is a valid song (not a system message)
     */
    private fun isValidSongDetection(title: String, artist: String): Boolean {
        val invalidPatterns = listOf(
            "Listening",
            "Now Playing",
            "Searching",
            "No match",
            "Not found",
            "Error",
            "Loading",
            "Detecting"
        )
        
        val titleLower = title.lowercase()
        val artistLower = artist.lowercase()
        
        return invalidPatterns.none { 
            titleLower.contains(it.lowercase()) || 
            artistLower.contains(it.lowercase())
        } && title.length >= 2 && artist.length >= 2
    }
    
    /**
     * Get current time in Now Playing format
     */
    private fun getCurrentTime(): String {
        val now = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
        return now.format(formatter)
    }
    
    /**
     * Load existing tracks from SharedPreferences
     */
    private fun loadExistingTracks() {
        try {
            val sharedPrefs = getSharedPreferences("wayve_prefs", MODE_PRIVATE)
            val savedData = sharedPrefs.getString("nowplaying_data", null)
            
            if (savedData != null) {
                val data = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString<NowPlayingData>(savedData)
                
                data.tracks.forEach { track ->
                    val key = "${track.title}|${track.artist}"
                    addedTracks.add(key)
                }
                
                Log.d(TAG, "Loaded ${addedTracks.size} existing tracks")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading existing tracks", e)
        }
    }
    
    /**
     * Add a newly detected track to the library
     */
    private fun addTrackToLibrary(track: NowPlayingTrack) {
        scope.launch(Dispatchers.IO) {
            try {
                val sharedPrefs = getSharedPreferences("wayve_prefs", MODE_PRIVATE)
                val savedData = sharedPrefs.getString("nowplaying_data", null)
                
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                }
                
                val currentData = if (savedData != null) {
                    json.decodeFromString<NowPlayingData>(savedData)
                } else {
                    NowPlayingData(
                        exported = java.time.Instant.now().toString(),
                        source = "com.google.android.as (Auto-detected)",
                        device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                        method = "Notification Listener (Instant)",
                        tracks = emptyList()
                    )
                }
                
                // Add new track to the beginning
                val updatedTracks = listOf(track) + currentData.tracks
                
                // Recalculate statistics
                val stats = NowPlayingParser.calculateStats(updatedTracks)
                
                val updatedData = currentData.copy(
                    tracks = updatedTracks,
                    statistics = stats,
                    method = "Now Playing (Ambient Detection)"
                )
                
                // Save back to SharedPreferences
                val jsonString = json.encodeToString(
                    NowPlayingData.serializer(),
                    updatedData
                )
                sharedPrefs.edit().putString("nowplaying_data", jsonString).apply()
                
                Log.d(TAG, "✅ Track added to library: ${track.title}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding track to library", e)
            }
        }
    }
    
    /**
     * Clear the added tracks cache
     */
    fun clearCache() {
        addedTracks.clear()
        Log.d(TAG, "Cache cleared")
    }
}
