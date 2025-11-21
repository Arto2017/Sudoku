# How to Test Your Real Banner Ads

## Quick Test Steps

### 1. **Build and Install on a Real Device**
   - ⚠️ **Important**: Ads will NOT work on emulators - you MUST test on a real Android device
   - Build your app: `./gradlew assembleDebug` or use Android Studio
   - Install on your device via USB or by copying the APK

### 2. **Check Logcat for Ad Status**

Open **Logcat** in Android Studio and filter by `AdManager`:

**Look for these success messages:**
```
✅ Banner ad loaded successfully! Ad Unit ID: ca-app-pub-2049534800625732/4495035971
✅ Banner ad impression recorded (ad was shown)
```

**If you see errors, check these common issues:**

#### Error Code 3 (ERROR_CODE_NO_FILL)
```
❌ Banner ad failed to load
   Error Code: 3
   Error Message: No fill
```
**This is NORMAL for new AdMob accounts!**
- Your account may not be fully approved yet
- AdMob needs time to start serving ads (can take 24-48 hours)
- Your app may need more users/impressions before ads fill
- **Solution**: Wait 1-2 days after account approval, or continue testing - this is expected

#### Error Code 1 (ERROR_CODE_INVALID_REQUEST)
```
❌ Banner ad failed to load
   Error Code: 1
   Error Message: Invalid request
```
**Check:**
- Is your Ad Unit ID correct? (should be: `ca-app-pub-2049534800625732/4495035971`)
- Is your App ID correct in AndroidManifest.xml?
- Did you add your app's package name to AdMob?

#### Error Code 2 (ERROR_CODE_NETWORK_ERROR)
```
❌ Banner ad failed to load
   Error Code: 2
   Error Message: Network error
```
**Check:**
- Is your device connected to the internet?
- Try switching between WiFi and mobile data

### 3. **Visual Check**

**Where to see banner ads:**
- **MainActivity** (game screen) - bottom of the screen
- **DailyChallengeActivity** - bottom of the screen

**What a working banner ad looks like:**
- A rectangular ad appears at the bottom of the screen
- Usually 320x50 pixels (standard banner size)
- Shows actual ad content (not a test ad)
- You can tap it to open the advertiser's website

### 4. **Test Ad vs Real Ad**

**Test Ad (if USE_TEST_ADS = true):**
- Shows "Test Ad" label
- Always loads successfully
- Format: `ca-app-pub-3940256099942544/...`

**Real Ad (USE_TEST_ADS = false):**
- Shows actual advertiser content
- May not always fill (especially for new accounts)
- Format: `ca-app-pub-2049534800625732/...`

## Step-by-Step Testing Guide

### Step 1: Enable USB Debugging
1. On your Android device: Settings → About Phone
2. Tap "Build Number" 7 times to enable Developer Options
3. Go to Settings → Developer Options
4. Enable "USB Debugging"

### Step 2: Connect Device and View Logs
1. Connect your device via USB
2. In Android Studio, open **Logcat** (bottom panel)
3. Filter by: `AdManager` or `Banner`
4. Run your app on the device

### Step 3: Navigate to Game Screen
1. Open your app
2. Start a game (MainActivity) or go to Daily Challenge
3. Look at the bottom of the screen for the banner ad
4. Check Logcat for ad loading messages

### Step 4: Interpret Results

**✅ SUCCESS - Ad Loaded:**
```
AdManager: Loading banner ad with ID: ca-app-pub-2049534800625732/4495035971 (Test mode: false)
AdManager: ✅ Banner ad loaded successfully! Ad Unit ID: ca-app-pub-2049534800625732/4495035971
AdManager: ✅ Banner ad impression recorded (ad was shown)
```
**Action**: Your ad is working! You should see the ad on screen.

**⚠️ NO FILL (Normal for New Accounts):**
```
AdManager: ❌ Banner ad failed to load
AdManager:    Error Code: 3
AdManager:    Error Message: No fill
```
**Action**: This is normal! Your account needs time. Wait 24-48 hours or continue testing.

**❌ ERROR - Check Configuration:**
```
AdManager: ❌ Banner ad failed to load
AdManager:    Error Code: 1
AdManager:    Error Message: Invalid request
```
**Action**: Check your Ad Unit ID and App ID are correct.

## Troubleshooting

### Ad Not Showing?

1. **Check Logcat** - Look for error messages
2. **Verify Ad Unit ID** - Should be `ca-app-pub-2049534800625732/4495035971`
3. **Check App ID** - Should be `ca-app-pub-2049534800625732~1014670551` in AndroidManifest.xml
4. **Verify Package Name** - Make sure your app's package name matches what's registered in AdMob
5. **Check AdMob Dashboard** - Go to AdMob → Your App → Check if ad unit is active

### Still Not Working?

1. **Wait 24-48 hours** - New AdMob accounts need approval time
2. **Check AdMob Account Status** - Make sure your account is approved
3. **Verify Internet Connection** - Ads need internet to load
4. **Check AdMob Policies** - Make sure your app complies with AdMob policies

## Quick Verification Checklist

- [ ] App installed on real Android device (not emulator)
- [ ] Device connected to internet
- [ ] Logcat shows "Loading banner ad" message
- [ ] Ad Unit ID in logs matches: `ca-app-pub-2049534800625732/4495035971`
- [ ] App ID in AndroidManifest.xml is: `ca-app-pub-2049534800625732~1014670551`
- [ ] Navigated to game screen (MainActivity or DailyChallengeActivity)
- [ ] Checked bottom of screen for banner ad
- [ ] Checked Logcat for success/error messages

## Expected Behavior

**First 24-48 hours after AdMob setup:**
- You may see "No fill" errors (Error Code 3)
- This is **NORMAL** - AdMob needs time to start serving ads
- Your account needs to be fully approved

**After account is approved:**
- Ads should start loading successfully
- You'll see actual ad content
- Logcat will show "Banner ad loaded successfully"

## Need More Help?

- **AdMob Help**: https://support.google.com/admob
- **Check AdMob Dashboard**: https://admob.google.com → Your App → Ad units
- **AdMob Status**: Check if your account shows "Ready" status

Good luck! 🚀

