# AdMob Setup Guide for Your Sudoku Game

## Step 1: Create AdMob Account
1. Go to https://admob.google.com
2. Sign in with your Google account
3. Create an AdMob account
4. Add your app to AdMob

## Step 2: Get Your Ad Unit IDs
1. In AdMob dashboard, go to "Apps" → Your App
2. Create ad units:
   - **Banner Ad** (for bottom of screen)
   - **Interstitial Ad** (full-screen, show after game completion)
   - **Rewarded Ad** (optional, for extra hints/rewards)
3. Copy the Ad Unit IDs (format: `ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX`)

## Step 3: Update Your Code

### Replace Test Ad IDs
In `AdManager.kt`, replace the test IDs with your real Ad Unit IDs:

```kotlin
// Replace these with your real IDs from AdMob
private val BANNER_AD_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
private val INTERSTITIAL_AD_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
private val REWARDED_AD_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
```

### Update AndroidManifest.xml
Replace the test App ID in `AndroidManifest.xml` with your real AdMob App ID:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX"/>
```

## Step 4: Add Banner Ad to Main Menu

Add this to `activity_main_menu.xml` at the bottom:

```xml
<com.google.android.gms.ads.AdView
    android:id="@+id/adView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_alignParentBottom="true"
    app:adSize="BANNER"
    app:adUnitId="@string/banner_ad_unit_id"/>
```

## Step 5: Initialize Ads in MainMenuActivity

Add to `onCreate()`:

```kotlin
private lateinit var adManager: AdManager

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ... existing code ...
    
    adManager = AdManager(this)
    
    // Load banner ad
    val adView = findViewById<AdView>(R.id.adView)
    adManager.loadBannerAd(adView)
    
    // Preload interstitial ad
    adManager.loadInterstitialAd()
}
```

## Step 6: Show Interstitial Ad After Game Completion

In `MainActivity.kt`, after showing victory dialog:

```kotlin
// Show ad after game completion (every 3-5 games)
if (gamesCompleted % 3 == 0) {
    adManager.showInterstitialAd(this) {
        // Ad closed, continue
    }
}
```

## Best Practices

1. **Don't show ads too frequently** - Show interstitial ads every 3-5 game completions
2. **Test with test IDs first** - Use test IDs during development
3. **Respect user experience** - Don't interrupt gameplay with ads
4. **Show ads at natural breaks** - After level completion, between games
5. **Consider rewarded ads** - Let users watch ads for extra hints

## Testing

- Use test ad IDs during development (already in code)
- Test on real devices, not emulators
- Wait for AdMob account approval before using real ads

## Important Notes

- ⚠️ **Never use test ad IDs in production** - Replace with real IDs before release
- ⚠️ **AdMob account approval** - May take 1-2 days after app submission
- ⚠️ **Minimum age** - Must be 18+ to have AdMob account
- ⚠️ **Policy compliance** - Follow Google AdMob policies

## Revenue Tips

- **Banner ads**: Lower revenue but less intrusive
- **Interstitial ads**: Higher revenue, show at natural breaks
- **Rewarded ads**: Highest engagement, users choose to watch
- **Frequency**: Balance user experience with revenue

Good luck with your app! 🚀

