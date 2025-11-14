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
    
    data class SpotifyPlaylist(
        val id: String,
        val name: String,
        val description: String,
        val trackCount: Int,
        val imageUrl: String?,
        val owner: String
    )
    
    data class SpotifyTrack(
        val title: String,
        val artist: String,
        val album: String,
        val albumArt: String?,
        val uri: String
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
    
    /**
     * Get user's playlists
     */
    suspend fun getUserPlaylists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val accessToken = authManager.getAccessToken()
        if (accessToken == null) {
            android.util.Log.e("SpotifyPlaylist", "Not signed in")
            return@withContext emptyList()
        }
        
        try {
            val playlists = mutableListOf<SpotifyPlaylist>()
            var nextUrl: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
            
            while (nextUrl != null) {
                val request = Request.Builder()
                    .url(nextUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val items = json.getJSONArray("items")
                        
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            
                            val images = item.getJSONArray("images")
                            val imageUrl = if (images.length() > 0) {
                                images.getJSONObject(0).getString("url")
                            } else null
                            
                            val owner = item.getJSONObject("owner").getString("display_name")
                            
                            playlists.add(
                                SpotifyPlaylist(
                                    id = item.getString("id"),
                                    name = item.getString("name"),
                                    description = item.optString("description", ""),
                                    trackCount = item.getJSONObject("tracks").getInt("total"),
                                    imageUrl = imageUrl,
                                    owner = owner
                                )
                            )
                        }
                        
                        nextUrl = if (json.has("next") && !json.isNull("next")) json.getString("next") else null
                    } else {
                        android.util.Log.e("SpotifyPlaylist", "Failed to get playlists: ${response.code}")
                        nextUrl = null
                    }
                }
            }
            
            android.util.Log.d("SpotifyPlaylist", "Found ${playlists.size} playlists")
            playlists
            
        } catch (e: Exception) {
            android.util.Log.e("SpotifyPlaylist", "Error getting playlists", e)
            emptyList()
        }
    }
    
    /**
     * Get tracks from a playlist
     */
    suspend fun getPlaylistTracks(playlistId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val accessToken = authManager.getAccessToken()
        if (accessToken == null) {
            android.util.Log.e("SpotifyPlaylist", "Not signed in")
            return@withContext emptyList()
        }
        
        try {
            val tracks = mutableListOf<SpotifyTrack>()
            var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
            
            while (nextUrl != null) {
                val request = Request.Builder()
                    .url(nextUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        val items = json.getJSONArray("items")
                        
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            val track = item.optJSONObject("track") ?: continue
                            
                            // Skip if track is null (can happen with deleted/unavailable tracks)
                            if (track.isNull("id")) continue
                            
                            val trackName = track.getString("name")
                            
                            // Get artists
                            val artists = track.getJSONArray("artists")
                            val artistNames = mutableListOf<String>()
                            for (j in 0 until artists.length()) {
                                artistNames.add(artists.getJSONObject(j).getString("name"))
                            }
                            val artistString = artistNames.joinToString(", ")
                            
                            // Get album info
                            val album = track.getJSONObject("album")
                            val albumName = album.getString("name")
                            
                            // Get album art
                            val images = album.getJSONArray("images")
                            val albumArt = if (images.length() > 0) {
                                images.getJSONObject(0).getString("url")
                            } else null
                            
                            tracks.add(
                                SpotifyTrack(
                                    title = trackName,
                                    artist = artistString,
                                    album = albumName,
                                    albumArt = albumArt,
                                    uri = track.getString("uri")
                                )
                            )
                        }
                        
                        nextUrl = if (json.has("next") && !json.isNull("next")) json.getString("next") else null
                    } else {
                        android.util.Log.e("SpotifyPlaylist", "Failed to get tracks: ${response.code}")
                        nextUrl = null
                    }
                }
            }
            
            android.util.Log.d("SpotifyPlaylist", "Found ${tracks.size} tracks in playlist")
            tracks
            
        } catch (e: Exception) {
            android.util.Log.e("SpotifyPlaylist", "Error getting playlist tracks", e)
            emptyList()
        }
    }
}

