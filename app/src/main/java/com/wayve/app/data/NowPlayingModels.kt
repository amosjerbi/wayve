package com.wayve.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

/**
 * Data models for Now Playing music history
 */

@Serializable
data class NowPlayingTrack(
    val title: String,
    val artist: String = "Unknown Artist",
    val time: String? = null,
    val date: String? = null,
    val favorited: Boolean = false,
    val captured_on_page: String? = null,
    val albumArt: String? = null // URL to album cover image
)

@Serializable
data class CaptureInfo(
    val total_pages: Int = 0,
    val date_groups: List<String> = emptyList(),
    val visual_screenshots_available: Boolean = false
)

@Serializable
data class Statistics(
    val total_tracks: Int = 0,
    val unique_artists: Int = 0,
    val unique_songs: Int = 0,
    val favorited_count: Int = 0
)

@Serializable
data class NowPlayingData(
    val exported: String = "",
    val source: String = "",
    val device: String = "",
    val method: String = "",
    val capture_info: CaptureInfo? = null,
    val statistics: Statistics? = null,
    val tracks: List<NowPlayingTrack> = emptyList()
)

/**
 * Helper functions for parsing Now Playing JSON data
 */
object NowPlayingParser {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    
    fun parseJson(jsonString: String): NowPlayingData? {
        return try {
            json.decodeFromString<NowPlayingData>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun calculateStats(tracks: List<NowPlayingTrack>): Statistics {
        val uniqueArtists = tracks.map { it.artist }.toSet().size
        val uniqueSongs = tracks.map { "${it.title}|${it.artist}" }.toSet().size
        val favoritedCount = tracks.count { it.favorited }
        
        return Statistics(
            total_tracks = tracks.size,
            unique_artists = uniqueArtists,
            unique_songs = uniqueSongs,
            favorited_count = favoritedCount
        )
    }
}

