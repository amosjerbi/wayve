package com.wayve.app.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Import public YouTube playlists using YouTube Data API v3
 */
class YouTubePlaylistImporter(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    data class YouTubeVideo(
        val title: String,
        val channel: String,
        val videoId: String,
        val thumbnail: String?
    )
    
    data class PlaylistInfo(
        val title: String,
        val description: String,
        val videoCount: Int,
        val thumbnail: String?,
        val channelTitle: String
    )
    
    data class ImportResult(
        val success: Boolean,
        val videos: List<YouTubeVideo> = emptyList(),
        val playlistInfo: PlaylistInfo? = null,
        val error: String? = null
    )
    
    /**
     * Extract playlist ID from various YouTube URL formats
     */
    fun extractPlaylistId(url: String): String? {
        return try {
            val patterns = listOf(
                Regex("list=([a-zA-Z0-9_-]+)"),
                Regex("youtube\\.com/playlist\\?list=([a-zA-Z0-9_-]+)"),
                Regex("youtu\\.be/.*\\?list=([a-zA-Z0-9_-]+)")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(url)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
            
            // If it's just an ID
            if (url.matches(Regex("^[a-zA-Z0-9_-]+$")) && url.length > 10) {
                return url
            }
            
            null
        } catch (e: Exception) {
            Log.e("YouTubeImporter", "Error extracting playlist ID", e)
            null
        }
    }
    
    /**
     * Get YouTube API key from settings
     */
    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("wayve_prefs", Context.MODE_PRIVATE)
        return prefs.getString("youtube_api_key", "") ?: ""
    }
    
    /**
     * Check if API key is configured
     */
    fun isConfigured(): Boolean {
        return getApiKey().isNotEmpty()
    }
    
    /**
     * Fetch playlist information
     */
    suspend fun getPlaylistInfo(playlistId: String): PlaylistInfo? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            Log.e("YouTubeImporter", "API key not configured")
            return@withContext null
        }
        
        try {
            val url = "https://www.googleapis.com/youtube/v3/playlists" +
                    "?part=snippet,contentDetails" +
                    "&id=$playlistId" +
                    "&key=$apiKey"
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val items = json.getJSONArray("items")
                    
                    if (items.length() > 0) {
                        val item = items.getJSONObject(0)
                        val snippet = item.getJSONObject("snippet")
                        val contentDetails = item.getJSONObject("contentDetails")
                        
                        val thumbnails = snippet.getJSONObject("thumbnails")
                        val thumbnail = when {
                            thumbnails.has("high") -> thumbnails.getJSONObject("high").getString("url")
                            thumbnails.has("medium") -> thumbnails.getJSONObject("medium").getString("url")
                            thumbnails.has("default") -> thumbnails.getJSONObject("default").getString("url")
                            else -> null
                        }
                        
                        PlaylistInfo(
                            title = snippet.getString("title"),
                            description = snippet.optString("description", ""),
                            videoCount = contentDetails.getInt("itemCount"),
                            thumbnail = thumbnail,
                            channelTitle = snippet.getString("channelTitle")
                        )
                    } else {
                        null
                    }
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("YouTubeImporter", "Failed to get playlist info: ${response.code} - $errorBody")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("YouTubeImporter", "Error getting playlist info", e)
            null
        }
    }
    
    /**
     * Import all videos from a YouTube playlist
     */
    suspend fun importPlaylist(
        playlistId: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext ImportResult(
                success = false,
                error = "YouTube API key not configured"
            )
        }
        
        try {
            // First get playlist info
            val playlistInfo = getPlaylistInfo(playlistId)
            if (playlistInfo == null) {
                return@withContext ImportResult(
                    success = false,
                    error = "Playlist not found or is private"
                )
            }
            
            Log.d("YouTubeImporter", "Importing playlist: ${playlistInfo.title}")
            
            val videos = mutableListOf<YouTubeVideo>()
            var nextPageToken: String? = null
            var pageCount = 0
            val maxPages = 20 // Limit to 1000 videos (50 per page)
            
            do {
                val url = buildString {
                    append("https://www.googleapis.com/youtube/v3/playlistItems")
                    append("?part=snippet")
                    append("&playlistId=$playlistId")
                    append("&maxResults=50")
                    append("&key=$apiKey")
                    if (nextPageToken != null) {
                        append("&pageToken=$nextPageToken")
                    }
                }
                
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val items = json.getJSONArray("items")
                        
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            val snippet = item.getJSONObject("snippet")
                            
                            // Skip private/deleted videos
                            if (snippet.getString("title") == "Private video" || 
                                snippet.getString("title") == "Deleted video") {
                                continue
                            }
                            
                            val thumbnails = snippet.getJSONObject("thumbnails")
                            val thumbnail = when {
                                thumbnails.has("high") -> thumbnails.getJSONObject("high").getString("url")
                                thumbnails.has("medium") -> thumbnails.getJSONObject("medium").getString("url")
                                thumbnails.has("default") -> thumbnails.getJSONObject("default").getString("url")
                                else -> null
                            }
                            
                            videos.add(
                                YouTubeVideo(
                                    title = snippet.getString("title"),
                                    channel = snippet.getString("channelTitle"),
                                    videoId = snippet.getJSONObject("resourceId").getString("videoId"),
                                    thumbnail = thumbnail
                                )
                            )
                        }
                        
                        nextPageToken = if (json.has("nextPageToken") && !json.isNull("nextPageToken")) {
                            json.getString("nextPageToken")
                        } else {
                            null
                        }
                        
                        pageCount++
                        onProgress(videos.size, playlistInfo.videoCount)
                        
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        Log.e("YouTubeImporter", "Failed to get videos: ${response.code} - $errorBody")
                        nextPageToken = null
                    }
                }
                
            } while (nextPageToken != null && pageCount < maxPages)
            
            Log.d("YouTubeImporter", "Imported ${videos.size} videos")
            
            ImportResult(
                success = true,
                videos = videos,
                playlistInfo = playlistInfo
            )
            
        } catch (e: Exception) {
            Log.e("YouTubeImporter", "Error importing playlist", e)
            ImportResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
}

