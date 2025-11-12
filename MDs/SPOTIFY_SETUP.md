# 🎵 Spotify Integration - Super Simple Setup

## ✨ What You Get
- One-click login to Spotify
- Create playlists directly on your Spotify account
- All your Wayve tracks automatically added to a new playlist

## 📝 Setup (Takes 2 minutes!)

### Step 1: Create Spotify App
1. Go to: **https://developer.spotify.com/dashboard**
2. Click "Create app"
3. Fill in:
   - **App name**: `Wayve`
   - **App description**: `Music library manager`
   - **Redirect URI**: `wayve://spotify-callback`
   - **API/SDKs**: Check "Android"
4. Click "Save"
5. Copy your **Client ID** (looks like: `abc123def456...`)

### Step 2: Add Client ID to App
1. Open: `app/src/main/java/com/wayve/app/network/SpotifyAuthManager.kt`
2. Find line 22:
   ```kotlin
   private const val CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID"
   ```
3. Replace with your Client ID:
   ```kotlin
   private const val CLIENT_ID = "abc123def456..."  // Your actual Client ID
   ```
4. Save the file
5. Rebuild the app

### Step 3: Done! 🎉
- Open Wayve app
- Go to Settings
- Tap "Connect to Spotify"
- Login with your Spotify account (one time only)
- Tap "Create Spotify Playlist"
- Wait while it adds all your tracks
- Playlist opens automatically in Spotify!

## 🔒 Security Note
Your Client ID is safe to put in the app - it's designed to be public. The OAuth flow ensures only you can access your Spotify account.

## ❓ Troubleshooting

**"Spotify Client ID not configured"**
- You forgot to replace `YOUR_SPOTIFY_CLIENT_ID` in Step 2

**"Login failed"**
- Make sure the Redirect URI in Spotify Dashboard is exactly: `wayve://spotify-callback`
- Check you're using the correct Client ID

**"Some tracks not added"**
- This is normal - sometimes tracks can't be found on Spotify
- The app will add as many as it can find

## 🎵 How It Works
1. You login once (token saved securely)
2. App searches each track on Spotify
3. Creates a new private playlist on your account
4. Adds all found tracks to the playlist
5. Opens the playlist so you can listen immediately

**No computer scripts needed - everything happens in the app!**

