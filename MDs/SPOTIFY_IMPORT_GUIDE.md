# 🎵 Spotify Playlist Import Guide

## Overview

Wayve now supports importing songs directly from your Spotify playlists! This feature allows you to quickly populate your Wayve library with tracks from any of your Spotify playlists.

---

## ✨ Features

- **View All Your Playlists**: Browse all playlists from your Spotify account
- **Visual Playlist Browser**: See playlist covers, names, track counts, and owners
- **One-Tap Import**: Import entire playlists with a single tap
- **Automatic Merging**: Imported tracks are added to your existing Wayve library
- **Complete Metadata**: Imports include song title, artist, album, and album art

---

## 📋 Prerequisites

Before you can import playlists, you need to:

1. **Configure Spotify API Settings**
   - Go to Settings → Spotify API Settings
   - Enter your Spotify Client ID and Client Secret
   - Save the settings

2. **Connect to Spotify**
   - Go to Settings → Connect to Spotify
   - Login with your Spotify account
   - Grant permissions (you'll need to reconnect if you haven't already, as we've added playlist reading permissions)

---

## 🚀 How to Import Playlists

### Step 1: Open Import Dialog

1. Open the **Wayve** app
2. Go to the **Settings** tab (bottom navigation)
3. Scroll to the **Spotify** section
4. Tap **"Import from Spotify"**

### Step 2: Browse Your Playlists

- The app will load all your Spotify playlists
- You'll see:
  - Playlist cover image
  - Playlist name
  - Number of tracks
  - Playlist owner

### Step 3: Select a Playlist

- Tap on any playlist you want to import
- You'll see a confirmation screen with:
  - Large playlist cover
  - Playlist details
  - Track count

### Step 4: Import

1. Tap the **"Import"** button
2. The app will:
   - Load all tracks from the playlist
   - Convert them to Wayve format
   - Add them to your library
3. You'll see a success message with the number of tracks imported

---

## 📝 What Happens During Import

### Data Imported
- **Song Title**: The track name
- **Artist**: All artists on the track
- **Album**: Album name
- **Album Art**: High-quality cover image
- **Timestamp**: Current date and time of import

### Data Organization
- Imported tracks are added to the **beginning** of your library
- Existing tracks are preserved
- Statistics are automatically updated
- All data is saved to your device

---

## 💡 Tips & Best Practices

### Importing Multiple Playlists
- You can import as many playlists as you want
- Import one at a time for better control
- The app handles duplicates - each import adds tracks even if they already exist

### Managing Imported Tracks
- View imported tracks in the **Wayve library** screen
- Use search to find specific tracks
- Sort by date to see recently imported tracks
- Favorite tracks to keep them organized

### Performance
- Large playlists (100+ tracks) may take a few seconds to import
- The app shows progress during import
- Internet connection required for importing

---

## 🔄 Permission Requirements

The Spotify import feature requires these permissions:

- ✅ `playlist-read-private` - Read your private playlists
- ✅ `playlist-read-collaborative` - Read collaborative playlists
- ✅ `playlist-modify-public` - Create public playlists (for export)
- ✅ `playlist-modify-private` - Create private playlists (for export)

**Important**: If you were already connected to Spotify before this feature was added, you'll need to:
1. Disconnect from Spotify (Settings → Disconnect Spotify)
2. Reconnect (Settings → Connect to Spotify)
3. This grants the new playlist reading permissions

---

## ❓ Troubleshooting

### "Please connect to Spotify first"
- Go to Settings → Connect to Spotify
- Make sure you complete the login process
- Return to the app after authorization

### "No playlists found"
- Make sure you have playlists in your Spotify account
- Check that you granted permissions during login
- Try disconnecting and reconnecting

### "Failed to load playlists"
- Check your internet connection
- Verify your Spotify credentials are correct
- Try disconnecting and reconnecting to Spotify

### Import shows "No tracks found"
- The playlist might be empty
- Tracks might be unavailable in your region
- Try another playlist

---

## 🎯 Use Cases

### Build Your Library Fast
- Import your favorite playlists to quickly populate Wayve
- Great for new users getting started

### Backup Spotify Playlists
- Import playlists to have a local copy in Wayve
- Keep track of songs even if playlist changes

### Discover & Export Workflow
1. Import Spotify playlists to Wayve
2. Use Wayve's detection features to add more tracks
3. Export back to Spotify as a new playlist

### Cross-Reference Music
- Compare your Now Playing history with playlist tracks
- Find tracks you've heard but haven't added to playlists

---

## 🔐 Privacy & Data

- **All imports are local**: Tracks are saved only on your device
- **No cloud storage**: Your Wayve library stays private
- **Spotify permissions**: Only used to read playlist data
- **Data format**: Standard JSON format, compatible with export features

---

## 🆚 Import vs Export

| Feature | Import from Spotify | Create Spotify Playlist |
|---------|-------------------|------------------------|
| **Direction** | Spotify → Wayve | Wayve → Spotify |
| **Source** | Your Spotify playlists | Your Wayve library |
| **Purpose** | Populate Wayve library | Share on Spotify |
| **Result** | Local tracks in Wayve | New playlist on Spotify |
| **Use Case** | Quick library setup | Backup/share library |

---

## 📊 Technical Details

### API Endpoints Used
- `GET /v1/me/playlists` - List user playlists
- `GET /v1/playlists/{id}/tracks` - Get playlist tracks

### Data Flow
```
Spotify API
    ↓ (fetch playlists)
SpotifyPlaylistManager
    ↓ (fetch tracks)
SpotifyImportDialog
    ↓ (convert format)
NowPlayingData
    ↓ (save)
SharedPreferences (Local Storage)
```

### Rate Limits
- Spotify API: 180 requests per minute
- Wayve handles pagination automatically
- Large playlists are processed in batches

---

## 🎉 Happy Importing!

Enjoy building your Wayve library with your favorite Spotify playlists! If you have any questions or issues, check the troubleshooting section above.

**Pro Tip**: Import your "Liked Songs" or "Favorites" playlist first to get all your favorite tracks in one go!

