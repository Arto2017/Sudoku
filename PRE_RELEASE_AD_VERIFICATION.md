# Pre-Release Ad Verification Checklist

## ✅ Verify All Ads Are Real (Not Test Ads) Before Publishing to Play Console

This checklist helps you verify that your app uses **REAL AdMob ad unit IDs** for production, not test ads.

---

## 🔍 **Step 1: Verify Build Configuration**

### Check `app/build.gradle`:

✅ **Release Build Configuration:**
```gradle
release {
    buildConfigField "boolean", "USE_TEST_ADS", "false"  // ✅ Must be FALSE
}
```

✅ **Debug Build Configuration:**
```gradle
debug {
    buildConfigField "boolean", "USE_TEST_ADS", "true"   // ✅ Can be TRUE (for testing)
}
```

**Status:** ✅ VERIFIED - Release builds use `USE_TEST_ADS = false`

---

## 🔍 **Step 2: Verify Ad Unit IDs**

### Test Ad Unit IDs (Google's Official Test IDs - Should NOT be used in release):
- ❌ `ca-app-pub-3940256099942544/6300978111` (Test Banner)
- ❌ `ca-app-pub-3940256099942544/1033173712` (Test Interstitial)
- ❌ `ca-app-pub-3940256099942544/5224354917` (Test Rewarded)

### Your Real Ad Unit IDs (Should be used in release):
- ✅ **Banner:** `ca-app-pub-2049534800625732/8506433174`
- ✅ **Interstitial:** `ca-app-pub-2049534800625732/8282964095`
- ✅ **Rewarded:** `ca-app-pub-2049534800625732/6071841522`

**Status:** ✅ VERIFIED - All real ad unit IDs are configured

---

## 🔍 **Step 3: Verify AdManager.kt**

### Check `app/src/main/java/com/example/gamesudoku/AdManager.kt`:

✅ **Line 35:** Uses `BuildConfig.USE_TEST_ADS` to switch between test/real ads
✅ **Line 85:** Banner ad uses: `if (USE_TEST_ADS) TEST_BANNER_AD_UNIT_ID else REAL_BANNER_AD_UNIT_ID`
✅ **Line 212:** Interstitial ad uses: `if (USE_TEST_ADS) TEST_INTERSTITIAL_AD_UNIT_ID else REAL_INTERSTITIAL_AD_UNIT_ID`
✅ **Line 285:** Rewarded ad uses: `if (USE_TEST_ADS) TEST_REWARDED_AD_UNIT_ID else REAL_REWARDED_AD_UNIT_ID`

**Status:** ✅ VERIFIED - AdManager correctly switches based on build type

---

## 🔍 **Step 4: Verify XML Layout Files**

### Check `app/src/main/res/layout/activity_main.xml`:
✅ **Line 388:** `ads:adUnitId="ca-app-pub-2049534800625732/8506433174"` (Real ID)

### Check `app/src/main/res/layout/activity_daily_challenge.xml`:
✅ **Line 320:** `ads:adUnitId="ca-app-pub-2049534800625732/8506433174"` (Real ID)

**Status:** ✅ VERIFIED - XML layouts use real ad unit IDs

---

## 🔍 **Step 5: Verify AndroidManifest.xml**

### Check `app/src/main/AndroidManifest.xml`:
✅ **Line 24:** AdMob App ID: `ca-app-pub-2049534800625732~1014670551` (Real App ID)

**Status:** ✅ VERIFIED - AndroidManifest uses real AdMob App ID

---

## 🔍 **Step 6: Build Release APK/AAB and Verify**

### Before Building:
1. ✅ Ensure you're building a **Release** build (not Debug)
2. ✅ In Android Studio: `Build` → `Select Build Variant` → Choose **release**

### Build Release:
```bash
./gradlew assembleRelease
# or for AAB (recommended for Play Console):
./gradlew bundleRelease
```

### Verify Build Output:
1. Check the generated APK/AAB is a **release** build
2. The APK/AAB should be in: `app/build/outputs/apk/release/` or `app/build/outputs/bundle/release/`

---

## 🔍 **Step 7: Test Release Build on Real Device**

### Important: Test on a REAL device (not emulator) before uploading to Play Console

1. **Install the release APK on a real Android device:**
   ```bash
   adb install app-release.apk
   ```

2. **Check Logcat for ad loading:**
   - Filter by: `AdManager`
   - Look for: `Loading banner ad with ID: ca-app-pub-2049534800625732/... (Test mode: false)`
   - ✅ Should see: `Test mode: false`
   - ❌ Should NOT see: `Test mode: true`

3. **Verify ad content:**
   - Real ads show actual advertiser content (not "Test Ad" labels)
   - Test ads show "Test Ad" watermark

4. **Expected Logcat Output (Release Build):**
   ```
   AdManager: Loading banner ad with ID: ca-app-pub-2049534800625732/8506433174 (Test mode: false)
   AdManager: ✅ Banner ad loaded successfully! Ad Unit ID: ca-app-pub-2049534800625732/8506433174
   ```

---

## 🔍 **Step 8: Final Checklist Before Uploading to Play Console**

- [ ] ✅ Release build has `USE_TEST_ADS = false` in `build.gradle`
- [ ] ✅ All ad unit IDs in code are real (not test IDs starting with `ca-app-pub-3940256099942544`)
- [ ] ✅ XML layouts use real ad unit IDs
- [ ] ✅ AndroidManifest has real AdMob App ID
- [ ] ✅ Built release APK/AAB (not debug)
- [ ] ✅ Tested release build on real device
- [ ] ✅ Logcat shows `Test mode: false` for all ads
- [ ] ✅ Ads show real content (not "Test Ad" watermarks)
- [ ] ✅ All ad types work: Banner, Interstitial, Rewarded

---

## 🚨 **Common Mistakes to Avoid**

1. ❌ **Don't upload Debug builds** - Always use Release builds
2. ❌ **Don't use test ad unit IDs** - Never use `ca-app-pub-3940256099942544/...`
3. ❌ **Don't forget to test** - Always test release builds on real devices
4. ❌ **Don't skip verification** - Check Logcat to confirm test mode is false

---

## 📝 **Quick Verification Command**

Run this command to search for any test ad IDs in your codebase:

```bash
# Search for test ad IDs (should only find them in AdManager.kt as constants)
grep -r "ca-app-pub-3940256099942544" app/src/main/

# Should only find:
# - app/src/main/java/com/example/gamesudoku/AdManager.kt (as TEST_* constants)
# - Should NOT find in XML files or actual usage
```

---

## ✅ **Current Status Summary**

Based on code analysis:

| Item | Status | Details |
|------|--------|---------|
| Release Build Config | ✅ VERIFIED | `USE_TEST_ADS = false` |
| Banner Ad ID | ✅ VERIFIED | Real ID in XML and code |
| Interstitial Ad ID | ✅ VERIFIED | Real ID in code |
| Rewarded Ad ID | ✅ VERIFIED | Real ID in code |
| AdMob App ID | ✅ VERIFIED | Real App ID in manifest |
| AdManager Logic | ✅ VERIFIED | Correctly switches based on build type |

**🎉 Your app is configured correctly for production!**

---

## 📤 **Ready to Upload to Play Console**

Once you've completed all verification steps above, you're ready to:

1. Build release AAB: `./gradlew bundleRelease`
2. Upload to Play Console → App Bundle
3. Complete release checklist in Play Console
4. Submit for review

**Good luck with your release! 🚀**
