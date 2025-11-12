package com.wayve.app.network

import android.content.Context
import com.wayve.app.data.NowPlayingTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Create Spotify playlists using Spotify Web API
 */
class SpotifyPlaylistManager(
    private val context: Context,
    private val authManager: SpotifyAuthManager
) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    data class PlaylistResult(
        val success: Boolean,
        val playlistUrl: String? = null,
        val addedCount: Int = 0,
        val totalCount: Int = 0,
        val error: String? = null
    )
    
    /**
     * Create a Spotify playlist from Wayve tracks
     */
    suspend fun createPlaylistFromTracks(
        tracks: List<NowPlayingTrack>,
        playlistName: String,
        onProgress: (current: Int, total: Int, trackName: String) -> Unit
    ): PlaylistResult = withContext(Dispatchers.IO) {
        
        val accessToken = authManager.getAccessToken()
        if (accessToken == null) {
            return@withContext PlaylistResult(
                success = false,
                error = "Not signed in to Spotify"
            )
        }
        
        try {
            android.util.Log.d("SpotifyPlaylist", "Creating playlist: $playlistName")
            
            // Step 1: Get user ID
            val userId = getUserId(accessToken) ?: return@withContext PlaylistResult(
                success = false,
                error = "Failed to get Spotify user info"
            )
            
            android.util.Log.d("SpotifyPlaylist", "User ID: $userId")
            
            // Step 2: Create playlist
            val playlistId = createPlaylist(accessToken, userId, playlistName, 
                "Created from Wayve - My music library") 
                ?: return@withContext PlaylistResult(
                    success = false,
                    error = "Failed to create playlist"
                )
            
            android.util.Log.d("SpotifyPlaylist", "Playlist created: $playlistId")
            
            // Step 3: Search for tracks and add them
            var addedCount = 0
            val trackUris = mutableListOf<String>()
            
            tracks.forEachIndexed { index, track ->
                onProgress(index + 1, tracks.size, track.title)
                
                try {
                    val uri = searchTrack(accessToken, track.title, track.artist)
                    if (uri != null) {
                        trackUris.add(uri)
                        addedCount++
                        
                        // Add tracks in batches of 100 (Spotify API limit)
                        if (trackUris.size >= 100) {
                            addTracksToPlaylist(accessToken, playlistId, trackUris)
                            trackUris.clear()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SpotifyPlaylist", "Failed to add ${track.title}: ${e.message}")
                }
            }
            
            // Add remaining tracks
            if (trackUris.isNotEmpty()) {
                addTracksToPlaylist(accessToken, playlistId, trackUris)
            }
            
            val playlistUrl = "https://open.spotify.com/playlist/$playlistId"
            
            PlaylistResult(
                success = true,
                playlistUrl = playlistUrl,
                addedCount = addedCount,
                totalCount = tracks.size
            )
            
        } catch (e: Exception) {
            android.util.Log.e("SpotifyPlaylist", "Error creating playlist", e)
            PlaylistResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Get Spotify user ID
     */
    private fun getUserId(accessToken: String): String? {
        return try {
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer $accessToken")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    json.getString("id")
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyPlaylist", "Error getting user ID", e)
            null
        }
    }
    
    /**
     * Create empty playlist
     */
    private fun createPlaylist(
        accessToken: String,
        userId: String,
        name: String,
        description: String
    ): String? {
        return try {
            val json = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("public", false)
            }
            
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/users/$userId/playlists")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseJson = JSONObject(response.body?.string() ?: "")
                    responseJson.getString("id")
                } else {
                    android.util.Log.e("SpotifyPlaylist", "Failed to create playlist: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyPlaylist", "Error creating playlist", e)
            null
        }
    }
    
    /**
     * Search for a track and return its URI
     */
    private fun searchTrack(accessToken: String, title: String, artist: String): String? {
        return try {
            val query = "$title $artist"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/search?q=$encodedQuery&type=track&limit=1")
                .header("Authorization", "Bearer $accessToken")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val tracks = json.getJSONObject("tracks").getJSONArray("items")
                    
                    if (tracks.length() > 0) {
                        tracks.getJSONObject(0).getString("uri")
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SpotifyPlaylist", "Error searching track: ${e.message}")
            null
        }
    }
    
    /**
     * Add tracks to playlist
     */
    private fun addTracksToPlaylist(
        accessToken: String,
        playlistId: String,
        trackUris: List<String>
    ) {
        try {
            val json = JSONObject().apply {
                put("uris", JSONArray(trackUris))
            }
            
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/$playlistId/tracks")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.e("SpotifyPlaylist", "Failed to add tracks: ${response.code}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyPlaylist", "Error adding tracks", e)
        }
    }
}

