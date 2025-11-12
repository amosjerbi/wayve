package com.wayve.app.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wayve.app.MainViewModel
import com.wayve.app.R
import com.wayve.app.data.NowPlayingData
import com.wayve.app.data.NowPlayingParser
import com.wayve.app.service.NowPlayingAccessibilityService
import com.wayve.app.service.NowPlayingCaptureService
import com.wayve.app.ui.dialogs.ShazamSettingsDialog
import com.wayve.app.ui.dialogs.SpotifySettingsDialog
import com.wayve.app.network.SpotifyAuthManager
import com.wayve.app.network.SpotifyPlaylistManager
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("wayve_prefs", android.content.Context.MODE_PRIVATE) }
    var showShazamSettings by remember { mutableStateOf(false) }
    var showSpotifySettings by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    
    // Password visibility toggle
    var hidePasswords by remember { 
        mutableStateOf(sharedPrefs.getBoolean("hide_passwords", true)) 
    }
    
    // Spotify OAuth state
    val spotifyAuthManager = remember { SpotifyAuthManager(context) }
    var isSpotifySignedIn by remember { mutableStateOf(spotifyAuthManager.isSignedIn()) }
    var isCreatingPlaylist by remember { mutableStateOf(false) }
    var playlistProgress by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    
    // Refresh sign-in state when screen becomes visible (after returning from browser)
    LaunchedEffect(Unit) {
        // Check immediately on launch
        isSpotifySignedIn = spotifyAuthManager.isSignedIn()
        android.util.Log.d("SpotifyAuth", "Initial sign-in check: $isSpotifySignedIn")
        
        // Keep checking for updates
        while (true) {
            kotlinx.coroutines.delay(500) // Check every 500ms
            val newState = spotifyAuthManager.isSignedIn()
            if (newState != isSpotifySignedIn) {
                android.util.Log.d("SpotifyAuth", "Sign-in state changed: $isSpotifySignedIn -> $newState")
                isSpotifySignedIn = newState
            }
        }
    }
    
    // Directory picker for save location
    val saveLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            android.widget.Toast.makeText(
                context,
                "Save location selected",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            item {
                Text(
                    text = "Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Capture Now Playing data Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Capture Now Playing data",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceBright
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column {
                            SettingsItem(
                                title = "Accessibility permission",
                                subtitle = "Tap to Enable settings",
                                badge = "1",
                                onClick = {
                                    val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, thickness = 1.dp)
                            SettingsItem(
                                title = "Choose save location",
                                subtitle = "Where to save JSON file",
                                badge = "2",
                                onClick = { saveLocationLauncher.launch(null) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, thickness = 1.dp)
                            SettingsItem(
                                title = "Open Now Playing",
                                subtitle = "Start capturing your data",
                                badge = "3",
                                onClick = {
                                    // Check if accessibility service is enabled
                                    if (!NowPlayingAccessibilityService.isServiceEnabled) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Please enable Accessibility permission first (Step 1)",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        return@SettingsItem
                                    }
                                    
                                    // Open Now Playing app and start capture
                                    val captureService = NowPlayingCaptureService(context)
                                    val opened = captureService.openNowPlayingApp()
                                    
                                    if (opened) {
                                        // Wait a moment for the app to open, then start capture
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            NowPlayingAccessibilityService.requestStartCapture()
                                            
                                            android.widget.Toast.makeText(
                                                context,
                                                "Auto-scroll capture started! Recording to JSON...",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }, 2000)
                                        
                                        android.widget.Toast.makeText(
                                            context,
                                            "Opening Now Playing app...",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Failed to open Now Playing app. Make sure it's installed.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Shazam Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Shazam",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceBright
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column {
                            SettingsItem(
                                title = "Shazam API Settings",
                                trailingIconRes = R.drawable.shazam_logo,
                                onClick = { showShazamSettings = true }
                            )
                        }
                    }
                }
            }
            
            // Spotify Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Spotify",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceBright
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column {
                            SettingsItem(
                                title = "Spotify API Settings",
                                subtitle = null,
                                trailingIconRes = R.drawable.spotify_logo,
                                onClick = { showSpotifySettings = true }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, thickness = 1.dp)
                            SettingsItem(
                                title = if (isSpotifySignedIn) "Disconnect Spotify" else "Connect to Spotify",
                                subtitle = null,
                                onClick = {
                                    if (isSpotifySignedIn) {
                                        // Sign out
                                        spotifyAuthManager.signOut()
                                        isSpotifySignedIn = false
                                        android.widget.Toast.makeText(
                                            context,
                                            "Disconnected from Spotify",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        // Check if configured
                                        if (!spotifyAuthManager.isConfigured()) {
                                            android.util.Log.w("SpotifyAuth", "Client ID or Secret not configured")
                                            android.widget.Toast.makeText(
                                                context,
                                                "⚠️ Please configure Spotify API Settings first",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            return@SettingsItem
                                        }
                                        
                                        // Start OAuth flow using PKCE (secure modern method)
                                        android.util.Log.d("SpotifyAuth", "=== Starting Spotify OAuth (PKCE) ===")
                                        
                                        try {
                                            // Generate authorization URL with PKCE
                                            val authUrl = spotifyAuthManager.buildAuthorizationUrl()
                                            
                                            // Open in browser
                                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            
                                            context.startActivity(intent)
                                            
                                            android.util.Log.d("SpotifyAuth", "Browser opened")
                                            android.widget.Toast.makeText(
                                                context,
                                                "Opening browser to login...",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Exception) {
                                            android.util.Log.e("SpotifyAuth", "Failed to start OAuth", e)
                                            android.widget.Toast.makeText(
                                                context,
                                                "❌ Failed to start login: ${e.message}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, thickness = 1.dp)
                            SettingsItem(
                                title = "Create Spotify Playlist",
                                subtitle = when {
                                    isCreatingPlaylist -> playlistProgress
                                    else -> null
                                },
                                onClick = {
                                    if (isCreatingPlaylist) return@SettingsItem
                                    
                                    if (!isSpotifySignedIn) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Please connect to Spotify first",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        return@SettingsItem
                                    }
                                    
                                    // Load library tracks
                                    val json = sharedPrefs.getString("nowplaying_data", null)
                                    if (json == null) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "No tracks in library",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        return@SettingsItem
                                    }
                                    
                                    try {
                                        val data = Json.decodeFromString<NowPlayingData>(json)
                                        if (data.tracks.isEmpty()) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "No tracks in library",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            return@SettingsItem
                                        }
                                        
                                        isCreatingPlaylist = true
                                        playlistProgress = "Starting..."
                                        
                                        scope.launch {
                                            val playlistManager = SpotifyPlaylistManager(context, spotifyAuthManager)
                                            val playlistName = "Wayve Library - ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}"
                                            
                                            val result = playlistManager.createPlaylistFromTracks(
                                                tracks = data.tracks,
                                                playlistName = playlistName,
                                                onProgress = { current, total, trackName ->
                                                    playlistProgress = "Adding $current/$total"
                                                }
                                            )
                                            
                                            isCreatingPlaylist = false
                                            playlistProgress = ""
                                            
                                            if (result.success) {
                                                val message = "✅ Playlist created! ${result.addedCount}/${result.totalCount} tracks added"
                                                android.widget.Toast.makeText(
                                                    context,
                                                    message,
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                                
                                                // Open the playlist
                                                result.playlistUrl?.let { url ->
                                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(intent)
                                                }
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "❌ ${result.error}",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isCreatingPlaylist = false
                                        playlistProgress = ""
                                        android.widget.Toast.makeText(
                                            context,
                                            "❌ Failed: ${e.message}",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Security Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Security",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceBright
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hide passwords and tokens",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(0.dp))
                                Text(
                                    text = "Mask sensitive information in text fields",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            
                            Switch(
                                checked = hidePasswords,
                                onCheckedChange = { 
                                    hidePasswords = it
                                    sharedPrefs.edit().putBoolean("hide_passwords", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Shazam Settings Dialog
    if (showShazamSettings) {
        ShazamSettingsDialog(
            sharedPrefs = sharedPrefs,
            hidePasswords = hidePasswords,
            onDismiss = { showShazamSettings = false }
        )
    }
    
    // Spotify Settings Dialog
    if (showSpotifySettings) {
        SpotifySettingsDialog(
            sharedPrefs = sharedPrefs,
            hidePasswords = hidePasswords,
            onDismiss = { showSpotifySettings = false }
        )
    }
    
    // Clear Confirmation Dialog
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
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIconRes: Int? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        
        if (badge != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        if (subtitle != null && badge == null && trailingIcon == null && trailingIconRes == null) {
            // Show the subtitle value as trailing text
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
        
        if (trailingIconRes != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = trailingIconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
