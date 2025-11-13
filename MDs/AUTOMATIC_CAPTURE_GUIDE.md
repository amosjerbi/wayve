# Automatic Now Playing Capture Guide

## Overview
wayve now features **automatic capture** that extracts your Now Playing music history directly from your device without needing a computer or ADB commands!

## How It Works
The app uses Android's **Accessibility Service** to:
1. Read the Now Playing History screen content
2. Automatically scroll through all pages
3. Extract track titles, artists, and timestamps
4. Save everything to a JSON file

All processing happens locally on your device. Your data stays private and secure.

## Setup Instructions

### 1. Enable Accessibility Service

**First Time Setup:**
1. Tap the **+** button in the Music tab
2. Select **"Capture from device"**
3. Choose where to save your data:
   - **Use default**: Saves to `Downloads/wayve` (recommended)
   - **Select location**: Choose a custom folder
4. A dialog will appear asking you to enable accessibility
5. Tap **"OPEN SETTINGS"**
6. Find **"wayve"** in the list
7. Toggle the switch to **ON**
8. Tap **"Allow"** when prompted
9. Return to wayve app

### 2. Start Automatic Capture

Once accessibility is enabled:
1. Tap the **+** button in Music tab
2. Select **"Capture from device"**
3. Choose save location (or use default)
4. The capture will start automatically!

**What happens next:**
- Now Playing History app opens
- wayve reads the screen and scrolls through pages
- Progress is shown with a loading indicator
- When complete, a success message appears
- Your music library is automatically populated

### 3. View Your Music

After capture completes:
- Switch to the **Library** tab
- Browse all your captured tracks
- Use search to find specific songs/artists
- Sort by Time, Title, or Artist
- Tap any track to play it on YouTube

## Features

### Library Tab
- **Search**: Find tracks by title or artist
- **Sort**: Time (newest first), Title (A-Z), Artist (A-Z)
- **Play**: Tap any track to play on YouTube
- **Track Count**: See total number of captured songs

### Analytics Tab
- **Statistics**: Total tracks, unique artists/songs
- **Top Artists**: Most played artists with play counts
- **Top Songs**: Most played tracks
- **Timeline**: Tracks organized by date

## Privacy & Permissions

### What wayve Can Access
- **Only the Now Playing app**: The accessibility service is configured to ONLY read from `com.google.android.as` (Google Now Playing)
- **No other apps**: wayve cannot see or interact with any other apps

### What wayve Does With Your Data
- **Stays on your device**: All capture and processing happens locally
- **No internet required**: (except for YouTube playback)
- **You control the files**: Save location is your choice
- **No tracking**: No analytics, no data collection

### Permissions Explained
- **Accessibility Service**: Required to read Now Playing screen content and scroll through pages
- **Storage**: To save JSON files with your music history
- **Internet**: Only used for YouTube search/playback

## Troubleshooting

### Capture Doesn't Start
1. Verify accessibility is enabled:
   - Settings → Accessibility → Downloaded apps → wayve (should be ON)
2. Make sure Now Playing app is installed
3. Try restarting the wayve app

### Capture Incomplete
- The service automatically scrolls through up to 50 pages
- If you have more history, it may not capture everything
- Try running capture again to get any missing tracks

### No Tracks Found
- Make sure you have Now Playing history on your device
- Open Now Playing manually to verify you have tracked songs
- Check that Now Playing is enabled in your device settings

### Accessibility Service Not Found
- Go to Settings → Accessibility
- Look for "Downloaded apps" or "Downloaded services"
- Find "wayve" and enable it
- If not visible, try reinstalling the app

## Alternative: Manual Capture (Advanced)

If you prefer or need to use the computer-based method:

1. Install the app on your device
2. Connect device to computer via ADB
3. Run the capture script on your computer:
   ```bash
   cd /Users/amosjerbi/Desktop/Now_Playing_Android/android-app
   ./scripts/capture_complete_data.sh
   ```
4. Transfer the generated JSON file to your device
5. In wayve app, tap **+** → **"Import JSON file"**
6. Select the transferred JSON file

## Tips

### Best Practices
- Run capture when you have good lighting (for potential screenshot features)
- Make sure your device isn't low on battery
- Close other apps to ensure smooth scrolling
- Let the capture complete without interruption

### Data Management
- Export your data regularly
- JSON files can be imported on any device with wayve
- Share JSON files with friends to compare music taste
- Files are human-readable if you open them in a text editor

### Performance
- First capture may take 2-5 minutes depending on history size
- Subsequent captures only get new tracks
- Each track takes ~1 second to process and scroll

## Future Features

Coming soon:
- **Screenshot capture**: Visual record of your history pages
- **Duplicate detection**: Automatically merge similar tracks
- **Export options**: CSV, Spotify playlists, Apple Music
- **Statistics**: More detailed analytics and insights
- **Backup & sync**: Cloud backup options

## Technical Details

### How It Works
1. **Service binding**: Accessibility service connects to Now Playing
2. **Node traversal**: Reads UI hierarchy to find song elements
3. **Data extraction**: Parses titles, artists, timestamps, favorite status
4. **Scroll automation**: Performs ACTION_SCROLL_FORWARD on scrollable nodes
5. **Duplicate detection**: Tracks by unique title+artist combination
6. **JSON generation**: Creates standardized data file with metadata
7. **File storage**: Saves to your selected location

### Resource IDs Used
- `com.google.android.as:id/song_row` - Song container
- `com.google.android.as:id/song_row_title` - Track title
- `com.google.android.as:id/song_artist_and_timestamp` - Artist • Time
- `com.google.android.as:id/favorite_image` - Favorite status

### Limitations
- Requires Now Playing app to be installed
- May not work if Now Playing UI changes significantly
- Maximum 50 scroll attempts per capture session
- Depends on device accessibility service availability

## Support

### Common Questions
**Q: Is this safe?**
A: Yes! The accessibility service only reads Now Playing and stores data locally.

**Q: Will this drain my battery?**
A: Minimal impact. The service only runs during active capture (~2-5 minutes).

**Q: Can I disable the accessibility service after?**
A: Yes, but you'll need to re-enable it for future captures.

**Q: Does this work on all Android versions?**
A: Tested on Android 8.0+. Requires Now Playing app from Google Pixel devices.

**Q: What if Now Playing updates?**
A: The app may need updates if Google changes the UI. Check for wayve updates.

---

**Version**: 2.0 (Automatic Capture)  
**Last Updated**: October 29, 2025  
**App**: wayve (formerly Romnix)

