# Auto-Scroll Debugging Guide

## Issue: Automatic scrolling not working

The new APK includes **extensive debug logging** to diagnose why auto-scroll isn't working.

## Step 1: Watch the Logs in Real-Time

### On Mac/Linux:
```bash
# Clear previous logs
adb logcat -c

# Watch live logs (run this BEFORE starting capture)
adb logcat | grep NowPlayingCapture
```

### On Windows:
```cmd
adb logcat -c
adb logcat | findstr NowPlayingCapture
```

## Step 2: Start Capture in the App

1. Open wayve app
2. Tap **+** button
3. Select **"Capture from device"**
4. Choose save location
5. Watch the terminal for logs!

## Step 3: What Logs to Look For

### ✅ **Success Logs** (scrolling is working):
```
===== STARTING CAPTURE =====
Performing initial capture...
Captured: tracks=8, newThisPage=8
Starting auto-scroll loop...
AUTO-SCROLL LOOP STARTED
--- Scroll attempt 1 ---
Found scrollable node: androidx.recyclerview.widget.RecyclerView
ACTION_SCROLL_FORWARD result: true
✓ Scroll 1 executed successfully
✓ Scroll 1: +7 tracks (total: 15)
--- Scroll attempt 2 ---
✓ Scroll 2: +8 tracks (total: 23)
...
```

### ❌ **Problem Indicators**:

**Problem 1: Service not starting**
```
(no logs at all)
```
**Fix**: Check accessibility service is enabled

**Problem 2: Can't find scrollable view**
```
===== STARTING CAPTURE =====
AUTO-SCROLL LOOP STARTED
--- Scroll attempt 1 ---
No scrollable node found, trying gesture...
RecyclerView too small for gesture
✗ Scroll attempt FAILED
```
**Fix**: UI structure issue - may need alternative scroll method

**Problem 3: Not on history page**
```
⚠️ Not on history page! Stopping scroll.
```
**Fix**: App switched to favorites or wrong page

**Problem 4: Scroll command fails**
```
Found scrollable node: ...
ACTION_SCROLL_FORWARD result: false
```
**Fix**: Scroll action being blocked

## Step 4: Quick Checks

### A. Is Accessibility Service Enabled?
```bash
# Check if service is running
adb shell settings get secure enabled_accessibility_services
```

Should output: `.../com.noga.app/com.romnix.app.service.NowPlayingAccessibilityService`

If not enabled:
1. Settings → Accessibility
2. Find "wayve"
3. Toggle ON

### B. Is Now Playing App Open?
```bash
# Check current app
adb shell dumpsys window windows | grep -E 'mCurrentFocus'
```

Should show: `com.google.android.as` when capturing

### C. Can App Scroll Manually?
Try manually scrolling in Now Playing app. If you can't scroll, there's nothing for the app to scroll either!

## Step 5: Full Debug Log Export

If it's still not working, save the full log:

```bash
# Start logging to file
adb logcat -c
adb logcat > /Users/amosjerbi/Desktop/noga_debug.txt

# In another terminal/after capture:
# Stop with Ctrl+C
# Then search the file for issues
```

## Common Solutions

### Solution 1: Re-enable Accessibility
Sometimes the service needs to be toggled:
1. Settings → Accessibility → wayve → **OFF**
2. Wait 5 seconds
3. Settings → Accessibility → wayve → **ON**
4. Return to app and try again

### Solution 2: Force Stop & Restart
```bash
# Force stop both apps
adb shell am force-stop com.noga.app
adb shell am force-stop com.google.android.as

# Restart wayve
adb shell am start -n com.noga.app/.MainActivity
```

### Solution 3: Check Android Version
Auto-scroll requires:
- ✅ Android 7.0+ (API 24) for gesture scroll
- ✅ Android 5.0+ (API 21) for basic scroll

Check your version:
```bash
adb shell getprop ro.build.version.release
```

### Solution 4: Manual Scroll Test
While capture is running, **manually scroll** in Now Playing app. Check if:
- Logs show "Captured: tracks=X, newThisPage=Y"
- Track count increases in wayve app

If manual scroll works but auto doesn't, the issue is with scroll automation.

## Expected Log Flow

A complete successful capture should look like:

```
===== STARTING CAPTURE =====
Performing initial capture...
Captured: tracks=8, newThisPage=8
Starting auto-scroll loop...
AUTO-SCROLL LOOP STARTED
--- Scroll attempt 1 ---
Found scrollable node: androidx.recyclerview.widget.RecyclerView
ACTION_SCROLL_FORWARD result: true
✓ Scroll 1 executed successfully
✓ Scroll 1: +7 tracks (total: 15)
--- Scroll attempt 2 ---
Found scrollable node: androidx.recyclerview.widget.RecyclerView
ACTION_SCROLL_FORWARD result: true
✓ Scroll 2 executed successfully
✓ Scroll 2: +6 tracks (total: 21)
--- Scroll attempt 3 ---
... continues for many scrolls ...
--- Scroll attempt 45 ---
No new tracks after scroll (empty: 1/8, total tracks: 293)
--- Scroll attempt 46 ---
No new tracks after scroll (empty: 2/8, total tracks: 293)
... continues up to 8 empty scrolls ...
--- Scroll attempt 53 ---
No new tracks after scroll (empty: 8/8, total tracks: 293)
STOPPING: 8 consecutive empty scrolls. Final count: 293 tracks
```

## APK Location

Updated APK with debug logging:
```
/Users/amosjerbi/Desktop/Now_Playing_Android/android-app/app/build/outputs/apk/debug/app-debug.apk
```

## Next Steps

1. **Install the new APK**
2. **Run the logcat command** in terminal
3. **Start capture** in the app
4. **Watch the logs** and look for errors
5. **Share the logs** if you need help debugging

The logs will tell us **exactly** what's going wrong! 🔍

