# 🎵 Shazam API Setup Guide

Your app is now configured to use **Shazam API** for song recognition!

## Current Status ✅

- **Service**: Running and listening continuously
- **Detection Frequency**: Every 30 seconds
- **API**: Shazam via RapidAPI (configured with placeholder)
- **Features**: 
  - Detects songs from MacBook or any external audio source
  - Saves to Now Playing library automatically
  - Shows lock screen notifications
  - Tracks detection history

## Setup Required 🔧

### Step 1: Get Your FREE Shazam API Key (2 minutes)

1. **Visit RapidAPI Shazam**
   ```
   https://rapidapi.com/apidojo/api/shazam
   ```

2. **Sign Up** (free account)
   - Click "Sign Up" button
   - Use email or Google/GitHub

3. **Subscribe to FREE Plan**
   - Click "Subscribe to Test"
   - Select **"Basic" plan** (FREE)
   - **500 requests/month** at no cost

4. **Copy Your API Key**
   - You'll see "X-RapidAPI-Key: xxxxxxxxx"
   - Copy the key (looks like: `abc123xyz456...`)

### Step 2: Add API Key to App

Open this file:
```
app/src/main/java/com/romnix/app/service/ContinuousAudioMonitorService.kt
```

Find line **57** and replace:
```kotlin
private val SHAZAM_API_KEY = "YOUR_RAPIDAPI_KEY_HERE"
```

With your actual key:
```kotlin
private val SHAZAM_API_KEY = "abc123xyz456..." // Your actual key
```

### Step 3: Rebuild & Test

```bash
cd /Users/amosjerbi/Desktop/wayve_song
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testing 🎧

1. **Open the app** (service starts automatically)
2. **Play music** from your MacBook or any source
3. **Wait 30 seconds** for detection cycle
4. **Check logs**:
   ```bash
   adb logcat -s "ContinuousAudioMonitor:*"
   ```
5. **Look for**:
   - `🌐 Sending ... bytes to Shazam...`
   - `✅ Shazam response received`
   - `✅ Detected: Artist - Song`
   - `✅ Track added to library`

## Features 🎵

### Automatic Detection
- **Always-On**: Runs in background 24/7
- **Smart Recording**: 10-second samples every 30 seconds
- **Low Battery Usage**: Optimized audio settings (8kHz mono)
- **Wake Lock**: Stays active even when phone sleeps

### Library Integration
- **Auto-Save**: Detected songs automatically added to Now Playing library
- **Timestamps**: Each track tagged with time and date
- **No Duplicates**: Cooldown period prevents duplicate detections
- **Statistics**: Track count, unique artists, unique songs

### Notifications
- **Lock Screen**: Shows detected songs even when phone is locked
- **Persistent**: Ongoing notification shows detection count
- **Tap to Open**: Click notification to open app

## How It Works 🔧

```
1. Microphone → Records 10s audio sample
2. Audio Processing → Converts to format Shazam expects
3. Shazam API → Identifies song from audio fingerprint
4. Library Update → Saves track with timestamp
5. Notification → Shows on lock screen
6. Wait 30s → Repeat
```

## Troubleshooting 🔍

### "API key not configured" Error
- Check that you replaced `YOUR_RAPIDAPI_KEY_HERE` with your actual key
- Rebuild the app after making changes

### No Songs Detected
- Make sure music is playing audibly (not just headphones)
- Check phone isn't muted
- Verify microphone permission is granted
- Check logs for API errors

### "Limit Reached" Error
- Free plan: 500 requests/month
- Each detection = 1 request
- Upgrade to paid plan if needed ($0.004/request)

## API Limits 📊

**FREE Plan (Basic)**:
- ✅ 500 requests/month
- ✅ No credit card required
- ✅ Perfect for testing

**If You Need More**:
- Pro: $9.99/month = 10,000 requests
- Ultra: $49.99/month = 100,000 requests
- Mega: $149.99/month = 500,000 requests

## Next Steps 🚀

1. **Get your API key** from https://rapidapi.com/apidojo/api/shazam
2. **Add it to line 57** in `ContinuousAudioMonitorService.kt`
3. **Rebuild the app**
4. **Test with music playing**
5. **Check your Now Playing library** for detected songs!

---

**Questions?** Check the logs or open an issue!

