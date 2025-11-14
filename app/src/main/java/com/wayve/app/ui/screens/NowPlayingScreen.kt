package com.wayve.app.ui.screens

import android.content.Intent
import android.net.Uri
import com.wayve.app.MonitorStarter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.wayve.app.MainViewModel
import com.wayve.app.R
import com.wayve.app.data.NowPlayingData
import com.wayve.app.data.NowPlayingParser
import com.wayve.app.data.NowPlayingTrack
import com.wayve.app.ui.dialogs.ShazamSettingsDialog
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun surfaceVariantDarker(): Color {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val isLight = surfaceVariant.luminance() > 0.5f
    return if (isLight) {
        Color(
            red = (surfaceVariant.red * 0.9f).coerceIn(0f, 1f),
            green = (surfaceVariant.green * 0.9f).coerceIn(0f, 1f),
            blue = (surfaceVariant.blue * 0.9f).coerceIn(0f, 1f)
        )
    } else {
        Color(
            red = (surfaceVariant.red * 1.3f).coerceIn(0f, 1f),
            green = (surfaceVariant.green * 1.3f).coerceIn(0f, 1f),
            blue = (surfaceVariant.blue * 1.3f).coerceIn(0f, 1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("wayve_prefs", android.content.Context.MODE_PRIVATE) }
    
    var nowPlayingData by remember { mutableStateOf<NowPlayingData?>(null) }
    var filteredTracks by remember { mutableStateOf<List<NowPlayingTrack>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var continuousMonitorEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("continuous_monitor_enabled", false)) }
    var selectedMusicApp by remember { mutableStateOf(sharedPrefs.getString("selected_music_app", "YouTube") ?: "YouTube") }
    var forceRefresh by remember { mutableStateOf(0) }
    var showDropdownMenu by remember { mutableStateOf(false) }
    var showPlaySongsWithDialog by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("Time") } // Time, Artist, Album
    var showSortMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showShazamSettings by remember { mutableStateOf(false) }
    
    // Load saved data on first composition
    LaunchedEffect(Unit) {
        val savedData = sharedPrefs.getString("nowplaying_data", null)
        if (savedData != null) {
            try {
                val data = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString<NowPlayingData>(savedData)
                nowPlayingData = data
                filteredTracks = data.tracks
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Save data whenever it changes
    LaunchedEffect(nowPlayingData) {
        nowPlayingData?.let { data ->
            try {
                val jsonString = kotlinx.serialization.json.Json {
                    prettyPrint = false
                }.encodeToString(NowPlayingData.serializer(), data)
                sharedPrefs.edit().putString("nowplaying_data", jsonString).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Auto-refresh every 5 seconds
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            val savedData = sharedPrefs.getString("nowplaying_data", null)
            if (savedData != null) {
                try {
                    val data = kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                    }.decodeFromString<NowPlayingData>(savedData)
                    
                    if (data.tracks.size != nowPlayingData?.tracks?.size) {
                        forceRefresh++
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
    
    // Reload when forceRefresh changes
    LaunchedEffect(forceRefresh) {
        if (forceRefresh > 0) {
            val savedData = sharedPrefs.getString("nowplaying_data", null)
            if (savedData != null) {
                try {
                    val data = kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                    }.decodeFromString<NowPlayingData>(savedData)
                    nowPlayingData = data
                    filteredTracks = data.tracks
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    // Microphone permission launcher
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            continuousMonitorEnabled = true
            sharedPrefs.edit().putBoolean("continuous_monitor_enabled", true).apply()
            MonitorStarter.startMonitoring(context)
            android.widget.Toast.makeText(
                context,
                "Ambient detection enabled",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(
                context,
                "Microphone permission required",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // JSON import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val jsonContent = context.contentResolver.openInputStream(it)?.use { stream ->
                    stream.bufferedReader().readText()
                }
                if (jsonContent != null) {
                    val importedData = NowPlayingParser.parseJson(jsonContent)
                    if (importedData != null) {
                        // Merge with existing data (avoid duplicates)
                        val existingTracks = nowPlayingData?.tracks ?: emptyList()
                        val newTracks = importedData.tracks
                        
                        // Create a set of existing track identifiers (title + artist)
                        val existingTrackIds = existingTracks.map { 
                            "${it.title.lowercase()}_${it.artist.lowercase()}" 
                        }.toSet()
                        
                        // Filter out duplicates from new tracks
                        val uniqueNewTracks = newTracks.filter { track ->
                            val trackId = "${track.title.lowercase()}_${track.artist.lowercase()}"
                            !existingTrackIds.contains(trackId)
                        }
                        
                        // Merge tracks
                        val mergedTracks = existingTracks + uniqueNewTracks
                        val mergedData = NowPlayingData(tracks = mergedTracks)
                        
                        // Save the merged data
                        val jsonString = kotlinx.serialization.json.Json {
                            prettyPrint = false
                        }.encodeToString(NowPlayingData.serializer(), mergedData)
                        sharedPrefs.edit().putString("nowplaying_data", jsonString).apply()
                        
                        // Update UI
                        nowPlayingData = mergedData
                        filteredTracks = mergedData.tracks
                        
                        val message = if (uniqueNewTracks.isEmpty()) {
                            "No new tracks added (all duplicates)"
                        } else {
                            "Added ${uniqueNewTracks.size} new tracks (${mergedTracks.size} total)"
                        }
                        
                        android.widget.Toast.makeText(
                            context,
                            message,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Failed to parse JSON file",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    "Error loading file: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                val savedData = sharedPrefs.getString("nowplaying_data", null)
                if (savedData != null) {
                    val json = kotlinx.serialization.json.Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    }
                    val data = json.decodeFromString<NowPlayingData>(savedData)
                    val prettyJson = json.encodeToString(NowPlayingData.serializer(), data)
                    
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(prettyJson.toByteArray())
                    }
                    
                    android.widget.Toast.makeText(
                        context,
                        "Exported ${data.tracks.size} tracks",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "No data to export",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    "Export failed: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    // Filter tracks based on search
    LaunchedEffect(searchQuery, nowPlayingData) {
        nowPlayingData?.let { data ->
            filteredTracks = if (searchQuery.isBlank()) {
                data.tracks
            } else {
                data.tracks.filter { track ->
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with title, 3-dot menu, and mic button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wayve library",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 3-dot menu button
                    Box {
                        IconButton(
                            onClick = { showDropdownMenu = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Dropdown menu
                        DropdownMenu(
                            expanded = showDropdownMenu,
                            onDismissRequest = { showDropdownMenu = false },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceBright)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDownward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Import JSON",
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    showDropdownMenu = false
                                    importLauncher.launch(arrayOf("application/json"))
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowUpward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Export JSON",
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    showDropdownMenu = false
                                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                                    val fileName = "wayve_library_$timestamp.json"
                                    exportLauncher.launch(fileName)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Play songs with",
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = selectedMusicApp,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showDropdownMenu = false
                                    showPlaySongsWithDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteForever,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Clear all library",
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    showDropdownMenu = false
                                    showClearConfirm = true
                                }
                            )
                        }
                    }
                    
                    // Tertiary colored circular mic button (Material 3)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (continuousMonitorEnabled) 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                
                                if (!hasMicPermission && !continuousMonitorEnabled) {
                                    microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else {
                                    continuousMonitorEnabled = !continuousMonitorEnabled
                                    sharedPrefs
                                        .edit()
                                        .putBoolean("continuous_monitor_enabled", continuousMonitorEnabled)
                                        .apply()
                                    
                                    if (continuousMonitorEnabled) {
                                        MonitorStarter.startMonitoring(context)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Ambient detection enabled",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        MonitorStarter.stopMonitoring(context)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Ambient detection disabled",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.music),
                            contentDescription = if (continuousMonitorEnabled) "Mic ON" else "Mic OFF",
                            tint = if (continuousMonitorEnabled) 
                                MaterialTheme.colorScheme.primaryContainer
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            
            // Search bar
            if (nowPlayingData != null && nowPlayingData!!.tracks.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    placeholder = { 
                        Text(
                            "Search songs & bands",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            
            // Content
            if (nowPlayingData == null || nowPlayingData!!.tracks.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer)
                            .clickable {
                                showShazamSettings = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.music),
                            contentDescription = "Configure Shazam API",
                            tint = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Configure Shazam API",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Track list - group by artist, album, or date depending on sort option
                val groupedTracks = when (sortBy) {
                    "Artist" -> {
                        // Group by artist and sort alphabetically
                        filteredTracks.groupBy { track ->
                            track.artist
                        }.toSortedMap(compareBy { it.lowercase() })
                    }
                    "Album" -> {
                        // Group by first letter of title (alphabetic grouping)
                        filteredTracks.groupBy { track ->
                            track.title.firstOrNull()?.uppercase() ?: "#"
                        }.toSortedMap(compareBy { it })
                    }
                    else -> {
                        // Group by date and sort by most recent
                        filteredTracks.groupBy { track ->
                            track.date ?: "Unknown"
                        }.toSortedMap(compareByDescending { it })
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val headersList = groupedTracks.keys.toList()
                    groupedTracks.forEach { (headerKey, unsortedTracks) ->
                        val isFirstHeader = headerKey == headersList.firstOrNull()
                        
                        // Sort tracks based on selected option
                        val tracks = when (sortBy) {
                            "Artist" -> unsortedTracks // Already grouped by artist, keep order
                            "Album" -> unsortedTracks.sortedBy { it.title.lowercase() }
                            else -> unsortedTracks // Time (keep original order)
                        }
                        
                        // Header (Artist name, Letter, or Date) with sort dropdown
                        item(key = "header_$headerKey") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (sortBy) {
                                        "Artist" -> headerKey
                                        "Album" -> headerKey
                                        else -> formatDateHeader(headerKey)
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                // Sort dropdown (only on first header)
                                if (isFirstHeader) {
                                    Box {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { showSortMenu = true }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Sort: $sortBy",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceBright)
                                        ) {
                                            listOf("Time", "Artist", "Album").forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = option,
                                                            fontWeight = if (sortBy == option) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (sortBy == option) 
                                                                MaterialTheme.colorScheme.primary 
                                                            else 
                                                                MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    onClick = {
                                                        sortBy = option
                                                        showSortMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Track cards
                        tracks.forEachIndexed { index, track ->
                            val isFirst = index == 0
                            val isLast = index == tracks.size - 1
                            val isSingle = tracks.size == 1
                            
                            item(key = "${headerKey}_${index}_${track.title}_${track.artist}_${track.time}") {
                                TrackCard(
                                    track = track,
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    isSingle = isSingle,
                                    onDelete = {
                                        // Remove track from data
                                        val updatedTracks = nowPlayingData?.tracks?.filter {
                                            !(it.title == track.title && it.artist == track.artist && it.time == track.time && it.date == track.date)
                                        } ?: emptyList()
                                        
                                        val updatedData = nowPlayingData?.copy(
                                            tracks = updatedTracks,
                                            statistics = NowPlayingParser.calculateStats(updatedTracks)
                                        )
                                        
                                        // Save updated data
                                        updatedData?.let { data ->
                                            try {
                                                val jsonString = kotlinx.serialization.json.Json {
                                                    prettyPrint = false
                                                }.encodeToString(NowPlayingData.serializer(), data)
                                                sharedPrefs.edit().putString("nowplaying_data", jsonString).apply()
                                                
                                                // Update UI
                                                nowPlayingData = data
                                                
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Song removed",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    onClick = {
                                    val searchQuery = "${track.title} ${track.artist}"
                                    val intent = when (selectedMusicApp) {
                                        "YouTube" -> Intent(Intent.ACTION_VIEW).apply {
                                            // Use search URL with autoplay hint
                                            data = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}&autoplay=1")
                                            // Try to use YouTube app intent
                                            setPackage("com.google.android.youtube")
                                        }
                                        "Spotify" -> Intent(Intent.ACTION_VIEW).apply {
                                            // Use Spotify play intent with search
                                            data = Uri.parse("spotify:search:${Uri.encode(searchQuery)}")
                                            putExtra("android.intent.extra.REFERRER", Uri.parse("android-app://com.wayve.app"))
                                        }
                                        "YT Music" -> Intent(Intent.ACTION_VIEW).apply {
                                            // Use YT Music with autoplay hint
                                            data = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(searchQuery)}&autoplay=1")
                                            setPackage("com.google.android.apps.youtube.music")
                                        }
                                        "YT Revanced" -> Intent(Intent.ACTION_VIEW).apply {
                                            // Use Revanced YT Music with autoplay
                                            data = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(searchQuery)}&autoplay=1")
                                            setPackage("app.revanced.android.apps.youtube.music")
                                        }
                                        else -> Intent(Intent.ACTION_VIEW).apply {
                                            data = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}&autoplay=1")
                                        }
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // If app-specific intent fails, try browser fallback
                                        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                                            data = when (selectedMusicApp) {
                                                "YouTube" -> Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}&autoplay=1")
                                                "YT Music", "YT Revanced" -> Uri.parse("https://music.youtube.com/search?q=${Uri.encode(searchQuery)}&autoplay=1")
                                                "Spotify" -> Uri.parse("https://open.spotify.com/search/${Uri.encode(searchQuery)}")
                                                else -> Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}&autoplay=1")
                                            }
                                        }
                                        try {
                                            context.startActivity(fallbackIntent)
                                        } catch (ex: Exception) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Unable to open $selectedMusicApp",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Play Songs With Dialog
    if (showPlaySongsWithDialog) {
        PlaySongsWithDialog(
            currentApp = selectedMusicApp,
            onDismiss = { showPlaySongsWithDialog = false },
            onAppSelected = { app ->
                selectedMusicApp = app
                sharedPrefs.edit().putString("selected_music_app", app).apply()
                showPlaySongsWithDialog = false
            }
        )
    }
    
    // Clear Library Confirmation Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { 
                Text(
                    "Clear all library?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = { 
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("This will delete all your saved tracks. This action cannot be undone.")
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                sharedPrefs.edit().remove("nowplaying_data").apply()
                                nowPlayingData = NowPlayingData(tracks = emptyList())
                                showClearConfirm = false
                                android.widget.Toast.makeText(
                                    context,
                                    "Library cleared",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                "CLEAR ALL",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        TextButton(
                            onClick = { showClearConfirm = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                "CANCEL",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
    
    // Shazam Settings Dialog
    if (showShazamSettings) {
        ShazamSettingsDialog(
            sharedPrefs = sharedPrefs,
            hidePasswords = sharedPrefs.getBoolean("hide_passwords", true),
            onDismiss = { showShazamSettings = false }
        )
    }
}

@Composable
private fun PlaySongsWithDialog(
    currentApp: String,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Play songs with",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                val apps = listOf("YouTube", "Spotify", "YT Music", "YT Revanced")
                apps.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAppSelected(app) }
                            .padding(start = 0.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentApp == app,
                            onClick = { onAppSelected(app) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = app,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "CANCEL",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun TrackCard(
    track: NowPlayingTrack,
    isFirst: Boolean,
    isLast: Boolean,
    isSingle: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val maxSwipeDistance = -200f // Limit swipe to 100dp to the left
    
    val draggableState = rememberDraggableState { delta ->
        val newOffset = (offsetX + delta).coerceIn(maxSwipeDistance, 0f)
        offsetX = newOffset
    }
    
    val cardShape = when {
        isSingle -> RoundedCornerShape(20.dp)
        isFirst -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        isLast -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        else -> RoundedCornerShape(0.dp)
    }
    
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Trash icon background
        if (offsetX < -10f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(vertical = 1.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.trash),
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .size(24.dp)
                        .clickable {
                            onDelete()
                        }
                )
            }
        }
        
        // Card that slides
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        // Snap to position
                        offsetX = if (offsetX < -30f) {
                            maxSwipeDistance // Snap to revealed
                        } else {
                            0f // Snap back to original
                        }
                    }
                )
                .clickable(onClick = onClick),
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art or placeholder
                if (track.albumArt != null) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(track.albumArt)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.music),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = track.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = "${track.artist} • ${track.time ?: ""}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Secondary colored play button (Material 3)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    }
}

private fun formatDateHeader(date: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateObj = inputFormat.parse(date)
        
        // Check if today
        val today = Calendar.getInstance()
        val trackDate = Calendar.getInstance()
        trackDate.time = dateObj ?: Date()
        
        if (today.get(Calendar.YEAR) == trackDate.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == trackDate.get(Calendar.DAY_OF_YEAR)
        ) {
            "Today"
        } else {
            outputFormat.format(dateObj ?: Date())
        }
    } catch (e: Exception) {
        date
    }
}

