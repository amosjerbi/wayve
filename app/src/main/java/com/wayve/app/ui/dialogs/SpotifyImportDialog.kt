package com.wayve.app.ui.dialogs

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.wayve.app.data.NowPlayingData
import com.wayve.app.data.NowPlayingParser
import com.wayve.app.data.NowPlayingTrack
import com.wayve.app.network.SpotifyPlaylistManager
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant

@Composable
fun SpotifyImportDialog(
    context: Context,
    playlistManager: SpotifyPlaylistManager,
    onDismiss: () -> Unit,
    onImportComplete: (Int) -> Unit
) {
    var playlists by remember { mutableStateOf<List<SpotifyPlaylistManager.SpotifyPlaylist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<SpotifyPlaylistManager.SpotifyPlaylist?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    
    // Load playlists on first display
    LaunchedEffect(Unit) {
        try {
            playlists = playlistManager.getUserPlaylists()
            isLoading = false
        } catch (e: Exception) {
            error = e.message ?: "Failed to load playlists"
            isLoading = false
        }
    }
    
    Dialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedPlaylist != null) "Import Playlist" else "Import from Spotify",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (!isImporting) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Loading playlists...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Error: $error",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(onClick = onDismiss) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                    
                    selectedPlaylist != null && isImporting -> {
                        // Import progress
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = importProgress,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    selectedPlaylist != null -> {
                        // Confirmation screen
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Playlist image
                                if (selectedPlaylist!!.imageUrl != null) {
                                    AsyncImage(
                                        model = selectedPlaylist!!.imageUrl,
                                        contentDescription = "Playlist cover",
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(80.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Text(
                                    text = selectedPlaylist!!.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "${selectedPlaylist!!.trackCount} tracks",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                if (selectedPlaylist!!.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = selectedPlaylist!!.description,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            // Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { selectedPlaylist = null },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Back")
                                }
                                
                                Button(
                                    onClick = {
                                        isImporting = true
                                        scope.launch {
                                            try {
                                                importProgress = "Loading tracks..."
                                                val tracks = playlistManager.getPlaylistTracks(selectedPlaylist!!.id)
                                                
                                                if (tracks.isEmpty()) {
                                                    importProgress = "No tracks found"
                                                    kotlinx.coroutines.delay(2000)
                                                    onDismiss()
                                                    return@launch
                                                }
                                                
                                                importProgress = "Importing ${tracks.size} tracks..."
                                                
                                                // Convert Spotify tracks to NowPlayingTrack format
                                                val nowPlayingTracks = tracks.map { track ->
                                                    NowPlayingTrack(
                                                        title = track.title,
                                                        artist = track.artist,
                                                        time = java.time.LocalTime.now().toString(),
                                                        date = java.time.LocalDate.now().toString(),
                                                        favorited = false,
                                                        captured_on_page = null,
                                                        albumArt = track.albumArt
                                                    )
                                                }
                                                
                                                // Load existing library
                                                val sharedPrefs = context.getSharedPreferences("wayve_prefs", Context.MODE_PRIVATE)
                                                val savedData = sharedPrefs.getString("nowplaying_data", null)
                                                
                                                val json = Json {
                                                    ignoreUnknownKeys = true
                                                    prettyPrint = false
                                                }
                                                
                                                val currentData = if (savedData != null) {
                                                    json.decodeFromString<NowPlayingData>(savedData)
                                                } else {
                                                    NowPlayingData(
                                                        exported = Instant.now().toString(),
                                                        source = "Spotify Import",
                                                        device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                                                        method = "Spotify Playlist Import",
                                                        tracks = emptyList()
                                                    )
                                                }
                                                
                                                // Merge tracks (add new ones at the beginning)
                                                val updatedTracks = nowPlayingTracks + currentData.tracks
                                                val stats = NowPlayingParser.calculateStats(updatedTracks)
                                                
                                                val updatedData = currentData.copy(
                                                    tracks = updatedTracks,
                                                    statistics = stats
                                                )
                                                
                                                // Save to shared preferences
                                                val jsonString = json.encodeToString(
                                                    NowPlayingData.serializer(),
                                                    updatedData
                                                )
                                                sharedPrefs.edit().putString("nowplaying_data", jsonString).apply()
                                                
                                                android.util.Log.d("SpotifyImport", "✅ Imported ${tracks.size} tracks from ${selectedPlaylist!!.name}")
                                                
                                                importProgress = "Imported ${tracks.size} tracks!"
                                                kotlinx.coroutines.delay(1000)
                                                
                                                onImportComplete(tracks.size)
                                                onDismiss()
                                                
                                            } catch (e: Exception) {
                                                android.util.Log.e("SpotifyImport", "Error importing playlist", e)
                                                importProgress = "Error: ${e.message}"
                                                kotlinx.coroutines.delay(2000)
                                                isImporting = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import")
                                }
                            }
                        }
                    }
                    
                    playlists.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No playlists found",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    else -> {
                        // Playlist list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(playlists) { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    onClick = { selectedPlaylist = playlist }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistItem(
    playlist: SpotifyPlaylistManager.SpotifyPlaylist,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist image
            if (playlist.imageUrl != null) {
                AsyncImage(
                    model = playlist.imageUrl,
                    contentDescription = "Playlist cover",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Playlist info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = playlist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${playlist.trackCount} tracks",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "•",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = playlist.owner,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

