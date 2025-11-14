package com.wayve.app.ui.dialogs

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
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
import com.wayve.app.network.YouTubePlaylistImporter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant

@Composable
fun YouTubeImportDialog(
    context: Context,
    importer: YouTubePlaylistImporter,
    onDismiss: () -> Unit,
    onImportComplete: (Int) -> Unit
) {
    var playlistUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playlistInfo by remember { mutableStateOf<YouTubePlaylistImporter.PlaylistInfo?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    
    Dialog(
        onDismissRequest = { if (!isImporting && !isLoading) onDismiss() },
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Import YouTube Playlist",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    if (!isImporting && !isLoading) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when {
                        isImporting -> {
                            // Import progress
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Text(
                                        text = importProgress,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        playlistInfo != null -> {
                            // Playlist preview
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Playlist thumbnail
                                if (playlistInfo!!.thumbnail != null) {
                                    AsyncImage(
                                        model = playlistInfo!!.thumbnail,
                                        contentDescription = "Playlist thumbnail",
                                        modifier = Modifier
                                            .size(180.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(180.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(90.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                // Playlist details
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = playlistInfo!!.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    Text(
                                        text = playlistInfo!!.channelTitle,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${playlistInfo!!.videoCount} videos",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                    
                                    if (playlistInfo!!.description.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = playlistInfo!!.description,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        
                        else -> {
                            // URL input
                            Text(
                                text = "Paste a YouTube playlist link below",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            OutlinedTextField(
                                value = playlistUrl,
                                onValueChange = { 
                                    playlistUrl = it
                                    error = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("YouTube Playlist URL") },
                                placeholder = { Text("https://youtube.com/playlist?list=...") },
                                singleLine = true,
                                isError = error != null,
                                supportingText = if (error != null) {
                                    { Text(error!!, color = MaterialTheme.colorScheme.error) }
                                } else null,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            // Examples
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "💡 Supported formats:",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "• Full URL: youtube.com/playlist?list=...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "• Playlist ID: PLxxxxxxxx",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Text(
                                        text = "⚠️ Only public playlists can be imported",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (playlistInfo != null && !isImporting) {
                        OutlinedButton(
                            onClick = { 
                                playlistInfo = null
                                error = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back")
                        }
                    }
                    
                    Button(
                        onClick = {
                            when {
                                playlistInfo != null -> {
                                    // Import playlist
                                    isImporting = true
                                    scope.launch {
                                        try {
                                            val playlistId = importer.extractPlaylistId(playlistUrl)
                                            if (playlistId == null) {
                                                error = "Invalid playlist URL"
                                                isImporting = false
                                                return@launch
                                            }
                                            
                                            importProgress = "Loading videos..."
                                            
                                            val result = importer.importPlaylist(playlistId) { current, total ->
                                                importProgress = "Loading $current/$total videos..."
                                            }
                                            
                                            if (result.success && result.videos.isNotEmpty()) {
                                                importProgress = "Importing ${result.videos.size} videos..."
                                                
                                                // Convert to NowPlayingTrack format
                                                val tracks = result.videos.map { video ->
                                                    NowPlayingTrack(
                                                        title = video.title,
                                                        artist = video.channel,
                                                        time = java.time.LocalTime.now().toString(),
                                                        date = java.time.LocalDate.now().toString(),
                                                        favorited = false,
                                                        captured_on_page = null,
                                                        albumArt = video.thumbnail
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
                                                        source = "YouTube Import",
                                                        device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                                                        method = "YouTube Playlist Import",
                                                        tracks = emptyList()
                                                    )
                                                }
                                                
                                                // Merge tracks
                                                val updatedTracks = tracks + currentData.tracks
                                                val stats = NowPlayingParser.calculateStats(updatedTracks)
                                                
                                                val updatedData = currentData.copy(
                                                    tracks = updatedTracks,
                                                    statistics = stats
                                                )
                                                
                                                // Save
                                                val jsonString = json.encodeToString(
                                                    NowPlayingData.serializer(),
                                                    updatedData
                                                )
                                                sharedPrefs.edit().putString("nowplaying_data", jsonString).apply()
                                                
                                                android.util.Log.d("YouTubeImport", "✅ Imported ${tracks.size} videos")
                                                
                                                importProgress = "Imported ${tracks.size} videos!"
                                                kotlinx.coroutines.delay(1000)
                                                
                                                onImportComplete(tracks.size)
                                                onDismiss()
                                            } else {
                                                error = result.error ?: "No videos found"
                                                isImporting = false
                                            }
                                            
                                        } catch (e: Exception) {
                                            android.util.Log.e("YouTubeImport", "Error importing", e)
                                            error = e.message ?: "Import failed"
                                            isImporting = false
                                        }
                                    }
                                }
                                else -> {
                                    // Fetch playlist info
                                    if (playlistUrl.isBlank()) {
                                        error = "Please enter a playlist URL"
                                        return@Button
                                    }
                                    
                                    if (!importer.isConfigured()) {
                                        error = "Please configure YouTube API key in settings first"
                                        return@Button
                                    }
                                    
                                    val playlistId = importer.extractPlaylistId(playlistUrl)
                                    if (playlistId == null) {
                                        error = "Invalid playlist URL or ID"
                                        return@Button
                                    }
                                    
                                    isLoading = true
                                    error = null
                                    
                                    scope.launch {
                                        try {
                                            val info = importer.getPlaylistInfo(playlistId)
                                            if (info != null) {
                                                playlistInfo = info
                                            } else {
                                                error = "Playlist not found or is private"
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "Failed to load playlist"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && !isImporting
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (playlistInfo != null) "Import" else "Continue")
                        }
                    }
                }
            }
        }
    }
}

