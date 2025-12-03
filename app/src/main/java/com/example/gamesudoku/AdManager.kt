package com.artashes.sudoku

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem

class AdManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AdManager"
        
        // Test Ad Unit IDs - These are Google's official test ad units
        private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        
        // Real Ad Unit IDs - All configured with your AdMob ad unit IDs
        private const val REAL_BANNER_AD_UNIT_ID = "ca-app-pub-2049534800625732/8506433174"
        private const val REAL_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-2049534800625732/8282964095"
        private const val REAL_REWARDED_AD_UNIT_ID = "ca-app-pub-2049534800625732/6071841522"
        
        // Use BuildConfig to automatically switch between test and real ads
        // Debug builds use test ads, Release builds use real ads
        private val USE_TEST_ADS = com.artashes.sudoku.BuildConfig.USE_TEST_ADS
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardedAdLoadCallback: (() -> Unit)? = null
    
    // Retry logic for banner ads
    private var bannerAdRetryCount = 0
    private val maxBannerRetries = 3
    private val bannerRetryHandler = Handler(Looper.getMainLooper())
    private var currentBannerAdView: AdView? = null
    
    // Ad failure tracking and cooldown
    private var rewardedAdFailureTime: Long = 0
    private val rewardedAdCooldownMs = 5 * 60 * 1000L // 5 minutes cooldown after failure
    private var rewardedAdFailureCallback: ((String) -> Unit)? = null // Callback for failure notification
    
    init {
        // Configure test devices for debug builds
        if (USE_TEST_ADS) {
            val testDeviceIds = listOf(
                AdRequest.DEVICE_ID_EMULATOR, // Emulator
                // Add your physical device ID here for testing
                // Get it from logcat: "To get test ads on this device, set adRequest.testDevices = listOf("XXXXX")"
            )
            val requestConfiguration = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)
            Log.d(TAG, "Test devices configured for debug builds")
        }
        
        MobileAds.initialize(context) { initializationStatus ->
            val statusMap = initializationStatus.adapterStatusMap
            for (adapterClass in statusMap.keys) {
                val status = statusMap[adapterClass]
                Log.d(TAG, "Adapter: $adapterClass, Status: ${status?.initializationState}, Description: ${status?.description}")
            }
        }
    }
    
    /**
     * Load a banner ad with automatic retry for network errors
     */
    fun loadBannerAd(adView: AdView) {
        currentBannerAdView = adView
        bannerAdRetryCount = 0
        loadBannerAdInternal(adView)
    }
    
    /**
     * Internal method to load banner ad with retry logic
     */
    private fun loadBannerAdInternal(adView: AdView) {
        val adUnitId = if (USE_TEST_ADS) TEST_BANNER_AD_UNIT_ID else REAL_BANNER_AD_UNIT_ID
        
        try {
            Log.d(TAG, "Loading banner ad with ID: $adUnitId (Test mode: $USE_TEST_ADS)${if (bannerAdRetryCount > 0) " [Retry $bannerAdRetryCount/$maxBannerRetries]" else ""}")
            
            // Try to set ad unit ID programmatically
            // Note: If adUnitId is set in XML, it cannot be changed programmatically
            // XML has test ad unit ID as default - we'll override for release builds if possible
            var currentAdUnitId: String? = null
            try {
                currentAdUnitId = adView.adUnitId
            } catch (e: Exception) {
                Log.w(TAG, "Could not read current adUnitId: ${e.message}")
            }
            
            // Check if we need to override the XML value (for release builds)
            val needsOverride = currentAdUnitId != null && 
                                currentAdUnitId == TEST_BANNER_AD_UNIT_ID && 
                                !USE_TEST_ADS
            
            if (currentAdUnitId == null || currentAdUnitId.isEmpty() || needsOverride) {
                try {
                    adView.adUnitId = adUnitId
                    Log.d(TAG, "Set ad unit ID to: $adUnitId")
                } catch (e: IllegalStateException) {
                    // Ad unit ID already set in XML and cannot be changed
                    // This is OK - XML has test ad unit ID, which is fine for debug builds
                    // For release builds, we need to ensure XML has the correct ID
                    if (!USE_TEST_ADS && currentAdUnitId == TEST_BANNER_AD_UNIT_ID) {
                        Log.w(TAG, "⚠️ WARNING: Cannot override XML adUnitId. Release build should have real ad unit ID in XML!")
                        Log.w(TAG, "Current adUnitId from XML: $currentAdUnitId")
                        Log.w(TAG, "Expected adUnitId for release: $adUnitId")
                    } else {
                        Log.d(TAG, "Using existing ad unit ID from XML: $currentAdUnitId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting ad unit ID: ${e.message}", e)
                    return
                }
            } else {
                Log.d(TAG, "AdView already has ad unit ID: $currentAdUnitId, using existing ID")
            }
            
            // Verify adUnitId is set before loading
            var finalAdUnitId: String? = null
            try {
                finalAdUnitId = adView.adUnitId
            } catch (e: Exception) {
                Log.e(TAG, "Cannot verify ad unit ID: ${e.message}", e)
                return
            }
            
            if (finalAdUnitId == null || finalAdUnitId.isEmpty()) {
                Log.e(TAG, "❌ Cannot load ad: adUnitId is not set!")
                return
            }
            
            // Build ad request with test device configuration for debug builds
            val adRequestBuilder = AdRequest.Builder()
            
            // Add test device configuration for debug builds (even though it's set globally, 
            // adding it here ensures test ads work properly)
            if (USE_TEST_ADS) {
                // Test devices are already configured globally in init(), but we can add 
                // additional test device configuration here if needed
                Log.d(TAG, "Using test ad request for banner ad")
            }
            
            val adRequest = adRequestBuilder.build()
            adView.loadAd(adRequest)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fatal error loading banner ad: ${e.message}", e)
            return
        }
        
        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "✅ Banner ad loaded successfully! Ad Unit ID: $adUnitId")
                // Reset retry count on success
                bannerAdRetryCount = 0
            }
            
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e(TAG, "❌ Banner ad failed to load")
                Log.e(TAG, "   Error Code: ${loadAdError.code}")
                Log.e(TAG, "   Error Message: ${loadAdError.message}")
                Log.e(TAG, "   Error Domain: ${loadAdError.domain}")
                Log.e(TAG, "   Ad Unit ID: $adUnitId")
                
                // Common error codes:
                // 0 = ERROR_CODE_INTERNAL_ERROR
                // 1 = ERROR_CODE_INVALID_REQUEST (usually transient, but don't retry as it might be config issue)
                // 2 = ERROR_CODE_NETWORK_ERROR (retry with exponential backoff)
                // 3 = ERROR_CODE_NO_FILL (no ads available - normal for new accounts, don't retry)
                
                // Only retry on network errors (Error Code 2) and limit retries
                if (loadAdError.code == 2 && bannerAdRetryCount < maxBannerRetries) {
                    bannerAdRetryCount++
                    val retryDelay = (1000 * bannerAdRetryCount).toLong() // Exponential backoff: 1s, 2s, 3s
                    Log.w(TAG, "   Network error detected. Retrying in ${retryDelay}ms... (Attempt $bannerAdRetryCount/$maxBannerRetries)")
                    
                    bannerRetryHandler.postDelayed({
                        // Only retry if the ad view is still valid
                        currentBannerAdView?.let {
                            loadBannerAdInternal(it)
                        }
                    }, retryDelay)
                } else {
                    // Reset retry count after max retries or non-retryable error
                    if (loadAdError.code == 2) {
                        Log.w(TAG, "   Max retries reached. Giving up on banner ad load.")
                    } else {
                        Log.d(TAG, "   Error code ${loadAdError.code} is not retryable. Check your ad configuration if this persists.")
                    }
                    bannerAdRetryCount = 0
                }
            }
            
            override fun onAdOpened() {
                Log.d(TAG, "Banner ad opened (user clicked)")
            }
            
            override fun onAdClosed() {
                Log.d(TAG, "Banner ad closed")
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "✅ Banner ad impression recorded (ad was shown)")
            }
        }
    }
    
    /**
     * Load an interstitial ad
     */
    fun loadInterstitialAd() {
        val adUnitId = if (USE_TEST_ADS) TEST_INTERSTITIAL_AD_UNIT_ID else REAL_INTERSTITIAL_AD_UNIT_ID
        Log.d(TAG, "Loading interstitial ad with ID: $adUnitId (Test mode: $USE_TEST_ADS)")
        
        // Build ad request
        // Test devices are configured globally in init() for debug builds
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "✅ Interstitial ad loaded successfully! Ad Unit ID: $adUnitId")
                    interstitialAd = ad
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "❌ Interstitial ad failed to load")
                    Log.e(TAG, "   Error Code: ${loadAdError.code}")
                    Log.e(TAG, "   Error Message: ${loadAdError.message}")
                    Log.e(TAG, "   Error Domain: ${loadAdError.domain}")
                    Log.e(TAG, "   Ad Unit ID: $adUnitId")
                    interstitialAd = null
                }
            }
        )
    }
    
    /**
     * Show interstitial ad if loaded
     */
    fun showInterstitialAd(activity: android.app.Activity, onAdClosed: (() -> Unit)? = null) {
        interstitialAd?.let { ad ->
            Log.d(TAG, "Showing interstitial ad...")
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "✅ Interstitial ad dismissed (user closed it)")
                    interstitialAd = null
                    // Load next ad
                    loadInterstitialAd()
                    onAdClosed?.invoke()
                }
                
                override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                    Log.e(TAG, "❌ Interstitial ad failed to show: ${p0.message}")
                    Log.e(TAG, "   Error Code: ${p0.code}")
                    interstitialAd = null
                    onAdClosed?.invoke()
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "✅ Interstitial ad showed successfully (full screen)")
                }
            }
            ad.show(activity)
        } ?: run {
            Log.w(TAG, "⚠️ Interstitial ad not loaded yet - cannot show")
            onAdClosed?.invoke()
        }
    }
    
    /**
     * Check if interstitial ad is loaded
     */
    fun isInterstitialAdLoaded(): Boolean {
        return interstitialAd != null
    }
    
    /**
     * Load a rewarded ad
     * @param onLoaded Callback when ad loads successfully
     * @param onFailure Callback when ad fails to load (receives error message)
     */
    fun loadRewardedAd(onLoaded: (() -> Unit)? = null, onFailure: ((String) -> Unit)? = null) {
        // Check if we're in cooldown period
        val timeSinceFailure = System.currentTimeMillis() - rewardedAdFailureTime
        if (rewardedAdFailureTime > 0 && timeSinceFailure < rewardedAdCooldownMs) {
            val remainingSeconds = (rewardedAdCooldownMs - timeSinceFailure) / 1000
            val formattedTime = formatTimeAsMinutesSeconds(remainingSeconds)
            val errorMessage = "Ads are currently unavailable. Please wait $formattedTime or continue playing."
            Log.w(TAG, "⚠️ Ad load blocked - still in cooldown period (${remainingSeconds}s remaining)")
            onFailure?.invoke(errorMessage)
            return
        }
        
        // Reset failure time if cooldown has passed
        if (timeSinceFailure >= rewardedAdCooldownMs) {
            rewardedAdFailureTime = 0
        }
        
        val adUnitId = if (USE_TEST_ADS) TEST_REWARDED_AD_UNIT_ID else REAL_REWARDED_AD_UNIT_ID
        Log.d(TAG, "Loading rewarded ad with ID: $adUnitId (Test mode: $USE_TEST_ADS)")
        
        // Store callbacks if provided
        if (onLoaded != null) {
            rewardedAdLoadCallback = onLoaded
        }
        if (onFailure != null) {
            rewardedAdFailureCallback = onFailure
        }
        
        // Build ad request
        // Test devices are configured globally in init() for debug builds
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "✅ Rewarded ad loaded successfully! Ad Unit ID: $adUnitId")
                    rewardedAd = ad
                    // Reset failure time on successful load
                    rewardedAdFailureTime = 0
                    // Notify callback if set
                    rewardedAdLoadCallback?.invoke()
                    rewardedAdLoadCallback = null
                    rewardedAdFailureCallback = null
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "❌ Rewarded ad failed to load")
                    Log.e(TAG, "   Error Code: ${loadAdError.code}")
                    Log.e(TAG, "   Error Message: ${loadAdError.message}")
                    Log.e(TAG, "   Error Domain: ${loadAdError.domain}")
                    Log.e(TAG, "   Ad Unit ID: $adUnitId")
                    rewardedAd = null
                    
                    // Record failure time and start cooldown
                    rewardedAdFailureTime = System.currentTimeMillis()
                    Log.d(TAG, "Ad failure cooldown started. Will not retry for ${rewardedAdCooldownMs / 60000} minutes")
                    
                    // Notify callback about failure
                    // The cooldown will be set, so the next call will show the formatted time
                    val errorMessage = when (loadAdError.code) {
                        0 -> "Ads are currently unavailable. Please wait 5:00 or continue playing."
                        1 -> "Ads are not available now. Please try again later."
                        2 -> "Network error. Please check your connection and try again."
                        3 -> "No ads available. Please wait 5:00 or continue playing."
                        else -> "Ads are currently unavailable. Please wait 5:00 or continue playing."
                    }
                    rewardedAdFailureCallback?.invoke(errorMessage)
                    
                    // Clear callbacks on failure
                    rewardedAdLoadCallback = null
                    rewardedAdFailureCallback = null
                }
            }
        )
    }
    
    /**
     * Show rewarded ad if loaded
     */
    fun showRewardedAd(activity: android.app.Activity, onAdClosed: (() -> Unit)? = null, onUserEarnedReward: ((RewardItem) -> Unit)? = null, onAdShowed: (() -> Unit)? = null) {
        rewardedAd?.let { ad ->
            Log.d(TAG, "Showing rewarded ad...")
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "✅ Rewarded ad dismissed (user closed it)")
                    rewardedAd = null
                    // Load next ad
                    loadRewardedAd()
                    onAdClosed?.invoke()
                }
                
                override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                    Log.e(TAG, "❌ Rewarded ad failed to show: ${p0.message}")
                    Log.e(TAG, "   Error Code: ${p0.code}")
                    rewardedAd = null
                    onAdClosed?.invoke()
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "✅ Rewarded ad showed successfully (full screen)")
                    onAdShowed?.invoke()
                }
            }
            
            ad.show(activity) { rewardItem ->
                Log.d(TAG, "🎁 User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onUserEarnedReward?.invoke(rewardItem)
            }
        } ?: run {
            Log.w(TAG, "⚠️ Rewarded ad not loaded yet - cannot show")
            onAdClosed?.invoke()
        }
    }
    
    /**
     * Check if rewarded ad is loaded
     */
    fun isRewardedAdLoaded(): Boolean {
        return rewardedAd != null
    }
    
    /**
     * Check if rewarded ad is in cooldown period (after failure)
     */
    fun isRewardedAdInCooldown(): Boolean {
        val timeSinceFailure = System.currentTimeMillis() - rewardedAdFailureTime
        return rewardedAdFailureTime > 0 && timeSinceFailure < rewardedAdCooldownMs
    }
    
    /**
     * Get remaining cooldown time in seconds
     */
    fun getRewardedAdCooldownRemainingSeconds(): Long {
        if (!isRewardedAdInCooldown()) return 0
        val timeSinceFailure = System.currentTimeMillis() - rewardedAdFailureTime
        return (rewardedAdCooldownMs - timeSinceFailure) / 1000
    }
    
    /**
     * Format seconds as "M:SS" (e.g., "4:59")
     */
    private fun formatTimeAsMinutesSeconds(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }
    
    /**
     * Get formatted cooldown time as "M:SS"
     */
    fun getRewardedAdCooldownFormatted(): String {
        val seconds = getRewardedAdCooldownRemainingSeconds()
        return formatTimeAsMinutesSeconds(seconds)
    }
    
    /**
     * Get banner ad unit ID (test or real based on USE_TEST_ADS flag)
     */
    fun getBannerAdUnitId(): String {
        return if (USE_TEST_ADS) TEST_BANNER_AD_UNIT_ID else REAL_BANNER_AD_UNIT_ID
    }
}

