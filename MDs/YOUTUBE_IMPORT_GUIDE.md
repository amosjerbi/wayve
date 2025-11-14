# 🎥 YouTube Playlist Import Guide

## Overview

Wayve now supports importing songs from public YouTube playlists! Simply paste a YouTube playlist link and all videos will be added to your Wayve library.

---

## ✨ Features

- **Paste & Import**: Just paste any public YouTube playlist URL
- **Playlist Preview**: See playlist details before importing (title, channel, video count, thumbnail)
- **Automatic Import**: Import hundreds of videos with one tap
- **Complete Metadata**: Imports video title, channel name, and thumbnail
- **Progress Tracking**: See real-time import progress

---

## 📋 Prerequisites

### Step 1: Get YouTube API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Create a new project (if you don't have one)
3. Enable **YouTube Data API v3**:
   - Go to "Library"
   - Search for "YouTube Data API v3"
   - Click "Enable"
4. Create API Key:
   - Go to "Credentials"
   - Click "Create Credentials" → "API Key"
   - Copy the API key

**💡 Free tier**: 10,000 quota units/day (1 playlist import ≈ 1-50 units)

### Step 2: Configure in Wayve

1. Open **Wayve** app
2. Go to **Settings** tab
3. Scroll to **YouTube** section
4. Tap **"YouTube API Settings"**
5. Paste your API key
6. Tap **"SAVE"**

---

## 🚀 How to Import Playlists

### Step 1: Open Import Dialog

1. Open **Settings** tab
2. Scroll to **YouTube** section
3. Tap **"Import YouTube Playlist"**

### Step 2: Paste Playlist URL

Supported URL formats:
- Full URL: `https://youtube.com/playlist?list=PLxxxxxx`
- Mobile URL: `https://m.youtube.com/playlist?list=PLxxxxxx`
- Short URL: `https://youtu.be/video?list=PLxxxxxx`
- Playlist ID: `PLxxxxxx`

### Step 3: Preview Playlist

After pasting the URL, tap **"Continue"** to see:
- Playlist thumbnail
- Playlist name
- Channel name
- Video count
- Description

### Step 4: Import

1. Tap **"Import"** button
2. Wait while videos are loaded (progress shown)
3. Videos are automatically added to your library
4. Success message shows number of videos imported

---

## 📝 What Gets Imported

### Video Information
- **Title**: YouTube video title
- **Artist/Channel**: Channel name
- **Thumbnail**: Video thumbnail image
- **Timestamp**: Current date and time of import

### Import Details
- Videos are added to the **beginning** of your library
- Existing tracks are preserved
- Statistics automatically updated
- All data saved locally on your device

---

## 💡 Tips & Best Practices

### Supported Playlists
- ✅ Public playlists
- ✅ Unlisted playlists (with link)
- ❌ Private playlists (not accessible via API)

### Large Playlists
- Supports up to 1,000 videos per playlist
- Import shows real-time progress
- Takes a few seconds for large playlists

### API Quota
- Free tier: 10,000 units/day
- Typical playlist import: 1-50 units
- You can import 200+ playlists per day
- Quota resets daily at midnight Pacific Time

### Managing Imported Videos
- View in **Wayve library** screen
- Search by title or channel
- Sort by date to see recent imports
- Videos show channel name as "artist"

---

## 🎯 Use Cases

### Build Music Library Fast
- Import your favorite music playlists
- Add curated music collections
- Backup playlists for offline reference

### Track Video History
- Import "Watch Later" playlist
- Save favorite music video collections
- Keep local copy of playlists

### Cross-Platform Music Discovery
1. Discover playlists on YouTube
2. Import to Wayve for tracking
3. Use auto-detection to catch more songs

---

## ❓ Troubleshooting

### "Please configure YouTube API key first"
- Go to Settings → YouTube API Settings
- Enter your YouTube API key
- Make sure you enabled YouTube Data API v3

### "Invalid playlist URL or ID"
- Check the URL format
- Make sure it's a playlist link (contains `list=`)
- Try copying the URL again

### "Playlist not found or is private"
- Playlist might be private
- Check if the playlist exists
- Make sure the link is correct
- Try opening the playlist in YouTube first

### "Failed to load playlist"
- Check your internet connection
- Verify API key is correct
- Check if you've exceeded daily quota
- Try again in a few minutes

### No videos imported
- Playlist might be empty
- Videos might be private/deleted
- Check if playlist has restrictions

### API Quota Exceeded
- Wait until midnight Pacific Time for reset
- Check quota usage in [Google Cloud Console](https://console.cloud.google.com/apis/dashboard)
- Consider requesting quota increase (usually not needed)

---

## 🔒 Privacy & Data

- **Local Storage**: All imports saved only on your device
- **No Tracking**: Wayve doesn't track what you import
- **API Usage**: Only uses YouTube API to fetch playlist data
- **Your Control**: Delete data anytime from settings

---

## 🆚 Import Comparison

| Platform | What it Imports | Source |
|----------|----------------|---------|
| **YouTube** | Video titles, channels, thumbnails | Public playlists via URL |
| **Spotify** | Song titles, artists, albums, album art | Your Spotify playlists |
| **Manual** | JSON files | Google Pixel Now Playing |

---

## 📊 Technical Details

### API Endpoints Used
- `GET /youtube/v3/playlists` - Get playlist info
- `GET /youtube/v3/playlistItems` - Get playlist videos

### Data Flow
```
YouTube Playlist URL
    ↓ (extract playlist ID)
YouTube Data API v3
    ↓ (fetch playlist & videos)
YouTubePlaylistImporter
    ↓ (convert to NowPlayingTrack)
Wayve Library
    ↓ (save locally)
SharedPreferences
```

### Rate Limits
- API Quota: 10,000 units/day
- Playlist info: 1 unit
- Playlist items (50 videos): 1 unit
- No rate limiting within daily quota

### Video Limits
- Max 1,000 videos per import (20 API pages)
- YouTube API returns 50 videos per page
- Private/deleted videos are skipped

---

## 🎉 Happy Importing!

Now you can easily import your favorite YouTube music playlists to build your Wayve library!

**Pro Tip**: Import music mix playlists, album playlists, or your personal "Liked videos" playlist to quickly populate your library!

---

## 🔗 Quick Links

- [YouTube Data API Documentation](https://developers.google.com/youtube/v3/docs)
- [Google Cloud Console](https://console.cloud.google.com/)
- [API Quota Management](https://console.cloud.google.com/apis/dashboard)

