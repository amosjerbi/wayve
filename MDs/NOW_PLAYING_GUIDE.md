# Now Playing Integration Guide

## Overview

The wayve app now includes a **Music** tab that allows you to import and browse your Google Pixel's "Now Playing" history. This feature lets you:

- 📚 Import JSON files from Now Playing captures
- 🎵 Browse your complete music history
- 🔍 Search and filter tracks by title or artist
- 📊 View analytics (top artists, top songs, statistics)
- ▶️ Play tracks directly on YouTube
- ❤️ See your favorited tracks

---

## Features

### 1. **Library Tab**
- View all imported tracks in a beautifully designed list
- Search by song title or artist name
- Sort by:
  - Time (most recent first)
  - Title (alphabetical)
  - Artist (alphabetical)
- Click any track to search and play on YouTube
- Continuous surface design matching wayve's Material 3 theme

### 2. **Analytics Tab**
- **Statistics Cards:**
  - Total tracks
  - Unique artists
  - Unique songs
  - Favorited tracks count
- **Top Artists:** See which artists appear most in your history
- **Top Songs:** See which songs you've heard the most
- **Timeline:** View date groups from your capture history

---

## How to Use

### Step 1: Capture Your Now Playing History

On your **computer** (with your Pixel phone connected via USB):

```bash
cd /Users/amosjerbi/Desktop/Now_Playing_Android/android-app/scripts

# Option 1: Complete capture with screenshots and metadata
./capture_complete_data.sh

# Option 2: Quick capture without screenshots
./capture_all_history.sh
```

This will:
- Open the Now Playing History app on your phone
- Automatically scroll through all pages
- Capture UI data for each page
- Parse and export to JSON format

**Output files:**
- `exported_data/nowplaying_complete_with_metadata.json` (recommended)
- `exported_data/nowplaying_complete_history.json` (alternative)

### Step 2: Transfer JSON to Your Phone

Transfer the JSON file to your phone using any method:
- Google Drive
- Email to yourself
- USB file transfer
- Any cloud storage service

Save it somewhere accessible (Downloads folder recommended).

### Step 3: Import in the wayve App

1. Open the **wayve** app
2. Navigate to the **Music** tab (4th tab at bottom)
3. Tap the **+** button in the top-right corner
4. Select the JSON file you transferred
5. Wait for the import to complete

✅ You'll see a success message with the number of tracks imported!

### Step 4: Browse and Play

**Library Tab:**
- Scroll through your tracks
- Use the search bar to find specific songs or artists
- Tap the filter icon to change sorting
- Tap any track or the play button to search on YouTube

**Analytics Tab:**
- Switch to the Analytics tab to see statistics
- Explore your top artists and songs
- Review the timeline of your music history

---

## Design Features

The Music tab follows wayve's established design patterns:

### Visual Consistency
- ✅ Material 3 color scheme (surfaceContainer/surfaceBright)
- ✅ Continuous surface pattern for track cards
- ✅ Rounded corners (28dp for lists, 20dp for modals)
- ✅ Empty state with illustration and helper text
- ✅ Consistent header spacing (48dp top, 24dp bottom, 20dp horizontal)

### User Experience
- ✅ Smooth animations and transitions
- ✅ Search with instant filtering
- ✅ Multiple sort options
- ✅ One-tap YouTube playback
- ✅ Analytics with visual stat cards
- ✅ Tab-based navigation

---

## Technical Details

### Data Models

The app uses Kotlin serialization to parse JSON data:

```kotlin
data class NowPlayingTrack(
    val title: String,
    val artist: String,
    val time: String?,
    val date: String?,
    val favorited: Boolean,
    val captured_on_page: String?
)

data class NowPlayingData(
    val exported: String,
    val source: String,
    val device: String,
    val tracks: List<NowPlayingTrack>,
    val statistics: Statistics?,
    val capture_info: CaptureInfo?
)
```

### YouTube Integration

When you tap a track:
1. App constructs a YouTube search query: `"{title} {artist}"`
2. Opens YouTube app (or browser) with the search results
3. You can then play the exact version you want

---

## File Structure

```
android-app/
├── app/src/main/java/com/romnix/app/
│   ├── data/
│   │   └── NowPlayingModels.kt       # Data models for tracks
│   └── ui/screens/
│       └── NowPlayingScreen.kt       # Main music screen UI
├── scripts/
│   ├── capture_complete_data.sh      # Capture script
│   └── exported_data/
│       └── nowplaying_complete_with_metadata.json
└── NOW_PLAYING_GUIDE.md             # This file
```

---

## Tips & Best Practices

### 📱 For Best Results:
1. **Capture regularly** - Run the capture script periodically to keep your history up to date
2. **Use complete capture** - The `capture_complete_data.sh` script includes metadata like favorite status and date groupings
3. **Keep originals** - Save your JSON files as backups
4. **Re-import anytime** - You can import new JSON files to refresh your library

### 🎵 Music Discovery:
- Use the **Analytics tab** to discover patterns in your listening
- **Top Artists** shows which musicians you hear most often
- **Search** helps you find that song you heard yesterday
- **Timeline** shows when you discovered different tracks

### ⚡ Performance:
- The app handles hundreds of tracks smoothly
- Search is instant with no lag
- Analytics are calculated on-the-fly
- No internet required (except for YouTube playback)

---

## Troubleshooting

### "Failed to parse JSON file"
- Make sure you're importing a valid Now Playing JSON export
- Check that the file isn't corrupted during transfer
- Try exporting again with the capture script

### "No tracks loaded"
- Verify the JSON file contains a `tracks` array
- Check that tracks have at least `title` and `artist` fields

### YouTube doesn't open
- Make sure YouTube app is installed
- Check that you have internet connection
- Try opening YouTube manually first

### Import button not working
- Grant file access permissions to wayve
- Make sure the file is in an accessible location (not a restricted folder)

---

## Future Enhancements (Optional)

Possible improvements for future versions:
- 🎸 Built-in audio player (using audio_server.py functionality)
- 📤 Export filtered/sorted lists to CSV
- 🎨 Custom themes for the music tab
- 🔄 Automatic capture via ADB directly from the app
- 📊 More detailed analytics and charts
- ⭐ Mark favorites within the app
- 📱 Share tracks with friends

---

## Credits

**Integration by:** AI Assistant  
**Based on:** Now Playing capture scripts and dashboard_player.html  
**App:** wayve - Music & ROM Manager  
**Device:** Google Pixel 8a (Android 16)  

---

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Verify your JSON file is valid
3. Review the capture script documentation in `scripts/README_COMPLETE.md`
4. Check app permissions for file access

---

**Enjoy exploring your music history! 🎵✨**

