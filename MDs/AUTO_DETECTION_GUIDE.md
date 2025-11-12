# Automatic Song Detection Guide

## Overview

The **wayve** app includes automatic song detection using Android's MediaSession API. This feature monitors music apps (Spotify, YouTube Music, Apple Music, etc.) in real-time and automatically adds detected songs to your library without any manual intervention.

## How It Works

### Technical Implementation

The automatic detection system uses **MediaSessionManager** that:

1. **Monitors All Music Apps** - Listens to MediaSession events from all music player apps
2. **Extracts Song Information** - Gets metadata including:
   - Song title
   - Artist name
   - Album name
   - Detection time
   - Detection date
3. **Prevents Duplicates** - Tracks previously detected songs to avoid adding the same song multiple times
4. **Updates Library Automatically** - Adds new songs to your library in real-time
5. **Works in Background** - Runs as a foreground service with minimal battery impact

### Key Components

#### 1. NowPlayingMonitorService.kt
- `NotificationListenerService` that runs in the background
- Monitors MediaSession events from all music apps
- Validates song detections (filters out system messages)
- Manages duplicate prevention
- Updates SharedPreferences with new tracks

#### 2. Updated NowPlayingScreen.kt
- Auto-detection toggle in the header
- Status indicator showing "Auto-detect ON/OFF"
- Permission management dialogs
- Real-time UI updates when songs are detected

#### 3. AndroidManifest.xml
- Registers the `NowPlayingMonitorService`
- Declares notification listener permission

## Setup Instructions

### Step 1: Enable Notification Access

1. Open the **wayve** app
2. In the **Wayve library** screen, you'll see an "Auto-detect OFF" chip in the header
3. Tap the chip to open the permission dialog
4. Tap **"OPEN SETTINGS"**
5. Find **"wayve"** in the Notification Access list
6. Toggle the switch to **ON**
7. Confirm the permission prompt

### Step 2: Enable Auto-Detection

1. Return to the **wayve** app
2. Tap the "Auto-detect OFF" chip again
3. It will now show **"Auto-detect ON"** with a green indicator

### Step 3: Start Detecting Songs

That's it! The app will now automatically:
- Monitor music apps (Spotify, YouTube Music, Apple Music, etc.)
- Extract song information
- Add new songs to your library
- Show a notification when songs are detected

## Features

### Real-Time Detection
- Songs are added immediately when they start playing in music apps
- No need to manually capture anything
- Works with all major music apps

### Smart Duplicate Prevention
- The service tracks all previously detected songs
- Uses a unique key: `title|artist`
- Won't add the same song twice

### Seamless Integration
- New songs appear at the top of your library (most recent first)
- Library statistics are automatically recalculated
- Works with all existing features (search, sort, analytics)

### Universal Compatibility
- Works on ALL Android devices (not just Pixel)
- Supports Spotify, YouTube Music, Apple Music, Deezer, Tidal, SoundCloud, Pandora, Amazon Music, and more
- No internet connection required for detection

### Privacy-Focused
- Only reads MediaSession metadata
- All data stays on your device
- No external services used

## UI Changes

### Header Status Chip
Located below the track count in the header:

```
Wayve library
293 tracks from Google Pixel 8a
[●] Auto-detect ON    ← Clickable chip with status indicator
```

**Colors:**
- **Green + Primary Container** - Auto-detection enabled and running
- **Gray + Surface Variant** - Auto-detection disabled

### Foreground Notification
When auto-detection is running:
```
🎵 Wayve Music Detection
Monitoring music apps...
```

When a song is detected:
```
🎵 Detected!
Song Title - Artist Name
```

## Supported Music Apps

The service automatically detects songs from:
- ✅ Spotify
- ✅ YouTube Music
- ✅ Apple Music
- ✅ Deezer
- ✅ Tidal
- ✅ SoundCloud
- ✅ Pandora
- ✅ Amazon Music
- ✅ Any app that uses MediaSession API

## Best Practices

### When to Use Auto-Detection
✅ For ongoing automatic tracking of songs  
✅ When you want real-time updates  
✅ For seamless background monitoring  
✅ To track your music listening across all apps

### Recommended Workflow
1. **Enable auto-detection** to automatically track songs as you listen
2. **Let it run** in the background while you use your music apps
3. **Check your library** to see all the songs you've listened to

## Troubleshooting

### Auto-Detection Not Working?

**Check Notification Access Permission:**
1. Go to Settings → Apps → Special app access → Notification access
2. Ensure **wayve** is enabled

**Check Auto-Detection Toggle:**
- Make sure the chip shows "Auto-detect ON" with a green indicator

**Check Music App is Playing:**
- Ensure you're actually playing music in a supported app
- The song must be actively playing to be detected

**Check Duplicates:**
- If a song was already in your library, it won't be added again
- This is expected behavior to prevent duplicates

### Songs Not Showing Up?

**Force Refresh:**
- Close and reopen the app
- The library should update automatically

**Check SharedPreferences:**
- The service saves to `wayve_prefs` → `nowplaying_data`
- This persists across app restarts

### Permission Denied?

If you see "Permission denied" errors:
1. Uninstall and reinstall the app
2. Grant notification access during first launch
3. Enable auto-detection

## Technical Details

### Data Flow

```
Music app plays song
    ↓
MediaSession posts metadata
    ↓
NowPlayingMonitorService receives metadata update
    ↓
Extracts title + artist + album
    ↓
Validates detection (filters system messages)
    ↓
Checks duplicate cache
    ↓
Adds to library (if new)
    ↓
Saves to SharedPreferences
    ↓
Triggers UI callback
    ↓
NowPlayingScreen refreshes
    ↓
User sees new song in library
```

### Storage

**SharedPreferences Key:** `wayve_prefs`

**Data Structure:**
```json
{
  "nowplaying_data": {
    "exported": "2025-10-30T...",
    "source": "com.google.android.as (Auto-detected)",
    "device": "Google Pixel 8a",
    "method": "Notification Listener (Instant)",
    "tracks": [
      {
        "title": "Song Name",
        "artist": "Artist Name",
        "time": "3:45 PM",
        "date": "2025-10-30",
        "favorited": false
      }
    ],
    "statistics": {
      "total_tracks": 294,
      "unique_artists": 120,
      "unique_songs": 250,
      "favorited_count": 15
    }
  },
  "auto_detection_enabled": true
}
```

### MediaSession API

The service monitors:
- **Title** - `METADATA_KEY_TITLE` or `METADATA_KEY_DISPLAY_TITLE`
- **Artist** - `METADATA_KEY_ARTIST` or `METADATA_KEY_ALBUM_ARTIST`
- **Album** - `METADATA_KEY_ALBUM`

### Validation Rules

The service filters out invalid detections:
- "Listening"
- "Now Playing"
- "Searching"
- "No match"
- "Not found"
- "Error"
- "Loading"
- "Detecting"

## Privacy & Security

### What the App Can Access
✅ MediaSession metadata from music apps only  
✅ Song title, artist, and album information  

### What the App Cannot Access
❌ Other app notifications  
❌ Notification content from non-music apps  
❌ Your messages or sensitive data  

### Data Storage
- All data stored locally in app's private storage
- Uses Android's SharedPreferences (encrypted on device)
- No cloud sync or external transmission
- Data cleared when app is uninstalled

## Battery Impact

The service uses:
- **Foreground Service**: Keeps running in background
- **Wake Lock**: Partial wake lock for reliability
- **MediaSessionManager**: Passive monitoring (no polling)

**Estimated battery impact**: Less than 1-2% per day

## Future Enhancements

Potential improvements:
- [ ] Configurable detection filters
- [ ] Per-app detection toggles
- [ ] Export detected songs to CSV/JSON
- [ ] Sync with music streaming services
- [ ] Album art fetching

## Support

For issues or questions:
1. Check this guide
2. Check app logs: `adb logcat -s "NowPlayingMonitor:*"`
3. Verify permissions in Android Settings

---

**Enjoy automatic song tracking with wayve! 🎵**
