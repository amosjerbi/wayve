package com.wayve.app.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.wayve.app.data.CaptureInfo
import com.wayve.app.data.NowPlayingData
import com.wayve.app.data.NowPlayingTrack
import com.wayve.app.data.Statistics
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStreamReader
import java.io.BufferedReader

/**
 * Service to capture Now Playing history data
 * This opens the Now Playing app and provides instructions for data capture
 */
class NowPlayingCaptureService(private val context: Context) {
    
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Opens the Now Playing History activity
     */
    fun openNowPlayingApp(): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(
                    "com.google.android.as",
                    "com.google.intelligence.sense.ambientmusic.history.HistoryActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Attempts to capture UI dump using shell commands (requires root/adb)
     * This is a simplified version - full implementation would need proper permissions
     */
    fun captureCurrentScreen(outputFile: File): Boolean {
        return try {
            // Try to dump UI hierarchy
            val process = Runtime.getRuntime().exec("uiautomator dump /sdcard/window_dump.xml")
            process.waitFor()
            
            if (process.exitValue() == 0) {
                // Copy from sdcard to app storage
                val copyProcess = Runtime.getRuntime().exec(
                    "cp /sdcard/window_dump.xml ${outputFile.absolutePath}"
                )
                copyProcess.waitFor()
                copyProcess.exitValue() == 0
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Parse XML dump file to extract track information
     */
    fun parseXmlDump(xmlFile: File): List<NowPlayingTrack> {
        val tracks = mutableListOf<NowPlayingTrack>()
        
        try {
            val xmlContent = xmlFile.readText()
            
            // Simple regex-based parsing
            // Look for song_row_title and associated artist info
            val titlePattern = """node.*?song_row_title.*?text="([^"]+)"""".toRegex()
            val artistPattern = """node.*?song_artist_and_timestamp.*?text="([^"]+)"""".toRegex()
            
            val titles = titlePattern.findAll(xmlContent).map { it.groupValues[1] }.toList()
            val artistsAndTimes = artistPattern.findAll(xmlContent).map { it.groupValues[1] }.toList()
            
            // Match titles with artists
            titles.forEachIndexed { index, title ->
                if (index < artistsAndTimes.size) {
                    val artistAndTime = artistsAndTimes[index]
                    val parts = artistAndTime.split("•").map { it.trim() }
                    
                    val track = NowPlayingTrack(
                        title = title,
                        artist = parts.getOrNull(0) ?: "Unknown Artist",
                        time = parts.getOrNull(1),
                        date = java.time.LocalDate.now().toString()
                    )
                    tracks.add(track)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return tracks
    }
    
    /**
     * Create JSON data from captured tracks
     */
    fun createJsonData(tracks: List<NowPlayingTrack>): NowPlayingData {
        val uniqueArtists = tracks.map { it.artist }.toSet().size
        val uniqueSongs = tracks.map { "${it.title}|${it.artist}" }.toSet().size
        val favoritedCount = tracks.count { it.favorited }
        
        // Extract unique dates from tracks
        val dateGroups = tracks
            .mapNotNull { it.date }
            .distinct()
            .sorted()
            .reversed() // Most recent first
        
        return NowPlayingData(
            exported = java.time.Instant.now().toString(),
            source = "com.google.android.as (Captured via wayve)",
            device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            method = "In-app capture",
            capture_info = CaptureInfo(
                total_pages = 1,
                date_groups = dateGroups,
                visual_screenshots_available = false
            ),
            statistics = Statistics(
                total_tracks = tracks.size,
                unique_artists = uniqueArtists,
                unique_songs = uniqueSongs,
                favorited_count = favoritedCount
            ),
            tracks = tracks
        )
    }
    
    /**
     * Save JSON data to file
     */
    fun saveJsonData(data: NowPlayingData, outputFile: File): Boolean {
        return try {
            val json = kotlinx.serialization.json.Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }
            val jsonString = json.encodeToString(
                NowPlayingData.serializer(),
                data
            )
            outputFile.writeText(jsonString)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Get capture instructions for manual process
     */
    fun getCaptureInstructions(): String {
        return """
            To capture your Now Playing history:
            
            1. The Now Playing History app will open
            2. Use your computer with ADB connected
            3. Run the capture script:
               ./scripts/capture_complete_data.sh
            4. Transfer the generated JSON file to your phone
            5. Return here and tap "Import JSON"
            
            Alternative (Advanced):
            If you have root access or ADB wireless:
            - The app can attempt automatic capture
            - Grant necessary permissions when prompted
        """.trimIndent()
    }
    
    /**
     * Check if ADB/root access is available for automatic capture
     */
    fun canAutomaticCapture(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which uiautomator")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            !output.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

