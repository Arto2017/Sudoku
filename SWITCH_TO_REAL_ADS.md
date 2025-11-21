# How to Switch from Test Ads to Real Ads

Your code has been updated to use real ads! Now you need to replace the placeholder IDs with your actual AdMob ad unit IDs.

## Step 1: Get Your AdMob Ad Unit IDs

1. Go to [AdMob Dashboard](https://admob.google.com)
2. Sign in with your Google account
3. Select your app (or create a new app if you haven't already)
4. Go to **"Ad units"** section
5. Create the following ad units if you haven't already:
   - **Banner Ad** (for bottom of screen)
   - **Interstitial Ad** (full-screen ads)
   - **Rewarded Ad** (for rewards/hints)

6. Copy each Ad Unit ID (format: `ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX`)

## Step 2: Get Your AdMob App ID

1. In AdMob dashboard, go to **"Apps"** → Select your app
2. Click on **"App settings"**
3. Copy your **App ID** (format: `ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX`)

## Step 3: Update Your Code

### Update AdManager.kt

Open `app/src/main/java/com/example/gamesudoku/AdManager.kt` and replace these placeholder values:

```kotlin
// Replace these with your real Ad Unit IDs from AdMob
private const val REAL_BANNER_AD_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
private const val REAL_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
private const val REAL_REWARDED_AD_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
```

**Important:** Make sure `USE_TEST_ADS = false` (it's already set to false).

### Update strings.xml

Open `app/src/main/res/values/strings.xml` and replace:

```xml
<string name="banner_ad_unit_id">ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX</string>
```

With your real banner ad unit ID.

### Update AndroidManifest.xml

Open `app/src/main/AndroidManifest.xml` and replace:

```xml
android:value="ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX"
```

With your real AdMob App ID.

## Step 4: Verify Everything

After updating all the IDs:

1. **Build your app** to make sure there are no errors
2. **Test on a real device** (not emulator) - ads won't show on emulators
3. **Check Logcat** for any ad loading errors

## Important Notes

⚠️ **Never use test ad IDs in production** - Always use real IDs when releasing your app

⚠️ **AdMob Account Approval** - Your AdMob account needs to be approved before real ads will show (may take 1-2 days)

⚠️ **Test First** - You can temporarily set `USE_TEST_ADS = true` to test that ads are working, then switch back to `false` with real IDs

## Quick Checklist

- [ ] Got Banner Ad Unit ID from AdMob
- [ ] Got Interstitial Ad Unit ID from AdMob
- [ ] Got Rewarded Ad Unit ID from AdMob
- [ ] Got App ID from AdMob
- [ ] Updated `REAL_BANNER_AD_UNIT_ID` in AdManager.kt
- [ ] Updated `REAL_INTERSTITIAL_AD_UNIT_ID` in AdManager.kt
- [ ] Updated `REAL_REWARDED_AD_UNIT_ID` in AdManager.kt
- [ ] Updated `banner_ad_unit_id` in strings.xml
- [ ] Updated App ID in AndroidManifest.xml
- [ ] Verified `USE_TEST_ADS = false` in AdManager.kt
- [ ] Built and tested the app

## Need Help?

- AdMob Help: https://support.google.com/admob
- AdMob Policies: https://support.google.com/admob/answer/6128543

Good luck! 🚀

