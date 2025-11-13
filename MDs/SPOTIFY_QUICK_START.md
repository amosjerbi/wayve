# 🎵 Spotify Quick Start - 2 Minute Setup

## Step 1: Get Your Spotify Client ID (1 minute)

1. Open: **https://developer.spotify.com/dashboard**
2. Login with your Spotify account
3. Click green button: **"Create app"**
4. Fill in the form:
   ```
   App name: Wayve
   App description: Music library manager  
   Redirect URI: wayve://spotify-callback
   ```
5. Check the box: **"Android"**
6. Click **"Save"**
7. You'll see your app dashboard
8. **Copy the "Client ID"** (looks like: `1a2b3c4d5e6f...`)

## Step 2: Add Client ID to App (30 seconds)

1. Open file: `app/src/main/java/com/wayve/app/network/SpotifyAuthManager.kt`
2. Find line 22:
   ```kotlin
   private const val CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID"
   ```
3. Replace with your actual Client ID:
   ```kotlin
   private const val CLIENT_ID = "1a2b3c4d5e6f..."
   ```
4. Save file
5. Run: `./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Step 3: Use It! (30 seconds)

1. Open Wayve app
2. Go to **Settings** tab
3. Tap **"Connect to Spotify"**
4. Login with Spotify (one time only)
5. Tap **"Create Spotify Playlist"**
6. Wait while magic happens ✨
7. Playlist opens automatically!

## ✅ Done!

Now you have all your Wayve tracks in a Spotify playlist on your account!

---

## 💡 Pro Tips

- **One-time login**: Token is saved, you only need to login once
- **Private playlists**: All created playlists are private by default
- **Auto-search**: App automatically searches for each track on Spotify
- **Multiple playlists**: Each time you tap "Create", it makes a new playlist with today's date

## 🔒 Is This Safe?

Yes! The Client ID is designed to be public (it's not a secret). The Spotify OAuth flow ensures only you can access your account through the app.

---

**Need help?** Check `SPOTIFY_SETUP.md` for detailed troubleshooting!

