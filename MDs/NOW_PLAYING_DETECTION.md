# Now Playing Detection - Ambient Music Capture

## Overview

The **wayve** app now uses **Google Pixel's Now Playing** feature to automatically detect and save songs playing from external sources like your MacBook, speakers, TV, radio, etc.

## How It Works

### Detection Method: Now Playing Notifications (Pixel Only)

Your Pixel device has a built-in "Now Playing" feature that constantly listens for ambient music using Google's audio fingerprinting technology running on the Tensor chip. When it detects a song, it shows a notification.

**wayve captures these notifications automatically** and adds the detected songs to your library!

### Key Benefits

- ✅ **Zero battery impact** - Now Playing is already running on your Pixel
- ✅ **Instant detection** - Songs appear immediately when detected
- ✅ **External sources** - Detects music from MacBook, speakers, TV, radio, etc.
- ✅ **Works in background** - Captures songs even with screen off
- ✅ **High accuracy** - Uses Google's proprietary music database
- ✅ **Privacy-focused** - No microphone access required (uses existing Now Playing)
- ✅ **Free** - No API costs or external services

## Setup Instructions

### Step 1: Enable Now Playing on Your Pixel

1. Open **Settings** on your Pixel
2. Go to **Sound & vibration** → **Now Playing**
3. Toggle **Now Playing** ON
4. Enable **Show on lock screen** (recommended)

### Step 2: Enable Notification Access in wayve

1. Open the **wayve** app
2. Tap the **Music** tab (4th icon in bottom nav)
3. Tap the "Auto-detect OFF" chip
4. Tap **"OPEN SETTINGS"**
5. Find **"wayve"** in the Notification Access list
6. Toggle the switch to **ON**
7. Confirm the permission prompt

### Step 3: Enable Auto-Detection

1. Return to the **wayve** app
2. Tap the "Auto-detect OFF" chip again
3. It will now show **"Auto-detect ON"** with a green indicator

### Step 4: Test It!

1. Play music from your MacBook or any speaker
2. Wait for Now Playing to detect it (usually 5-10 seconds)
3. You'll see a Now Playing notification on your Pixel
4. The song will automatically be added to your wayve library!

## What Gets Detected

### External Audio Sources ✅
- 🎵 MacBook speakers playing music
- 📻 Radio stations
- 📺 TV shows and commercials
- 🔊 Bluetooth/WiFi speakers
- 🎤 Live performances
- 🏪 Music in stores, cafés, restaurants
- 🚗 Car radio

### NOT Detected ❌
- ❌ Music apps on your phone (Spotify, YouTube Music, etc.)
- ❌ Music playing through headphones connected to your phone

**Why?** Now Playing is designed to detect **ambient music** in your environment, not music playing directly on your device.

## User Experience

### Foreground Notification
When auto-detection is running, you'll see:
```
🎵 Wayve Now Playing Detection
Monitoring ambient music...
```

When a song is detected:
```
🎵 Detected!
Song Title - Artist Name
```

### Library Updates
- Songs appear at the top of your library (most recent first)
- Each track shows: Title, Artist, Time detected, Date
- Library statistics update automatically

## Use Cases

### Perfect For:
✅ **Working from home** - Capture songs playing from your MacBook while you work  
✅ **Discovery** - Save songs you hear in cafés, stores, or on the radio  
✅ **Music enthusiasts** - Build a log of all ambient music you encounter  
✅ **Background tracking** - Automatically save songs without manual effort  

### Not Ideal For:
❌ **Personal music apps** - Use manual import or third-party music tracking apps  
❌ **Non-Pixel devices** - This feature requires Google Pixel with Now Playing  

## Privacy & Security

### What wayve Can Access
✅ Now Playing notifications from `com.google.android.as`  
✅ Song title and artist from notifications  

### What wayve Cannot Access
❌ Other app notifications  
❌ Your messages or personal data  
❌ Microphone audio (wayve doesn't record anything)  

### How It Works
1. Google's Now Playing detects the song (on-device AI)
2. Now Playing shows a notification with song info
3. wayve reads that notification and saves it to your library
4. All data stays on your device

## Technical Details

### Notification Format
Now Playing notifications contain:
- **Title** (`android.title`) - Format: "Song Name by Artist Name"
- **Text** (`android.text`) - Generic text like "Tap to see your song history"
- **Package** - `com.google.android.as`

### Detection Logic
```kotlin
// Parse "Song by Artist" format
if (title.contains(" by ")) {
    val parts = title.split(" by ")
    songTitle = parts[0]
    songArtist = parts[1]
}
```

### Service Architecture
```
Now Playing (Pixel Tensor chip)
    ↓ Detects ambient music
    ↓ Shows notification
NowPlayingMonitorService (wayve)
    ↓ Captures notification
    ↓ Parses song info
    ↓ Adds to library
    ↓ Updates UI
```

## Troubleshooting

### No Songs Being Detected?

**Check Now Playing is Enabled:**
1. Settings → Sound & vibration → Now Playing
2. Make sure it's ON

**Check wayve Has Permission:**
1. Settings → Apps → Special app access → Notification access
2. Make sure **wayve** is enabled

**Check Auto-Detection Toggle:**
- Open wayve → Music tab
- Make sure chip shows "Auto-detect ON" with green indicator

**Test Now Playing Directly:**
1. Play music from your MacBook
2. Look for Now Playing notification on your Pixel
3. If Now Playing doesn't show it, wayve can't capture it either

### Songs Detected But Not Showing in wayve?

**Force Refresh:**
- Close and reopen the wayve app
- The library should update automatically

**Check Duplicates:**
- If a song was already in your library, it won't be added again
- This prevents duplicate entries

### Permission Denied?

**Reset Notification Access:**
1. Settings → Apps → wayve → Notifications
2. Turn OFF notification access
3. Turn ON again
4. Reopen wayve and enable auto-detection

## Comparison with Other Methods

| Method | Speed | Battery | Devices | Sources | Setup |
|--------|-------|---------|---------|---------|-------|
| **Now Playing** | Instant | 0% | Pixel only | External audio | None |
| MediaSession | Instant | 0% | All Android | Music apps | None |
| Microphone | ~15s | 5-10% | All Android | Everything | API keys |

**Now Playing is the best method for Pixel users who want to capture ambient music!**

## Device Compatibility

### ✅ Supported Devices
- Google Pixel 2 and newer
- Google Pixel 2 XL and newer
- All Pixel 3, 4, 5, 6, 7, 8 series
- Pixel Fold

### ❌ Not Supported
- Non-Pixel Android devices (Samsung, OnePlus, etc.)
- iPhones
- Devices without Now Playing feature

**Note:** Only Pixel devices have the Now Playing feature with Tensor chip audio fingerprinting.

## FAQ

**Q: Will this drain my battery?**  
A: No! Now Playing is already running on your Pixel. wayve just reads the notifications, which has zero battery impact.

**Q: Does wayve record audio?**  
A: No! wayve only reads Now Playing notifications. It never accesses your microphone.

**Q: Can I use this with Spotify on my phone?**  
A: No. Now Playing doesn't detect music playing through your phone's apps. Use manual import for that.

**Q: Why does it only work on Pixel?**  
A: Now Playing is a Pixel-exclusive feature that uses Google's Tensor chip for on-device audio fingerprinting.

**Q: What if Now Playing doesn't detect a song?**  
A: If Now Playing can't detect it, wayve can't capture it. Try increasing the volume or playing a clearer recording.

**Q: Can I disable this?**  
A: Yes! Just tap the "Auto-detect ON" chip in the Music tab to turn it OFF.

## Support

For issues or questions:
1. Check this guide
2. Verify Now Playing is working (test with direct notification)
3. Check app logs: `adb logcat -s "NowPlayingMonitor:*"`
4. Verify notification access permission

---

**Enjoy automatic ambient music tracking with wayve! 🎵**

Perfect for Pixel users who want to capture songs playing from their MacBook and other external sources!

