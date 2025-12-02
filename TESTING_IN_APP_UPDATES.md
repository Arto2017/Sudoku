# Testing In-App Updates

## ⚠️ Important Notes

**In-App Updates only work with apps published on Google Play Store!**
- It won't work with local debug builds
- You need to publish to at least Internal Testing track
- The update check compares version codes from Google Play

## 🧪 Testing Methods

### Method 1: Internal Testing Track (Recommended)

This is the easiest way to test:

#### Step 1: Build and Upload First Version
1. **Set version code to 4** (or current version) in `app/build.gradle`
2. Build release AAB:
   ```
   Build → Generate Signed Bundle/APK → Android App Bundle → release
   ```
3. Upload to Google Play Console → **Internal Testing** track
4. Add yourself as a tester
5. Install the app from Play Store (Internal Testing link)

#### Step 2: Create Update Version
1. **Increment version code to 5** in `app/build.gradle`:
   ```gradle
   versionCode 5
   versionName "1.1"
   ```
2. Build new release AAB with version 5
3. Upload to **Internal Testing** track (same track, higher version)
4. **Don't publish yet** - keep it as draft

#### Step 3: Test Update Flow
1. Open the app (version 4) on your device
2. The app should check for updates 2 seconds after opening
3. You should see the update prompt from Google Play
4. Test both:
   - **Flexible update**: Downloads in background, you can keep playing
   - **Immediate update**: Blocks app until update is installed

### Method 2: Internal App Sharing (Quick Testing)

For faster testing without publishing:

1. **Build version 4** (current):
   - Set `versionCode 4` in `build.gradle`
   - Build release AAB
   - Upload to **Internal App Sharing** in Play Console
   - Install on device from sharing link

2. **Build version 5** (update):
   - Set `versionCode 5` in `build.gradle`
   - Build release AAB
   - Upload to **Internal App Sharing** (same app, higher version)
   - Open version 4 app → should prompt for update

**Note**: Internal App Sharing may not always trigger in-app updates. Internal Testing is more reliable.

### Method 3: Staged Rollout (Production Testing)

If your app is already in production:

1. Keep current production version (e.g., version 4)
2. Upload new version (version 5) to **Production** track
3. Set rollout to **20%** (staged rollout)
4. Wait for Google Play to process (can take hours)
5. Open app on device → should prompt for update

## 🔍 How to Verify It's Working

### Check Logs
Look for these log messages in Logcat:
```
AppUpdateManager: Update available, starting flexible update
AppUpdateManager: Update downloaded, ready to install
```

### What You Should See
1. **2 seconds after app opens**: Update check happens (no UI, just background)
2. **If update available**: Google Play dialog appears asking to update
3. **Flexible update**: Download happens in background, you can keep playing
4. **After download**: App can prompt to restart (currently just logs)

## 🐛 Troubleshooting

### Update Not Showing?
- ✅ Make sure app is installed from Play Store (not debug build)
- ✅ Check version code is higher (5 > 4)
- ✅ Wait a few minutes after uploading (Google Play needs time to process)
- ✅ Make sure you're on the same Google account that uploaded the app
- ✅ Check Play Console → the new version should be "Available" (not just uploaded)

### Update Check Fails?
- Check Logcat for errors:
  ```
  AppUpdateManager: Failed to check for update
  ```
- Common issues:
  - App not installed from Play Store
  - No internet connection
  - Google Play Services not updated

### Testing on Emulator?
- In-app updates **may not work** on emulators
- Use a **real device** for reliable testing
- Make sure device has Google Play Store installed

## 📝 Quick Test Checklist

- [ ] Version 4 app installed from Play Store (Internal Testing)
- [ ] Version 5 AAB uploaded to Play Store (same track, higher version code)
- [ ] Version 5 status is "Available" in Play Console
- [ ] Open version 4 app
- [ ] Wait 2 seconds
- [ ] Check Logcat for update check logs
- [ ] Update dialog should appear (if update available)

## 🎯 Expected Behavior

**When update is available:**
- User sees Google Play update dialog
- Can choose "Update" or "Later"
- If "Update" → download starts (flexible) or app restarts (immediate)

**When no update:**
- Nothing happens (silent check)
- Logcat shows: "No update available"

---

**Note**: The update system is now integrated. When you release version 5, users with version 4 will be prompted to update automatically!
