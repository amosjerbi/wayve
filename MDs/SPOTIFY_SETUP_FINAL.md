# 🎵 Spotify Integration - Complete Guide

## ✨ What You Get
- **Zero code editing** - Configure everything in the app!
- One-tap login to Spotify
- Create real playlists on YOUR Spotify account
- All your Wayve tracks automatically added

---

## 📱 Super Simple Setup (2 minutes)

### Step 1: Get Your Spotify Credentials (1 minute)

1. Open: **https://developer.spotify.com/dashboard**
2. Login with your Spotify account
3. Click the green **"Create app"** button
4. Fill in the form:
   ```
   App name: Wayve
   App description: Music library manager
   Redirect URI: wayve://spotify-callback
   ```
5. ✅ Check the box: **"Android"**
6. Click **"Save"**
7. You'll see your app dashboard:
   - **Copy the "Client ID"** (looks like: `1a2b3c4d5e6f...`)
   - Click **"View client secret"** and **copy the "Client Secret"** (looks like: `9z8y7x6w5v...`)

### Step 2: Configure in Wayve App (30 seconds)

1. Open **Wayve** app
2. Go to **Settings** tab (bottom right)
3. Tap **"Spotify API Settings"**
4. Paste your **Client ID**
5. Paste your **Client Secret**
6. Tap **"SAVE"**

### Step 3: Connect & Create! (30 seconds)

1. Still in Settings, tap **"Connect to Spotify"**
2. Login with your Spotify account (one time only)
3. ✅ You'll see **"✓ Connected to Spotify"**
4. Tap **"Create Spotify Playlist"**
5. Watch the progress: "Adding 5/50..."
6. 🎉 Playlist opens automatically in Spotify!

---

## 💡 How It Works

**The Flow:**
1. **Spotify API Settings** → Save your Client ID and Client Secret (from developer dashboard)
2. **Connect to Spotify** → One-time OAuth login (token saved securely)
3. **Create Spotify Playlist** → App searches each track & creates playlist

**What Happens When You Create:**
- Creates a new **private playlist** on YOUR account
- Name format: "Wayve Library - Nov 02, 2025"
- Searches Spotify for each track (by title + artist)
- Adds all found tracks to the playlist
- Shows progress in real-time
- Opens the playlist when done

**Token Management:**
- Token is saved securely on your device
- Login once, works for ~1 hour sessions
- App refreshes token automatically
- Tap "✓ Connected to Spotify" to disconnect

---

## 🔒 Security & Privacy

**Is This Safe?**
- ✅ Yes! Client ID is designed to be public (not a secret)
- ✅ OAuth ensures only YOU can access your account
- ✅ Token is stored locally on your device only
- ✅ Playlists are private by default

**What Permissions Does Wayve Need?**
- `playlist-modify-public` - Create public playlists (if you want)
- `playlist-modify-private` - Create private playlists

---

## ❓ Troubleshooting

### "Please configure Spotify API Settings first"
**Solution:** You forgot Step 2! Go to Settings → Spotify API Settings and add your Client ID.

### "Spotify login failed"
**Possible causes:**
1. **Wrong Redirect URI** - Make sure it's exactly: `wayve://spotify-callback` in Spotify Dashboard
2. **Wrong Client ID** - Double-check you copied it correctly
3. **Network issue** - Check your internet connection

### "Some tracks not added to playlist"
**This is normal!** Sometimes tracks can't be found on Spotify because:
- Different spelling or artist name
- Not available in your region
- Exclusive to other platforms

The app will add as many as it can find.

### How to reset?
1. Tap **"✓ Connected to Spotify"** to disconnect
2. Go to **Spotify API Settings** and clear the Client ID
3. Start fresh from Step 1

---

## 🎵 Pro Tips

**Multiple Playlists:**
- Each time you tap "Create", it makes a NEW playlist
- Old playlists stay on your account
- Organize your library by date/mood/genre!

**Best Results:**
- Make sure track titles and artists are correct in your library
- The better the metadata, the better the search results
- You can manually fix any missed tracks in Spotify later

**Speed:**
- Creating a 50-track playlist takes ~30 seconds
- 100 tracks ~1 minute
- 500 tracks ~5 minutes

---

## 📚 Comparison with Old Method

**Old Way (Computer Script):**
- ❌ Need to export JSON manually
- ❌ Run Python script on computer
- ❌ Install dependencies (yt-dlp, etc)
- ❌ Configure OAuth on computer
- ❌ Multiple steps

**New Way (In-App):**
- ✅ Everything in the app
- ✅ One-tap playlist creation
- ✅ No computer needed
- ✅ Configure once, use forever
- ✅ Two taps: Connect → Create

---

## 🚀 Next Steps

Once you've created your first playlist:
1. Open Spotify and check it out!
2. Rename the playlist if you want
3. Make it public to share with friends
4. Create more playlists for different moods
5. Enjoy your music! 🎶

---

**Need more help?** The app is designed to be self-explanatory - just follow the Settings screen prompts! Everything you need is right there.

