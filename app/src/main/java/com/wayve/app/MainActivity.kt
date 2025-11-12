package com.wayve.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import kotlin.system.exitProcess
import com.wayve.app.ui.theme.ButtonStyles
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import com.wayve.app.ui.screens.NowPlayingScreen
import com.wayve.app.ui.screens.BacklogScreen
import com.wayve.app.ui.screens.SettingsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.wayve.app.ui.theme.AppTheme
import com.wayve.app.ui.theme.NavyBlue
import com.wayve.app.network.SpotifyAuthManager
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.util.Locale

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { 
        MainViewModelFactory(applicationContext)
    }

    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle Spotify OAuth callback from browser (PKCE flow)
        Log.d("SpotifyAuth", "=== onNewIntent callback ===")
        Log.d("SpotifyAuth", "Intent: $intent")
        Log.d("SpotifyAuth", "Intent data: ${intent.data}")
        
        val uri = intent.data
        if (uri != null && uri.scheme == "wayve" && uri.host == "spotify-callback") {
            Log.d("SpotifyAuth", "Processing Spotify callback URI: $uri")
            
            // PKCE flow returns the code as a query parameter
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            
            Log.d("SpotifyAuth", "Parsed - code present: ${code != null}, error: $error")
            
            when {
                code != null -> {
                    Log.i("SpotifyAuth", "✅ Authorization CODE received: ${code.take(20)}...")
                    
                    // Exchange code for access token in background (IO dispatcher for network)
                    val spotifyAuthManager = SpotifyAuthManager(this)
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val success = spotifyAuthManager.exchangeCodeForToken(code)
                        
                        // Switch back to main thread for UI updates
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (success) {
                                Log.i("SpotifyAuth", "✅ Token exchange successful")
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "✅ Connected to Spotify!",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Log.e("SpotifyAuth", "❌ Token exchange failed")
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "❌ Failed to connect to Spotify",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
                error != null -> {
                    Log.e("SpotifyAuth", "❌ OAuth ERROR: $error")
                    android.widget.Toast.makeText(
                        this,
                        "❌ Spotify login failed: $error",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    Log.w("SpotifyAuth", "No code or error in URI")
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle Spotify OAuth callback if app was launched from browser
        Log.d("SpotifyAuth", "onCreate called with intent: $intent")
        Log.d("SpotifyAuth", "Intent data: ${intent?.data}")
        if (intent?.data != null) {
            Log.d("SpotifyAuth", "Processing OAuth callback in onCreate")
            onNewIntent(intent)
        }
        
        // System bars will be handled dynamically in setContent based on theme
        
        // Start continuous audio monitoring (AmbientMusicMod-style)
        MonitorStarter.startMonitoring(this)
        Log.d("MainActivity", "Continuous monitoring service started")
        
        setContent {
            AppTheme {
                // Configure system bars based on theme
                val colorScheme = MaterialTheme.colorScheme
                LaunchedEffect(colorScheme) {
                    window.statusBarColor = colorScheme.surfaceContainer.toArgb()
                    window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        window.decorView.post {
                            val isLightTheme = colorScheme.surfaceContainer.luminance() > 0.5
                            window.insetsController?.setSystemBarsAppearance(
                                if (isLightTheme) {
                                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                                } else 0,
                                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                            )
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        run {
                            val isLightTheme = colorScheme.background.luminance() > 0.5
                            var flags = if (isLightTheme) android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && isLightTheme) {
                                flags = flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                            }
                            window.decorView.post { window.decorView.systemUiVisibility = flags }
                        }
                    }
                }
                
                RomApp(viewModel = viewModel, downloader = AndroidDownloader(this))
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Save backlog state when app goes to background
        viewModel.saveBacklogState()
    }
    
    override fun onStop() {
        super.onStop()
        // Additional save when app is stopped
        viewModel.saveBacklogState()
    }
}

data class Foldername(
    val id: String, 
    val label: String, 
    val archiveUrl: String, 
    val extensions: List<String>,
    val isCustom: Boolean = false
)

object FoldernameManager {
    private val defaultFoldernames = emptyList<Foldername>()
    
    private var customFoldernames = mutableListOf<Foldername>()
    
    val all: List<Foldername>
        get() = defaultFoldernames + customFoldernames
    
    fun loadCustomFoldernames(context: Context) {
        val sp = context.getSharedPreferences("custom_platforms", Context.MODE_PRIVATE)
        val platformsJson = sp.getString("platforms", "[]")
        try {
            customFoldernames.clear()
            // Simple JSON parsing for custom platforms
            if (platformsJson != "[]") {
                val platformsData = parseCustomFoldernamesJson(platformsJson!!)
                customFoldernames.addAll(platformsData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun saveCustomFoldernames(context: Context) {
        val sp = context.getSharedPreferences("custom_platforms", Context.MODE_PRIVATE)
        val json = customFoldernamesToJson(customFoldernames)
        sp.edit().putString("platforms", json).apply()
    }
    
    fun addCustomFoldername(context: Context, platform: Foldername) {
        val customFoldername = platform.copy(isCustom = true)
        customFoldernames.removeAll { it.id == customFoldername.id }
        customFoldernames.add(customFoldername)
        saveCustomFoldernames(context)
    }
    
    fun removeCustomFoldername(context: Context, platformId: String) {
        customFoldernames.removeAll { it.id == platformId }
        saveCustomFoldernames(context)
    }
    
    fun updateCustomFoldername(context: Context, platform: Foldername) {
        val index = customFoldernames.indexOfFirst { it.id == platform.id }
        if (index != -1) {
            customFoldernames[index] = platform.copy(isCustom = true)
            saveCustomFoldernames(context)
        }
    }
    
    fun exportFoldernames(): String {
        val exportData = StringBuilder()
        exportData.appendLine("# wayve backup file")
        exportData.appendLine("# Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        exportData.appendLine()
        
        all.forEach { platform ->
            exportData.appendLine("[Foldername]")
            exportData.appendLine("Name=${platform.label}")
            exportData.appendLine("URL=${platform.archiveUrl}")
            exportData.appendLine("Extensions=${platform.extensions.joinToString(",")}")
            exportData.appendLine("Custom=${platform.isCustom}")
            exportData.appendLine()
        }
        
        return exportData.toString()
    }
    
    fun clearAllCustomFoldernames(context: Context) {
        customFoldernames.clear()
        saveCustomFoldernames(context)
    }
    
    fun importFoldernames(context: Context, fileContent: String): Int {
        try {
            val lines = fileContent.lines()
            var currentFoldername: MutableMap<String, String>? = null
            var importedCount = 0
            
            lines.forEach { line ->
                val trimmedLine = line.trim()
                when {
                    trimmedLine.startsWith("[Foldername]") -> {
                        currentFoldername = mutableMapOf()
                    }
                    trimmedLine.startsWith("Name=") -> {
                        currentFoldername?.put("name", trimmedLine.substringAfter("="))
                    }
                    trimmedLine.startsWith("URL=") -> {
                        currentFoldername?.put("url", trimmedLine.substringAfter("="))
                    }
                    trimmedLine.startsWith("Extensions=") -> {
                        currentFoldername?.put("extensions", trimmedLine.substringAfter("="))
                    }
                    trimmedLine.isEmpty() && currentFoldername != null -> {
                        // End of foldername definition
                        val name = currentFoldername?.get("name")
                        val url = currentFoldername?.get("url")
                        val extensions = currentFoldername?.get("extensions")?.split(",") ?: emptyList()
                        
                        if (name != null && url != null) {
                            val platformId = name.lowercase().replace(" ", "")
                            addCustomFoldername(context, Foldername(
                                id = platformId,
                                label = name,
                                archiveUrl = url,
                                extensions = extensions,
                                isCustom = true
                            ))
                            importedCount++
                        }
                        
                        currentFoldername = null
                    }
                }
            }
            
            // Handle last foldername if file doesn't end with empty line
            currentFoldername?.let {
                val name = it["name"]
                val url = it["url"]
                val extensions = it["extensions"]?.split(",") ?: emptyList()
                
                if (name != null && url != null) {
                    val platformId = name.lowercase().replace(" ", "")
                    addCustomFoldername(context, Foldername(
                        id = platformId,
                        label = name,
                        archiveUrl = url,
                        extensions = extensions,
                        isCustom = true
                    ))
                    importedCount++
                }
            }
            
            return importedCount
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }
}

fun parseCustomFoldernamesJson(json: String): List<Foldername> {
    // Simple JSON parsing for custom foldernames array
    val platforms = mutableListOf<Foldername>()
    
    try {
        // Remove outer brackets
        val content = json.trim().removeSurrounding("[", "]")
        
        // Split by },{ to get individual objects
        val objects = content.split("},")
        
        objects.forEach { obj ->
            var cleanObj = obj.trim().removeSurrounding("{", "}")
            if (!cleanObj.endsWith("}")) {
                cleanObj += "}"
            }
            cleanObj = cleanObj.removeSuffix("}")
            
            // Parse key-value pairs
            val pairs = mutableMapOf<String, String>()
            var currentKey = ""
            var currentValue = ""
            var inQuotes = false
            var inArray = false
            var arrayContent = ""
            
            var i = 0
            while (i < cleanObj.length) {
                val char = cleanObj[i]
                
                when {
                    char == '"' -> {
                        inQuotes = !inQuotes
                        if (inQuotes && currentKey.isEmpty()) {
                            // Start of key
                            var endQuote = cleanObj.indexOf('"', i + 1)
                            currentKey = cleanObj.substring(i + 1, endQuote)
                            i = endQuote
                        } else if (!inQuotes && currentKey.isNotEmpty() && currentValue.isEmpty()) {
                            // Start of value
                            var endQuote = cleanObj.indexOf('"', i + 1)
                            // Handle escaped quotes
                            while (endQuote > 0 && cleanObj[endQuote - 1] == '\\') {
                                endQuote = cleanObj.indexOf('"', endQuote + 1)
                            }
                            currentValue = cleanObj.substring(i + 1, endQuote)
                            pairs[currentKey] = currentValue
                            currentKey = ""
                            currentValue = ""
                            i = endQuote
                        }
                    }
                    char == '[' && currentKey == "extensions" -> {
                        inArray = true
                        arrayContent = ""
                    }
                    char == ']' && inArray -> {
                        inArray = false
                        pairs[currentKey] = arrayContent
                        currentKey = ""
                        arrayContent = ""
                    }
                    inArray -> {
                        if (char != '"' && char != ',') {
                            arrayContent += char
                        } else if (char == ',') {
                            arrayContent += ","
                        }
                    }
                }
                i++
            }
            
            // Create Foldername object from pairs
            val id = pairs["id"] ?: ""
            val label = pairs["label"] ?: ""
            val archiveUrl = pairs["archiveUrl"] ?: ""
            val extensions = pairs["extensions"]?.split(",")?.map { it.trim() } ?: emptyList()
            
            if (id.isNotEmpty() && label.isNotEmpty()) {
                platforms.add(
                    Foldername(
                        id = id,
                        label = label,
                        archiveUrl = archiveUrl,
                        extensions = extensions,
                        isCustom = true
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return platforms
}

fun customFoldernamesToJson(foldernames: List<Foldername>): String {
    if (foldernames.isEmpty()) return "[]"
    
    val jsonObjects = foldernames.map { platform ->
        val extensionsJson = platform.extensions.joinToString(",") { "\"$it\"" }
        """{"id":"${platform.id}","label":"${platform.label}","archiveUrl":"${platform.archiveUrl}","extensions":[$extensionsJson]}"""
    }
    
    return "[${jsonObjects.joinToString(",")}]"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomApp(viewModel: MainViewModel, downloader: Downloader) {
    var selectedTab by remember { mutableStateOf(0) } // 0=Library, 1=Analytics, 2=Settings
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(
                                id = if (selectedTab == 0) R.drawable.library_selected else R.drawable.library
                            ),
                            contentDescription = "Library",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { 
                        Text(
                            "Library",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(
                                id = if (selectedTab == 1) R.drawable.analytics_selected else R.drawable.analytics
                            ),
                            contentDescription = "Analytics",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { 
                        Text(
                            "Analytics",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(
                                id = if (selectedTab == 2) R.drawable.settings_selected else R.drawable.settings
                            ),
                            contentDescription = "Settings",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { 
                        Text(
                            "Settings",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> NowPlayingScreen(viewModel)
                1 -> BacklogScreen(viewModel)
                2 -> SettingsScreen(viewModel)
            }
        }
    }
}

// Old unused code - commented out for new design
/*
@Composable
private fun OldBrowseScreen(viewModel: MainViewModel, downloader: Downloader) {
    // Not used in new design
}
*/

// Old unused functions - stubbed for compilation
/*
@Composable
fun ResultsList(
    results: List<WebResult>,
    onDownload: (WebResult) -> Unit,
    onUpload: (WebResult) -> Unit,
    onDownloadAndTransfer: ((WebResult) -> Unit)? = null
) {
    // Not used in new design
}
*/

data class WebResult(
    val downloadUrl: String,
    val displayName: String,
    val sizeStr: String,
    val platform: Foldername
)

interface Downloader {
    fun download(context: Context, item: WebResult)
}

class AndroidDownloader(private val activity: ComponentActivity) : Downloader {
    override fun download(context: Context, item: WebResult) {
        // Stub - not used in new design
    }
}
