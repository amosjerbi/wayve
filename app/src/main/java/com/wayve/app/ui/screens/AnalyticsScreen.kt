package com.wayve.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayve.app.MainViewModel
import com.wayve.app.R
import com.wayve.app.data.NowPlayingData
import com.wayve.app.data.NowPlayingParser

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

@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("wayve_prefs", android.content.Context.MODE_PRIVATE) }
    var nowPlayingData by remember { mutableStateOf<NowPlayingData?>(null) }
    
    // Load data on composition
    LaunchedEffect(Unit) {
        val savedData = sharedPrefs.getString("nowplaying_data", null)
        if (savedData != null) {
            try {
                val data = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString<NowPlayingData>(savedData)
                nowPlayingData = data
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
                    nowPlayingData = data
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (nowPlayingData == null || nowPlayingData!!.tracks.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                
                Image(
                    painter = painterResource(id = R.drawable.emptystate3),
                    contentDescription = null,
                    modifier = Modifier.size(180.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Songs are missing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Show analytics
            AnalyticsContent(nowPlayingData!!)
        }
    }
}

@Composable
private fun AnalyticsContent(data: NowPlayingData) {
    val stats = data.statistics ?: NowPlayingParser.calculateStats(data.tracks)
    
    // Calculate duplicates (songs that appear more than once)
    val duplicatesCount = data.tracks
        .groupBy { "${it.title}|${it.artist}" }
        .filter { it.value.size > 1 }
        .values
        .sumOf { it.size - 1 }
    
    // Calculate total playtime (average song is ~3.5 minutes)
    val totalMinutes = data.tracks.size * 3.5
    val totalPlaytime = when {
        totalMinutes < 60 -> "${totalMinutes.toInt()}m"
        totalMinutes < 1440 -> {
            val hours = (totalMinutes / 60).toInt()
            val mins = (totalMinutes % 60).toInt()
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        }
        else -> {
            val days = (totalMinutes / 1440).toInt()
            val hours = ((totalMinutes % 1440) / 60).toInt()
            if (hours > 0) "${days}d ${hours}h" else "${days}d"
        }
    }
    
    // Calculate top songs (most played) - only duplicates
    val topSongs = data.tracks
        .groupBy { "${it.title}|${it.artist}" }
        .mapValues { it.value.size }
        .filter { it.value > 1 }
        .toList()
        .sortedByDescending { it.second }
        .take(10)
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Text(
                text = "Analytics",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        // Stats cards - Row 1
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Duplicates",
                    value = duplicatesCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Unique Artists",
                    value = stats.unique_artists.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Stats cards - Row 2
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Unique Songs",
                    value = stats.unique_songs.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total playtime",
                    value = totalPlaytime,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Most Played Songs
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Most Played Songs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (topSongs.isEmpty()) {
                        Text(
                            text = "No duplicate songs found",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    } else {
                        // Table header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SONG",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "PLAYS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Table rows
                        topSongs.forEach { (songKey, count) ->
                            val (title, artist) = songKey.split("|")
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = artist,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                    
                                    Text(
                                        text = count.toString(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                if (topSongs.last() != (songKey to count)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        thickness = 1.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

