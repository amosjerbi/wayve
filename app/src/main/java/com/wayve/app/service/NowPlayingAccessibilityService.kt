package com.wayve.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wayve.app.data.NowPlayingData
import com.wayve.app.data.NowPlayingParser
import com.wayve.app.data.NowPlayingTrack
import kotlinx.coroutines.*
import java.io.File

/**
 * Accessibility Service to automatically capture Now Playing history
 * This service can read the screen content and extract music data
 */
class NowPlayingAccessibilityService : AccessibilityService() {
    
    private val capturedTracks = mutableSetOf<String>()
    private val allTracks = mutableListOf<NowPlayingTrack>()
    private var isCapturing = false
    private var captureJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scrollAttempts = 0
    private val maxScrollAttempts = 150
    private var consecutiveEmptyScrolls = 0
    private val maxConsecutiveEmptyScrolls = Int.MAX_VALUE // Unlimited
    private var lastTrackCount = 0
    private var lastScrollTime = 0L
    
    companion object {
        private const val TAG = "NowPlayingCapture"
        var isServiceEnabled = false
        var captureCallback: ((List<NowPlayingTrack>) -> Unit)? = null
        var progressCallback: ((Int, Int, Int) -> Unit)? = null // (trackCount, scrollAttempts, emptyScrolls)
        const val ACTION_START_CAPTURE = "com.wayve.app.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.wayve.app.STOP_CAPTURE"
        
        private var serviceInstance: NowPlayingAccessibilityService? = null
        
        fun requestStartCapture() {
            serviceInstance?.startCapture()
        }
        
        fun requestStopCapture() {
            serviceInstance?.stopCapture()
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceEnabled = true
        serviceInstance = this
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isCapturing) return
        
        event?.let {
            // Process ANY events from Now Playing app
            if (it.packageName == "com.google.android.as") {
                when (it.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                        // Debounce rapid events - capture after things settle
                        handler.removeCallbacksAndMessages(null)
                        handler.postDelayed({
                            captureCurrentScreen()
                        }, 500)
                    }
                }
            }
        }
    }
    
    override fun onInterrupt() {
        stopCapture()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                startCapture()
            }
            ACTION_STOP_CAPTURE -> {
                stopCapture()
            }
        }
        return START_STICKY
    }
    
    private fun startCapture() {
        if (isCapturing) return
        
        android.util.Log.d("NowPlayingCapture", "===== STARTING CAPTURE =====")
        isCapturing = true
        scrollAttempts = 0
        consecutiveEmptyScrolls = 0
        lastTrackCount = 0
        capturedTracks.clear()
        allTracks.clear()
        
        // Initial capture
        captureJob = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            android.util.Log.d("NowPlayingCapture", "Performing initial capture...")
            captureCurrentScreen()
            
            delay(500)
            
            // Start auto-scrolling loop in background
            android.util.Log.d("NowPlayingCapture", "Starting auto-scroll loop...")
            startAutoScrollLoop()
        }
    }
    
    private fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        
        // Save to SharedPreferences
        if (allTracks.isNotEmpty()) {
            try {
                val sharedPrefs = applicationContext.getSharedPreferences("wayve_prefs", android.content.Context.MODE_PRIVATE)
                
                // Merge with existing data
                val existingJson = sharedPrefs.getString("nowplaying_data", null)
                val existingData = if (existingJson != null) {
                    try {
                        kotlinx.serialization.json.Json {
                            ignoreUnknownKeys = true
                        }.decodeFromString<NowPlayingData>(existingJson)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
                
                // Merge tracks
                val mergedTracks = if (existingData != null) {
                    val existingTrackKeys = existingData.tracks.map { "${it.title}|${it.artist}" }.toSet()
                    val newTracks = allTracks.filter { track ->
                        val key = "${track.title}|${track.artist}"
                        !existingTrackKeys.contains(key)
                    }
                    existingData.tracks + newTracks
                } else {
                    allTracks.toList()
                }
                
                // Create new data object with stats
                val newData = NowPlayingData(
                    exported = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    source = "Now Playing Accessibility Service",
                    device = android.os.Build.MODEL,
                    method = "Auto-scroll capture",
                    tracks = mergedTracks,
                    statistics = NowPlayingParser.calculateStats(mergedTracks),
                    capture_info = null
                )
                
                // Save to SharedPreferences
                val jsonString = kotlinx.serialization.json.Json {
                    prettyPrint = false
                }.encodeToString(NowPlayingData.serializer(), newData)
                sharedPrefs.edit().putString("nowplaying_data", jsonString).apply()
                
                android.util.Log.d(TAG, "Saved ${allTracks.size} new tracks (total: ${mergedTracks.size})")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error saving data", e)
            }
        }
        
        // Notify callback with results
        if (allTracks.isNotEmpty()) {
            captureCallback?.invoke(allTracks.toList())
        }
    }
    
    private fun captureCurrentScreen() {
        if (!isCapturing) return
        
        try {
            val root = rootInActiveWindow ?: return
            val newTracks = extractTracksFromNode(root)
            
            val previousCount = allTracks.size
            newTracks.forEach { track ->
                val key = "${track.title}|${track.artist}"
                if (!capturedTracks.contains(key)) {
                    capturedTracks.add(key)
                    allTracks.add(track)
                }
            }
            
            // Update progress whenever we capture
            progressCallback?.invoke(allTracks.size, scrollAttempts, consecutiveEmptyScrolls)
            android.util.Log.d("NowPlayingCapture", "Captured: tracks=${allTracks.size}, newThisPage=${allTracks.size - previousCount}")
            
            // Save periodically (every 10 new tracks)
            if (allTracks.size > 0 && allTracks.size % 10 == 0) {
                saveCurrentProgress()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun saveCurrentProgress() {
        try {
            val sharedPrefs = applicationContext.getSharedPreferences("wayve_prefs", android.content.Context.MODE_PRIVATE)
            
            // Get existing data
            val existingJson = sharedPrefs.getString("nowplaying_data", null)
            val existingData = if (existingJson != null) {
                try {
                    kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                    }.decodeFromString<NowPlayingData>(existingJson)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            
            // Merge tracks
            val mergedTracks = if (existingData != null) {
                val existingTrackKeys = existingData.tracks.map { "${it.title}|${it.artist}" }.toSet()
                val newTracks = allTracks.filter { track ->
                    val key = "${track.title}|${track.artist}"
                    !existingTrackKeys.contains(key)
                }
                existingData.tracks + newTracks
            } else {
                allTracks.toList()
            }
            
            // Create new data object
            val newData = NowPlayingData(
                exported = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                source = "Now Playing Accessibility Service",
                device = android.os.Build.MODEL,
                method = "Auto-scroll capture (in progress)",
                tracks = mergedTracks,
                statistics = NowPlayingParser.calculateStats(mergedTracks),
                capture_info = null
            )
            
            // Save to SharedPreferences
            val jsonString = kotlinx.serialization.json.Json {
                prettyPrint = false
            }.encodeToString(NowPlayingData.serializer(), newData)
            sharedPrefs.edit().putString("nowplaying_data", jsonString).apply()
            
            android.util.Log.d(TAG, "Progress saved: ${allTracks.size} tracks captured so far")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error saving progress", e)
        }
    }
    
    private fun startAutoScrollLoop() {
        android.util.Log.d("NowPlayingCapture", "AUTO-SCROLL LOOP STARTED")
        
        // Launch in a separate coroutine to ensure it runs independently
        CoroutineScope(Dispatchers.Main).launch {
            var failedScrollAttempts = 0
            
            while (isCapturing && scrollAttempts < maxScrollAttempts) {
                delay(800) // Wait between scroll attempts
                
                if (!isCapturing) {
                    android.util.Log.d("NowPlayingCapture", "Capture stopped, exiting scroll loop")
                    break
                }
                
                android.util.Log.d("NowPlayingCapture", "--- Scroll attempt ${scrollAttempts + 1} ---")
                
                val previousCount = allTracks.size
                val scrollSuccess = performScroll()
                
                if (scrollSuccess) {
                    scrollAttempts++
                    failedScrollAttempts = 0 // Reset failed attempts
                    android.util.Log.d("NowPlayingCapture", "✓ Scroll $scrollAttempts executed successfully")
                    
                    // Wait for UI to update and new content to load
                    delay(1000)
                    
                    // Check if we got new tracks
                    if (allTracks.size == previousCount) {
                        consecutiveEmptyScrolls++
                        android.util.Log.d("NowPlayingCapture", "No new tracks after scroll (empty: $consecutiveEmptyScrolls/$maxConsecutiveEmptyScrolls, total tracks: ${allTracks.size})")
                        
                        // Try a more aggressive scroll when we're not finding new content
                        if (consecutiveEmptyScrolls >= 2 && consecutiveEmptyScrolls < 5) {
                            android.util.Log.d("NowPlayingCapture", "Trying double scroll to push past stuck point...")
                            delay(300)
                            performScroll()
                            delay(800)
                        }
                    } else {
                        consecutiveEmptyScrolls = 0
                        val newTracks = allTracks.size - previousCount
                        android.util.Log.d("NowPlayingCapture", "✓ Scroll $scrollAttempts: +$newTracks tracks (total: ${allTracks.size})")
                    }
                    
                    // Stop if too many consecutive empty scrolls
                    if (consecutiveEmptyScrolls >= maxConsecutiveEmptyScrolls) {
                        android.util.Log.d("NowPlayingCapture", "STOPPING: $consecutiveEmptyScrolls consecutive empty scrolls. Final count: ${allTracks.size} tracks")
                        stopCapture()
                        break
                    }
                } else {
                    // Scroll failed
                    failedScrollAttempts++
                    consecutiveEmptyScrolls++
                    android.util.Log.w("NowPlayingCapture", "✗ Scroll attempt FAILED ($failedScrollAttempts failures)")
                    
                    if (failedScrollAttempts >= 3) {
                        android.util.Log.e("NowPlayingCapture", "STOPPING: Multiple scroll failures (${allTracks.size} tracks captured)")
                        stopCapture()
                        break
                    }
                    
                    delay(800) // Wait before retry
                }
            }
            
            if (scrollAttempts >= maxScrollAttempts) {
                android.util.Log.d("NowPlayingCapture", "STOPPING: Reached max scrolls. Total: ${allTracks.size} tracks")
                stopCapture()
            }
        }
    }
    
    private fun performScroll(): Boolean {
        try {
            val root = rootInActiveWindow
            if (root == null) {
                android.util.Log.w("NowPlayingCapture", "Cannot scroll: rootInActiveWindow is null")
                return false
            }
            
            // Check if we can find any song rows (simple check)
            val hasSongRows = findNodesByResourceId(root, "com.google.android.as:id/song_row").isNotEmpty()
            if (!hasSongRows) {
                android.util.Log.w("NowPlayingCapture", "⚠️ No song rows found, may not be on history page")
                // Don't fail immediately, just log warning and try to scroll anyway
            }
            
            // Try multiple scroll methods
            var scrolled = false
            
            // Method 1: Find scrollable node and use ACTION_SCROLL_FORWARD (preferred)
            val scrollableNode = findScrollableNode(root)
            if (scrollableNode != null) {
                android.util.Log.d("NowPlayingCapture", "Found scrollable node: ${scrollableNode.className}")
                scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                android.util.Log.d("NowPlayingCapture", "ACTION_SCROLL_FORWARD result: $scrolled")
                
                if (scrolled) {
                    lastScrollTime = System.currentTimeMillis()
                    return true
                }
            } else {
                android.util.Log.w("NowPlayingCapture", "No scrollable node found, trying gesture...")
            }
            
            // Method 2: Try gesture-based scrolling ONLY if standard scroll failed AND we have a clear target
            if (!scrolled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val recyclerView = findRecyclerView(root)
                if (recyclerView != null) {
                    val rect = android.graphics.Rect()
                    recyclerView.getBoundsInScreen(rect)
                    
                    // Make sure we have a reasonable area to scroll
                    if (rect.height() > 400 && rect.width() > 200) {
                        val path = android.graphics.Path()
                        // Start from lower in the list to avoid tabs at top
                        val startY = (rect.bottom - 300).toFloat()
                        val endY = (rect.top + 400).toFloat() // Don't go all the way to top to avoid tabs
                        
                        // Scroll in the center to avoid side buttons
                        path.moveTo(rect.centerX().toFloat(), startY)
                        path.lineTo(rect.centerX().toFloat(), endY)
                        
                        val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
                        val strokeDescription = android.accessibilityservice.GestureDescription.StrokeDescription(
                            path, 0, 250 // Slightly faster swipe
                        )
                        gestureBuilder.addStroke(strokeDescription)
                        
                        scrolled = dispatchGesture(
                            gestureBuilder.build(),
                            object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                                    super.onCompleted(gestureDescription)
                                    android.util.Log.d("NowPlayingCapture", "Gesture scroll completed")
                                }
                                
                                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                                    super.onCancelled(gestureDescription)
                                    android.util.Log.d("NowPlayingCapture", "Gesture scroll cancelled")
                                }
                            },
                            null
                        )
                        android.util.Log.d("NowPlayingCapture", "Scroll gesture dispatched: $scrolled (y: $startY -> $endY)")
                    } else {
                        android.util.Log.w("NowPlayingCapture", "RecyclerView too small for gesture: ${rect.height()}x${rect.width()}")
                    }
                }
            }
            
            return scrolled
            
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("NowPlayingCapture", "Scroll error: ${e.message}")
            return false
        }
    }
    
    private fun isOnHistoryPage(root: AccessibilityNodeInfo): Boolean {
        // Check if we can find history-specific elements (song_row)
        val hasSongRows = findNodesByResourceId(root, "com.google.android.as:id/song_row").isNotEmpty()
        
        // Also check we're not on favorites by looking for the favorites indicator
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        getAllNodes(root, nodes)
        
        val hasFavoritesIndicator = nodes.any { node ->
            node.text?.toString()?.contains("Favorites", ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains("Favorites", ignoreCase = true) == true
        }
        
        return hasSongRows && !hasFavoritesIndicator
    }
    
    private fun getAllNodes(node: AccessibilityNodeInfo, nodes: MutableList<AccessibilityNodeInfo>) {
        nodes.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                getAllNodes(child, nodes)
            }
        }
    }
    
    private fun extractTracksFromNode(node: AccessibilityNodeInfo): List<NowPlayingTrack> {
        val tracks = mutableListOf<NowPlayingTrack>()
        var currentDateGroup: String? = null
        
        try {
            // Look for date group headers and song rows in the UI hierarchy
            fun traverseForTracksAndDates(n: AccessibilityNodeInfo) {
                try {
                    // Check if this is a date group header (typically a TextView with date text)
                    val nodeText = n.text?.toString()
                    if (nodeText != null && isDateGroupHeader(nodeText)) {
                        currentDateGroup = nodeText
                        android.util.Log.d(TAG, "Found date group header: $currentDateGroup")
                    }
                    
                    // Check if this is a song row
                    if (n.viewIdResourceName == "com.google.android.as:id/song_row") {
                        val track = extractTrackFromSongRow(n, currentDateGroup)
                        if (track != null) {
                            tracks.add(track)
                        }
                    }
                    
                    // Traverse children
                    for (i in 0 until n.childCount) {
                        n.getChild(i)?.let { child ->
                            traverseForTracksAndDates(child)
                        }
                    }
                } catch (e: Exception) {
                    // Continue traversing even if one node fails
                }
            }
            
            traverseForTracksAndDates(node)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return tracks
    }
    
    private fun isDateGroupHeader(text: String): Boolean {
        // Check if text looks like a date group header
        // Examples: "Friday, October 24", "Wednesday, October 22", "Today", "Yesterday"
        val lowerText = text.lowercase()
        
        // Check for day names
        val dayNames = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        val hasDay = dayNames.any { lowerText.contains(it) }
        
        // Check for month names
        val monthNames = listOf("january", "february", "march", "april", "may", "june", 
                               "july", "august", "september", "october", "november", "december")
        val hasMonth = monthNames.any { lowerText.contains(it) }
        
        // Check for "Today" or "Yesterday"
        val isRelativeDay = lowerText == "today" || lowerText == "yesterday"
        
        return (hasDay && hasMonth) || isRelativeDay
    }
    
    private fun extractTrackFromSongRow(songRow: AccessibilityNodeInfo, dateGroupHeader: String?): NowPlayingTrack? {
        var title: String? = null
        var artist: String? = null
        var time: String? = null
        var favorited = false
        
        try {
            // Find title
            findNodesByResourceId(songRow, "com.google.android.as:id/song_row_title").firstOrNull()?.let {
                title = it.text?.toString()
            }
            
            // Find artist and time
            findNodesByResourceId(songRow, "com.google.android.as:id/song_artist_and_timestamp").firstOrNull()?.let {
                val artistAndTime = it.text?.toString()
                if (artistAndTime != null && "•" in artistAndTime) {
                    val parts = artistAndTime.split("•").map { p -> p.trim() }
                    artist = parts.getOrNull(0)
                    time = parts.getOrNull(1)
                } else {
                    artist = artistAndTime
                }
            }
            
            // Check if favorited
            findNodesByResourceId(songRow, "com.google.android.as:id/favorite_image").firstOrNull()?.let {
                favorited = it.contentDescription?.contains("Remove") == true
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return if (title != null) {
            // Use date group header if available, otherwise parse from timestamp
            val date = if (dateGroupHeader != null) {
                parseTimestampToDate(dateGroupHeader)
            } else {
                parseTimestampToDate(time)
            }
            android.util.Log.d(TAG, "Parsed track: $title | dateGroup='$dateGroupHeader' | time='$time' | date='$date'")
            NowPlayingTrack(
                title = title!!,
                artist = artist ?: "Unknown Artist",
                time = time,
                date = date,
                favorited = favorited
            )
        } else {
            null
        }
    }
    
    private fun parseTimestampToDate(timestamp: String?): String {
        if (timestamp == null) {
            android.util.Log.d(TAG, "No timestamp, using today")
            return java.time.LocalDate.now().toString()
        }
        
        android.util.Log.d(TAG, "Parsing timestamp: '$timestamp'")
        
        try {
            val now = java.time.LocalDate.now()
            
            // Check if it's just a time (e.g., "12:00", "3:45 PM") - means Today
            if (timestamp.contains(":")) {
                val hasOnlyTime = !timestamp.any { it.isLetter() || it == ',' } || 
                                 timestamp.matches(Regex(".*\\d+:\\d+\\s*[AP]M.*", RegexOption.IGNORE_CASE))
                if (hasOnlyTime || timestamp.length <= 8) {
                    android.util.Log.d(TAG, "Detected time-only format (Today): $timestamp")
                    return now.toString()
                }
            }
            
            // Handle "Today" or "Yesterday"
            when {
                timestamp.contains("Today", ignoreCase = true) -> {
                    android.util.Log.d(TAG, "Detected 'Today'")
                    return now.toString()
                }
                timestamp.contains("Yesterday", ignoreCase = true) -> {
                    android.util.Log.d(TAG, "Detected 'Yesterday'")
                    return now.minusDays(1).toString()
                }
            }
            
            // Parse formats like "Friday, October 24", "Mon, Jul 15" or "Jul 15"
            val months = mapOf(
                "Jan" to 1, "January" to 1,
                "Feb" to 2, "February" to 2,
                "Mar" to 3, "March" to 3,
                "Apr" to 4, "April" to 4,
                "May" to 5,
                "Jun" to 6, "June" to 6,
                "Jul" to 7, "July" to 7,
                "Aug" to 8, "August" to 8,
                "Sep" to 9, "September" to 9,
                "Oct" to 10, "October" to 10,
                "Nov" to 11, "November" to 11,
                "Dec" to 12, "December" to 12
            )
            
            // Extract month and day
            val parts = timestamp.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
            var month: Int? = null
            var day: Int? = null
            
            for (part in parts) {
                // Check if it's a month (abbreviated or full name)
                months[part]?.let { 
                    month = it
                    android.util.Log.d(TAG, "Found month: $part = $it")
                }
                // Check if it's a day number (but not part of a time like 12:00)
                if (!part.contains(":")) {
                    part.toIntOrNull()?.let { 
                        if (it <= 31) {  // Valid day number
                            day = it
                            android.util.Log.d(TAG, "Found day: $it")
                        }
                    }
                }
            }
            
            if (month != null && day != null) {
                val year = now.year
                var date = java.time.LocalDate.of(year, month!!, day!!)
                
                // If the date is in the future, it must be from last year
                if (date.isAfter(now)) {
                    date = date.minusYears(1)
                    android.util.Log.d(TAG, "Date was in future, adjusted to: $date")
                }
                
                android.util.Log.d(TAG, "Successfully parsed date: $date")
                return date.toString()
            } else {
                android.util.Log.w(TAG, "Could not parse month/day from: $timestamp")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing timestamp: $timestamp", e)
            e.printStackTrace()
        }
        
        android.util.Log.d(TAG, "Defaulting to today")
        return java.time.LocalDate.now().toString()
    }
    
    private fun findNodesByResourceId(node: AccessibilityNodeInfo, resourceId: String): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        
        try {
            if (node.viewIdResourceName == resourceId) {
                results.add(node)
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    results.addAll(findNodesByResourceId(child, resourceId))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return results
    }
    
    
    private fun findRecyclerView(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Look for RecyclerView or ScrollView
        if (node.className?.contains("RecyclerView") == true || 
            node.className?.contains("ScrollView") == true ||
            node.className?.contains("ListView") == true) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val result = findRecyclerView(child)
                if (result != null) return result
            }
        }
        
        return null
    }
    
    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Skip ViewPager (it's the tab switcher, not the song list!)
        if (node.className?.contains("ViewPager") == true) {
            android.util.Log.d("NowPlayingCapture", "Skipping ViewPager (tab switcher)")
            // Don't return this node, but still check its children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val result = findScrollableNode(child)
                    if (result != null) return result
                }
            }
            return null
        }
        
        // Prefer RecyclerView for scrolling
        if (node.isScrollable && node.isVisibleToUser) {
            val isRecyclerView = node.className?.contains("RecyclerView") == true
            val isListView = node.className?.contains("ListView") == true
            val isScrollView = node.className?.contains("ScrollView") == true
            
            if (isRecyclerView || isListView || isScrollView) {
                android.util.Log.d("NowPlayingCapture", "Found scrollable list: ${node.className}")
                return node
            } else {
                android.util.Log.d("NowPlayingCapture", "Skipping non-list scrollable: ${node.className}")
            }
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val result = findScrollableNode(child)
                if (result != null) return result
            }
        }
        
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        isServiceEnabled = false
        stopCapture()
    }
}

