# Quick Start Guide - AmbientMusicMod-Style Detection

## 🚀 Get Started in 5 Minutes

### Step 1: Get ACRCloud API Keys (2 minutes)

1. Visit: https://www.acrcloud.com
2. Click "Sign Up" → Create free account
3. Login → Create Project:
   - Name: "Wayve Music Detection"
   - Type: "Audio Recognition"
   - Audio Source: "Recorded"
4. Copy your credentials:
   - **Host:** `identify-eu-west-1.acrcloud.com`
   - **Access Key:** (copy this)
   - **Access Secret:** (copy this)

### Step 2: Add API Keys to App (1 minute)

Edit: `app/src/main/java/com/romnix/app/service/ContinuousAudioMonitorService.kt`

Find lines 43-45 and replace with your keys:

```kotlin
private val ACR_HOST = "identify-eu-west-1.acrcloud.com"  // ← Your host
private val ACR_ACCESS_KEY = "your_actual_key_here"       // ← Paste your key
private val ACR_ACCESS_SECRET = "your_actual_secret_here" // ← Paste your secret
```

### Step 3: Build and Install (1 minute)

```bash
cd /Users/amosjerbi/Desktop/wayve_song
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Grant Permissions (30 seconds)

1. Open wayve app
2. When prompted, grant:
   - ✅ Microphone access
   - ✅ Notification access

### Step 5: Start Monitoring (30 seconds)

**Option A: Via Code (Temporary)**

Add this somewhere in your app (like in NowPlayingScreen):

```kotlin
// Start continuous monitoring
val intent = Intent(context, ContinuousAudioMonitorService::class.java)
context.startForegroundService(intent)
```

**Option B: Via ADB (Quick Test)**

```bash
adb shell am start-foreground-service com.romnix.app/.service.ContinuousAudioMonitorService
```

### Step 6: Test It! 🎵

1. Play music from your **MacBook speakers**
2. Wait **30 seconds** for first check
3. Look for notification: **"🎵 Song Title"**
4. Check your wayve library - song should appear!

---

## 📱 You'll See:

### Foreground Notification:
```
🎵 Wayve - Always Listening
Monitoring ambient music...
0 songs detected
```

### Detection Notifications (Lock Screen):
```
🎵 Shape of You
by Ed Sheeran
```

---

## 🔧 Quick Troubleshooting

**Nothing happens?**
- Check you added correct API keys
- Make sure microphone permission granted
- Wait full 30 seconds between checks
- Play music louder

**Too many notifications?**
- Normal! Change `MONITORING_INTERVAL_MS` to `60000L` (1 minute)

**Battery draining?**
- Increase interval to 60 seconds
- Or stop service when not needed

---

## 🛑 How to Stop

**Via Code:**
```kotlin
val intent = Intent(context, ContinuousAudioMonitorService::class.java)
context.stopService(intent)
```

**Via ADB:**
```bash
adb shell am stop-service com.romnix.app/.service.ContinuousAudioMonitorService
```

---

## 📊 Monitor Logs

```bash
adb logcat -s "ContinuousAudioMonitor:*"
```

You'll see:
```
🎤 Recording audio sample...
🔍 Analyzing audio (80000 bytes)...
✅ Detected: Song Name by Artist Name
🎵 NEW TRACK: Song Name by Artist Name
```

---

## ⚙️ Configuration (Optional)

Edit `ContinuousAudioMonitorService.kt` to change:

```kotlin
// Check frequency
private val MONITORING_INTERVAL_MS = 30000L  // 30 seconds
// Change to: 60000L for 1 minute (better battery)

// Cooldown between duplicates
private val COOLDOWN_PERIOD_MS = 120000L  // 2 minutes
// Change to: 300000L for 5 minutes (fewer duplicate notifications)
```

---

## 🎉 That's It!

You now have **always-on music detection** like AmbientMusicMod!

**Works with:**
- MacBook speakers ✅
- TV ✅
- Radio ✅
- Any external audio source ✅

**Next:** Read `AMBIENTMUSICMOD_IMPLEMENTATION.md` for full details.

