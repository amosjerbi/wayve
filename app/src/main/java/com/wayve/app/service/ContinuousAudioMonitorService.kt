package com.wayve.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wayve.app.MainActivity
import com.wayve.app.R
import com.wayve.app.data.NowPlayingTrack
import com.wayve.app.data.NowPlayingData
import kotlinx.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Continuous Audio Monitor Service - AmbientMusicMod-style detection
 * 
 * This service runs continuously in the background, monitoring ambient audio
 * and detecting songs in real-time using ACRCloud fingerprinting.
 * 
 * Features:
 * - Continuous audio monitoring (like AmbientMusicMod)
 * - Automatic song detection every 30 seconds
 * - Lock screen notifications
 * - Power-efficient sampling strategies
 * - Works on ALL Android devices
 */
class ContinuousAudioMonitorService : Service() {
    
    private val TAG = "ContinuousAudioMonitor"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var monitoringJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val client = OkHttpClient()
    private val detectedTracks = mutableSetOf<String>()
    
    // Shazam API credentials - loaded from SharedPreferences
    private fun getShazamApiUrl(): String {
        val prefs = getSharedPreferences("wayve_prefs", MODE_PRIVATE)
        return prefs.getString("shazam_api_url", "https://shazam.p.rapidapi.com/songs/v2/detect") ?: "https://shazam.p.rapidapi.com/songs/v2/detect"
    }
    
    private fun getShazamApiKey(): String {
        val prefs = getSharedPreferences("wayve_prefs", MODE_PRIVATE)
        return prefs.getString("shazam_api_key", "") ?: ""
    }
    
    private fun getShazamApiHost(): String {
        val prefs = getSharedPreferences("wayve_prefs", MODE_PRIVATE)
        return prefs.getString("shazam_api_host", "shazam.p.rapidapi.com") ?: "shazam.p.rapidapi.com"
    }
    
    // Audio recording settings (optimized for song detection accuracy)
    private val SAMPLE_RATE = 44100 // CD quality for better detection
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val RECORD_DURATION_MS = 8000 // 8 seconds per sample for better detection
    
    // Monitoring settings
    private val MONITORING_INTERVAL_MS = 30000L // Check every 30 seconds
    private val COOLDOWN_PERIOD_MS = 120000L // 2 minutes between same song detections
    
    companion object {
        var isServiceRunning = false
        var isMonitoring = false
        var onNewTrackDetected: ((NowPlayingTrack) -> Unit)? = null
        
        private const val NOTIFICATION_ID = 12346
        private const val CHANNEL_ID = "wayve_continuous_monitor"
        private const val DETECTION_CHANNEL_ID = "wayve_song_detections"
        
        const val ACTION_TRACK_DETECTED = "com.wayve.app.TRACK_DETECTED"
        const val EXTRA_TRACK_TITLE = "track_title"
        const val EXTRA_TRACK_ARTIST = "track_artist"
        
        private var lastDetectionTime = mutableMapOf<String, Long>()
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Check for microphone permission before starting
        if (!hasPermission()) {
            Log.e(TAG, "❌ RECORD_AUDIO permission not granted - service cannot start")
            stopSelf()
            return
        }
        
        try {
            isServiceRunning = true
            Log.d(TAG, "🎵 Continuous Audio Monitor started - AmbientMusicMod-style detection")
            
            // Create notification channels
            createNotificationChannels()
            
            // Start as foreground service
            startForegroundService()
            
            // Acquire wake lock for continuous monitoring
            acquireWakeLock()
            
            // Load existing tracks
            loadExistingTracks()
            
            // Update missing album art for old tracks (async, doesn't block startup)
            updateMissingAlbumArt()
            
            // Start continuous monitoring
            startContinuousMonitoring()
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException in onCreate - permission issue", e)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreate", e)
            stopSelf()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with intent: $intent")
        // Return START_STICKY to ensure the service restarts if killed
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isMonitoring = false
        
        // Stop monitoring
        monitoringJob?.cancel()
        monitoringJob = null
        
        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        scope.cancel()
        Log.d(TAG, "ContinuousAudioMonitorService stopped")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Create notification channels for service and detections
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Service notification channel (minimal)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Music Detection Service",
                NotificationManager.IMPORTANCE_LOW // Low importance, won't make sound
            ).apply {
                description = "Shows when Wayve is listening for music in the background"
                setShowBadge(false)
                setSound(null, null) // No sound
                enableVibration(false) // No vibration
                enableLights(false) // No LED
            }
            notificationManager.createNotificationChannel(serviceChannel)
            
            // Detection notification channel (Minimal like Now Playing)
            val detectionChannel = NotificationChannel(
                DETECTION_CHANNEL_ID,
                "Song Detections",
                NotificationManager.IMPORTANCE_DEFAULT // Default importance for minimal appearance
            ).apply {
                description = "Shows detected songs on lock screen"
                setShowBadge(false) // No badge needed
                enableLights(false) // No lights
                enableVibration(false) // Silent like Now Playing
                setSound(null, null) // Completely silent
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Always show on lock screen
                setBypassDnd(false) // Don't bypass Do Not Disturb
            }
            notificationManager.createNotificationChannel(detectionChannel)
        }
    }
    
    /**
     * Start the service in foreground mode with invisible notification
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
            .setContentTitle("Listening for music")
            .setContentText("Tap to open Wayve")
            .setSmallIcon(R.drawable.music)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * Acquire wake lock for continuous operation
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Wayve::ContinuousMonitorWakeLock"
        )
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours
        Log.d(TAG, "Wake lock acquired for continuous monitoring")
    }
    
    /**
     * Start continuous audio monitoring loop
     */
    private fun startContinuousMonitoring() {
        isMonitoring = true
        
        monitoringJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "🎤 Continuous monitoring started - checking every ${MONITORING_INTERVAL_MS/1000}s")
            
            while (isActive && isMonitoring) {
                try {
                    // Check if we have microphone permission
                    if (!hasPermission()) {
                        Log.w(TAG, "⚠️ Microphone permission not granted")
                        delay(60000) // Check again in 1 minute
                        continue
                    }
                    
                    // Record and analyze audio
                    val track = detectSongFromAmbientAudio()
                    
                    if (track != null) {
                        handleDetectedTrack(track)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error during monitoring cycle", e)
                }
                
                // Wait before next check
                delay(MONITORING_INTERVAL_MS)
            }
            
            Log.d(TAG, "Continuous monitoring stopped")
        }
    }
    
    /**
     * Detect song from ambient audio
     */
    private suspend fun detectSongFromAmbientAudio(): NowPlayingTrack? {
        return try {
            Log.d(TAG, "🎤 Recording audio sample...")
            
            // Record audio (don't use withContext to avoid cancellation issues)
            val audioData = recordAudio()
            
            if (audioData == null || audioData.isEmpty()) {
                Log.e(TAG, "Failed to record audio")
                return null
            }
            
            Log.d(TAG, "🔍 Analyzing audio (${audioData.size} bytes)...")
            
            // Send to Shazam API for recognition
            withContext(Dispatchers.IO) {
                recognizeWithShazam(audioData)
            }
            
        } catch (e: CancellationException) {
            Log.w(TAG, "Detection cancelled (service stopping)")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting song", e)
            null
        }
    }
    
    /**
     * Record audio from microphone
     */
    private fun recordAudio(): ByteArray? {
        var audioRecord: AudioRecord? = null
        
        try {
            // Double check permission before recording
            if (!hasPermission()) {
                Log.e(TAG, "❌ RECORD_AUDIO permission not granted!")
                return null
            }
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                return null
            }
            
            Log.d(TAG, "Creating AudioRecord with buffer size: $bufferSize")
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
            
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized - state: ${audioRecord.state}")
                return null
            }
            
            Log.d(TAG, "AudioRecord initialized successfully, starting recording...")
            audioRecord.startRecording()
            
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(bufferSize)
            val recordingDuration = RECORD_DURATION_MS / 1000.0
            val totalSamples = (SAMPLE_RATE * recordingDuration).toInt()
            var samplesRead = 0
            
            while (samplesRead < totalSamples) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    samplesRead += read / 2 // 16-bit samples
                }
            }
            
            audioRecord.stop()
            
            val audioData = outputStream.toByteArray()
            
            // Check audio level to verify microphone is working
            var maxAmplitude = 0
            for (i in 0 until audioData.size-1 step 2) {
                val sample = ((audioData[i+1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()
                if (kotlin.math.abs(sample.toInt()) > maxAmplitude) {
                    maxAmplitude = kotlin.math.abs(sample.toInt())
                }
            }
            
            Log.d(TAG, "Recording complete: ${audioData.size} bytes, Max amplitude: $maxAmplitude / 32768")
            if (maxAmplitude < 500) {
                Log.w(TAG, "⚠️ Audio level very low ($maxAmplitude)! Music may not be playing or microphone not picking it up")
            }
            
            return audioData
            
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException: Microphone permission denied!", e)
            return null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ IllegalStateException: AudioRecord in invalid state", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error recording audio", e)
            return null
        } finally {
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord", e)
            }
        }
    }
    
    /**
     * Recognize audio using Shazam API via RapidAPI
     * FREE: 500 requests/month
     */
    private suspend fun recognizeWithShazam(audioData: ByteArray): NowPlayingTrack? {
        return withContext(Dispatchers.IO) {
            try {
                // Get API credentials from SharedPreferences
                val apiUrl = getShazamApiUrl()
                val apiKey = getShazamApiKey()
                val apiHost = getShazamApiHost()
                
                // Check if API key is configured
                if (apiKey.isEmpty() || apiKey == "YOUR_RAPIDAPI_KEY_HERE") {
                    Log.e(TAG, "❌ Shazam API key not configured! Configure in app settings.")
                    return@withContext null
                }
                
                // Official Shazam API expects base64-encoded raw PCM audio
                val base64Audio = android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
                
                // Create request body with base64 audio
                val requestBody = base64Audio.toRequestBody("text/plain".toMediaType())
                
                val request = Request.Builder()
                    .url(apiUrl)
                    .post(requestBody)
                    .addHeader("X-RapidAPI-Key", apiKey)
                    .addHeader("X-RapidAPI-Host", apiHost)
                    .addHeader("Content-Type", "text/plain")
                    .build()
                
                Log.d(TAG, "🌐 Sending ${audioData.size} bytes to Shazam...")
                Log.d(TAG, "📍 API URL: $apiUrl")
                Log.d(TAG, "📍 API Host: $apiHost")
                Log.d(TAG, "📍 API Key: ${apiKey.take(10)}...")
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                // Increment API usage counter (counts every request, successful or not)
                incrementApiUsage()
                
                Log.d(TAG, "📥 Response Code: ${response.code}")
                Log.d(TAG, "📥 Response Body: ${responseBody?.take(500)}")
                
                // Handle 204 No Content (no match found)
                if (response.code == 204 || responseBody.isNullOrEmpty()) {
                    Log.d(TAG, "No song match found (HTTP ${response.code})")
                    return@withContext null
                }
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "Shazam API error: ${response.code} - ${responseBody?.take(200)}")
                    return@withContext null
                }
                
                Log.d(TAG, "✅ Shazam response received")
                parseShazamResponse(responseBody)
                
            } catch (e: Exception) {
                Log.e(TAG, "Shazam recognition error", e)
                null
            }
        }
    }
    
    /**
     * Convert PCM to WAV format by adding WAV header
     */
    private fun convertPcmToWav(pcmData: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        
        // WAV header
        val channels = 1 // Mono
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        
        output.write("RIFF".toByteArray())
        output.write(intToByteArray(pcmData.size + 36), 0, 4) // File size - 8
        output.write("WAVE".toByteArray())
        output.write("fmt ".toByteArray())
        output.write(intToByteArray(16), 0, 4) // Subchunk1Size
        output.write(shortToByteArray(1), 0, 2) // AudioFormat (1 = PCM)
        output.write(shortToByteArray(channels.toShort()), 0, 2) // NumChannels
        output.write(intToByteArray(SAMPLE_RATE), 0, 4) // SampleRate
        output.write(intToByteArray(byteRate), 0, 4) // ByteRate
        output.write(shortToByteArray(blockAlign), 0, 2) // BlockAlign
        output.write(shortToByteArray(bitsPerSample.toShort()), 0, 2) // BitsPerSample
        output.write("data".toByteArray())
        output.write(intToByteArray(pcmData.size), 0, 4) // Subchunk2Size
        output.write(pcmData)
        
        return output.toByteArray()
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
    
    /**
     * Parse Shazam API response
     */
    private fun parseShazamResponse(json: String): NowPlayingTrack? {
        try {
            val jsonObject = JSONObject(json)
            
            // Check if track was found (official API returns "track" object directly)
            if (!jsonObject.has("track")) {
                Log.d(TAG, "No match found")
                return null
            }
            
            val track = jsonObject.getJSONObject("track")
            
            // Get title
            val title = if (track.has("title")) {
                track.getString("title")
            } else {
                Log.d(TAG, "No title in response")
                return null
            }
            
            // Get artist (subtitle field in official API)
            val artist = if (track.has("subtitle")) {
                track.getString("subtitle")
            } else {
                "Unknown Artist"
            }
            
            // Extract album art URL (use the largest available image)
            var albumArtUrl: String? = null
            if (track.has("images")) {
                val images = track.getJSONObject("images")
                // Try to get the coverarthq (highest quality), fallback to coverart
                albumArtUrl = when {
                    images.has("coverarthq") -> images.getString("coverarthq")
                    images.has("coverart") -> images.getString("coverart")
                    images.has("background") -> images.getString("background")
                    else -> null
                }
            }
            
            Log.d(TAG, "✅ Detected: $artist - $title ${if (albumArtUrl != null) "(with album art)" else ""}")
            
            return NowPlayingTrack(
                title = title,
                artist = artist,
                time = getCurrentTime(),
                date = LocalDate.now().toString(),
                albumArt = albumArtUrl
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Shazam response: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Handle detected track
     */
    private fun handleDetectedTrack(track: NowPlayingTrack) {
        val trackKey = "${track.title}|${track.artist}"
        
        // Check cooldown period
        val lastDetection = lastDetectionTime[trackKey] ?: 0L
        val now = System.currentTimeMillis()
        
        if (now - lastDetection < COOLDOWN_PERIOD_MS) {
            Log.d(TAG, "⏰ Cooldown active for: $trackKey")
            return
        }
        
        // Check if already detected
        if (!detectedTracks.contains(trackKey)) {
            detectedTracks.add(trackKey)
            lastDetectionTime[trackKey] = now
            
            Log.d(TAG, "🎵 NEW TRACK: ${track.title} by ${track.artist}")
            
            // Add to library
            addTrackToLibrary(track)
            
            // Show lock screen notification
            showDetectionNotification(track)
            
            // Broadcast to UI for real-time update
            broadcastTrackDetected(track)
            
            // Notify callback
            onNewTrackDetected?.invoke(track)
        }
    }
    
    /**
     * Increment Shazam API usage counter
     */
    private fun incrementApiUsage() {
        try {
            val prefs = getSharedPreferences("wayve_prefs", MODE_PRIVATE)
            val currentCount = prefs.getInt("shazam_api_usage_count", 0)
            prefs.edit().putInt("shazam_api_usage_count", currentCount + 1).apply()
            Log.d(TAG, "📊 API Usage: ${currentCount + 1}/500")
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing API usage", e)
        }
    }
    
    /**
     * Fetch album art for tracks that don't have it using iTunes Search API
     * Called on service start to update old tracks
     */
    private fun updateMissingAlbumArt() {
        scope.launch(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("wayve_prefs", MODE_PRIVATE)
                val savedData = prefs.getString("nowplaying_data", null) ?: return@launch
                
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val data = json.decodeFromString<NowPlayingData>(savedData)
                
                var updated = false
                val updatedTracks = mutableListOf<NowPlayingTrack>()
                
                for (track in data.tracks) {
                    if (track.albumArt == null) {
                        Log.d(TAG, "🖼️ Fetching album art for: ${track.title} by ${track.artist}")
                        val albumArtUrl = fetchAlbumArtFromItunes(track.title, track.artist)
                        if (albumArtUrl != null) {
                            updatedTracks.add(track.copy(albumArt = albumArtUrl))
                            updated = true
                            Log.d(TAG, "✅ Found album art for: ${track.title}")
                        } else {
                            updatedTracks.add(track)
                            Log.d(TAG, "⚠️ No album art found for: ${track.title}")
                        }
                        delay(500) // Rate limiting
                    } else {
                        updatedTracks.add(track)
                    }
                }
                
                if (updated) {
                    val updatedData = data.copy(tracks = updatedTracks)
                    val jsonString = json.encodeToString(NowPlayingData.serializer(), updatedData)
                    prefs.edit().putString("nowplaying_data", jsonString).apply()
                    Log.d(TAG, "✅ Updated album art for old tracks")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating album art", e)
            }
        }
    }
    
    /**
     * Fetch album art from iTunes Search API (free, no key required)
     */
    private suspend fun fetchAlbumArtFromItunes(title: String, artist: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val query = "$artist $title".replace(" ", "+")
                val url = "https://itunes.apple.com/search?term=$query&media=music&entity=song&limit=1"
                
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful || responseBody == null) {
                    return@withContext null
                }
                
                // Parse JSON response
                val jsonResponse = org.json.JSONObject(responseBody)
                val results = jsonResponse.optJSONArray("results")
                
                if (results != null && results.length() > 0) {
                    val firstResult = results.getJSONObject(0)
                    // Get high-quality artwork (600x600)
                    val artworkUrl = firstResult.optString("artworkUrl100", "")
                    if (artworkUrl.isNotEmpty()) {
                        return@withContext artworkUrl.replace("100x100bb", "600x600bb")
                    }
                }
                
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching iTunes album art", e)
                return@withContext null
            }
        }
    }
    
    /**
     * Show lock screen notification for detected song (Minimal Now Playing style)
     */
    private fun showDetectionNotification(track: NowPlayingTrack) {
        scope.launch(Dispatchers.IO) {
            try {
                val notificationIntent = Intent(this@ContinuousAudioMonitorService, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this@ContinuousAudioMonitorService,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                
                // Minimal notification - text only, no album art
                val builder = NotificationCompat.Builder(this@ContinuousAudioMonitorService, DETECTION_CHANNEL_ID)
                    .setContentTitle("${track.title} • Wayve")
                    .setContentText(track.artist)
                    .setSmallIcon(R.drawable.music)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Lower priority for minimal appearance
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
                    .setOnlyAlertOnce(true) // Silent like Now Playing
                    .setShowWhen(false) // Hide timestamp like Now Playing
                    .setTimeoutAfter(60000) // Auto-dismiss after 60 seconds
                    .setColorized(false)
                    .setOngoing(false)
                
                val notification = builder.build()
                
                withContext(Dispatchers.Main) {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    // Use a fixed ID so each new song replaces the previous one (like Now Playing)
                    notificationManager.notify(99999, notification)
                    Log.d(TAG, "📱 Minimal lock screen notification: ${track.title} by ${track.artist}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error showing notification", e)
            }
        }
    }
    
    /**
     * Download album art bitmap from URL
     */
    private suspend fun downloadAlbumArt(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                val response = client.newCall(request).execute()
                val bytes = response.body?.bytes()
                
                if (bytes != null) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading album art", e)
                null
            }
        }
    }
    
    /**
     * Broadcast track detection to UI for real-time update
     */
    private fun broadcastTrackDetected(track: NowPlayingTrack) {
        val intent = Intent(ACTION_TRACK_DETECTED).apply {
            putExtra(EXTRA_TRACK_TITLE, track.title)
            putExtra(EXTRA_TRACK_ARTIST, track.artist)
        }
        sendBroadcast(intent)
        Log.d(TAG, "📡 Broadcast sent to UI: ${track.title}")
    }
    
    /**
     * Update monitoring notification
     */
    private fun updateMonitoringNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎵 Wayve - Always Listening")
            .setContentText("${detectedTracks.size} songs detected")
            .setSmallIcon(R.drawable.music)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Check if microphone permission is granted
     */
    private fun hasPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get current time
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
                }.decodeFromString<com.wayve.app.data.NowPlayingData>(savedData)
                
                data.tracks.forEach { track ->
                    val key = "${track.title}|${track.artist}"
                    detectedTracks.add(key)
                }
                
                Log.d(TAG, "Loaded ${detectedTracks.size} existing tracks")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading existing tracks", e)
        }
    }
    
    /**
     * Add track to library
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
                    json.decodeFromString<com.wayve.app.data.NowPlayingData>(savedData)
                } else {
                    com.wayve.app.data.NowPlayingData(
                        exported = java.time.Instant.now().toString(),
                        source = "Continuous Audio Monitor",
                        device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                        method = "Always-On Detection (AmbientMusicMod-style)",
                        tracks = emptyList()
                    )
                }
                
                val updatedTracks = listOf(track) + currentData.tracks
                val stats = com.wayve.app.data.NowPlayingParser.calculateStats(updatedTracks)
                
                val updatedData = currentData.copy(
                    tracks = updatedTracks,
                    statistics = stats
                )
                
                val jsonString = json.encodeToString(
                    com.wayve.app.data.NowPlayingData.serializer(),
                    updatedData
                )
                sharedPrefs.edit().putString("nowplaying_data", jsonString).apply()
                
                Log.d(TAG, "✅ Track added to library: ${track.title}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding track to library", e)
            }
        }
    }
}

