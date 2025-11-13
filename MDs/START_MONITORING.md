# How to Start Continuous Monitoring

## The Issue

The ADB command doesn't work because the service needs to be started from within the app, not externally.

## ✅ Simple Solution: Add This Code

### Option 1: Quick Test (Add to MainActivity)

Add this code to your `MainActivity.kt` (around line 140, in the `onCreate` or after setContent):

```kotlin
// Add this after setContent {
// Start continuous monitoring automatically
val intent = Intent(this, com.romnix.app.service.ContinuousAudioMonitorService::class.java)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    startForegroundService(intent)
} else {
    startService(intent)
}
Log.d("MainActivity", "Continuous monitoring started")
```

This will start the service automatically when you open the app.

### Option 2: Toggle in UI (Better)

I've already added the code to start/stop the service. You just need to add a button to toggle `continuousMonitorEnabled`.

Add this anywhere in your NowPlayingScreen UI (like after line 1000 where other buttons are):

```kotlin
// Add Continuous Monitor Toggle Button
Button(
    onClick = { continuousMonitorEnabled = !continuousMonitorEnabled },
    colors = ButtonDefaults.buttonColors(
        containerColor = if (continuousMonitorEnabled) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceVariant
    )
) {
    Icon(
        imageVector = if (continuousMonitorEnabled) Icons.Default.Stop else Icons.Default.PlayArrow,
        contentDescription = null
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(if (continuousMonitorEnabled) "Stop Always-On Detection" else "Start Always-On Detection")
}
```

## 🚀 Easiest Way Right Now

### Step 1: Add Your API Keys

Edit: `app/src/main/java/com/romnix/app/service/ContinuousAudioMonitorService.kt`

Lines 43-45:
```kotlin
private val ACR_HOST = "identify-eu-west-1.acrcloud.com"
private val ACR_ACCESS_KEY = "YOUR_ACTUAL_KEY"  // ← Replace this
private val ACR_ACCESS_SECRET = "YOUR_ACTUAL_SECRET"  // ← Replace this
```

### Step 2: Use Shared Preferences to Start

Run this ADB command to enable it:

```bash
adb shell "run-as com.romnix.app sh -c 'echo \"continuousMonitorEnabled:true\" > /data/data/com.romnix.app/shared_prefs/wayve_prefs.xml'"
```

Then restart the app - it will auto-start!

### Step 3: Or Use This Simpler Approach

Create a file: `app/src/main/java/com/romnix/app/MonitorStarter.kt`

```kotlin
package com.romnix.app

import android.content.Context
import android.content.Intent
import android.os.Build
import com.romnix.app.service.ContinuousAudioMonitorService

object MonitorStarter {
    fun startMonitoring(context: Context) {
        val intent = Intent(context, ContinuousAudioMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    fun stopMonitoring(context: Context) {
        val intent = Intent(context, ContinuousAudioMonitorService::class.java)
        context.stopService(intent)
    }
}
```

Then in your MainActivity onCreate:
```kotlin
// Start monitoring on app launch
MonitorStarter.startMonitoring(this)
```

## 🎯 Quick Test

After adding the code above:

1. **Rebuild:**
   ```bash
   cd /Users/amosjerbi/Desktop/wayve_song
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Grant permission:**
   ```bash
   adb shell pm grant com.romnix.app android.permission.RECORD_AUDIO
   ```

3. **Open the app** - service should start automatically

4. **Check it's running:**
   ```bash
   adb logcat -s "ContinuousAudioMonitor:*"
   ```

You should see:
```
🎵 Continuous Audio Monitor started
🎤 Continuous monitoring started - checking every 30s
```

5. **Play music** from your MacBook and wait 30 seconds!

## ⚠️ Important Reminders

1. **Must have ACRCloud API keys** - Service will fail silently without them
2. **Must grant microphone permission** - Can't record audio without it  
3. **Wait 30 seconds** between checks - Not instant detection
4. **Check logs** to see what's happening

## 🐛 If Still Not Working

**Check service is actually started:**
```bash
adb shell dumpsys activity services | grep -A 10 ContinuousAudio
```

**Force start via code injection:**
```bash
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
```

**Check for errors:**
```bash
adb logcat | grep -E "ContinuousAudio|Error"
```

---

**TL;DR:** Add `MonitorStarter.startMonitoring(this)` to your MainActivity's onCreate, rebuild, and it will start automatically!

