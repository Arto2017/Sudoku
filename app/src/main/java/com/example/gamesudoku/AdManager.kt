package com.example.gamesudoku

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
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
        
        // Set to false to use real ads (make sure to replace the REAL_*_AD_UNIT_ID values above first!)
        private const val USE_TEST_ADS = false
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardedAdLoadCallback: (() -> Unit)? = null
    
    init {
        MobileAds.initialize(context) { initializationStatus ->
            val statusMap = initializationStatus.adapterStatusMap
            for (adapterClass in statusMap.keys) {
                val status = statusMap[adapterClass]
                Log.d(TAG, "Adapter: $adapterClass, Status: ${status?.initializationState}, Description: ${status?.description}")
            }
        }
    }
    
    /**
     * Load a banner ad
     */
    fun loadBannerAd(adView: AdView) {
        val adUnitId = if (USE_TEST_ADS) TEST_BANNER_AD_UNIT_ID else REAL_BANNER_AD_UNIT_ID
        Log.d(TAG, "Loading banner ad with ID: $adUnitId (Test mode: $USE_TEST_ADS)")
        
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        
        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "✅ Banner ad loaded successfully! Ad Unit ID: $adUnitId")
            }
            
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e(TAG, "❌ Banner ad failed to load")
                Log.e(TAG, "   Error Code: ${loadAdError.code}")
                Log.e(TAG, "   Error Message: ${loadAdError.message}")
                Log.e(TAG, "   Error Domain: ${loadAdError.domain}")
                Log.e(TAG, "   Ad Unit ID: $adUnitId")
                // Common error codes:
                // 0 = ERROR_CODE_INTERNAL_ERROR
                // 1 = ERROR_CODE_INVALID_REQUEST
                // 2 = ERROR_CODE_NETWORK_ERROR
                // 3 = ERROR_CODE_NO_FILL (no ads available - normal for new accounts)
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
     */
    fun loadRewardedAd(onLoaded: (() -> Unit)? = null) {
        val adUnitId = if (USE_TEST_ADS) TEST_REWARDED_AD_UNIT_ID else REAL_REWARDED_AD_UNIT_ID
        Log.d(TAG, "Loading rewarded ad with ID: $adUnitId (Test mode: $USE_TEST_ADS)")
        
        // Store callback if provided
        if (onLoaded != null) {
            rewardedAdLoadCallback = onLoaded
        }
        
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "✅ Rewarded ad loaded successfully! Ad Unit ID: $adUnitId")
                    rewardedAd = ad
                    // Notify callback if set
                    rewardedAdLoadCallback?.invoke()
                    rewardedAdLoadCallback = null
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "❌ Rewarded ad failed to load")
                    Log.e(TAG, "   Error Code: ${loadAdError.code}")
                    Log.e(TAG, "   Error Message: ${loadAdError.message}")
                    Log.e(TAG, "   Error Domain: ${loadAdError.domain}")
                    Log.e(TAG, "   Ad Unit ID: $adUnitId")
                    rewardedAd = null
                    // Clear callback on failure
                    rewardedAdLoadCallback = null
                }
            }
        )
    }
    
    /**
     * Show rewarded ad if loaded
     */
    fun showRewardedAd(activity: android.app.Activity, onAdClosed: (() -> Unit)? = null, onUserEarnedReward: ((RewardItem) -> Unit)? = null) {
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
     * Get banner ad unit ID (test or real based on USE_TEST_ADS flag)
     */
    fun getBannerAdUnitId(): String {
        return if (USE_TEST_ADS) TEST_BANNER_AD_UNIT_ID else REAL_BANNER_AD_UNIT_ID
    }
}

