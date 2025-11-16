package com.example.gamesudoku

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class AdManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AdManager"
        
        // Test Ad Unit IDs - These are Google's official test ad units
        private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        
        // Use test ads for now - replace with real ad unit IDs when ready for production
        private const val USE_TEST_ADS = true
    }
    
    private var interstitialAd: InterstitialAd? = null
    
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
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        
        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "Banner ad loaded successfully")
            }
            
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e(TAG, "Banner ad failed to load: ${loadAdError.message}")
            }
            
            override fun onAdOpened() {
                Log.d(TAG, "Banner ad opened")
            }
            
            override fun onAdClosed() {
                Log.d(TAG, "Banner ad closed")
            }
        }
    }
    
    /**
     * Load an interstitial ad
     */
    fun loadInterstitialAd() {
        val adUnitId = if (USE_TEST_ADS) TEST_INTERSTITIAL_AD_UNIT_ID else TEST_INTERSTITIAL_AD_UNIT_ID
        
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${loadAdError.message}")
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
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")
                    interstitialAd = null
                    // Load next ad
                    loadInterstitialAd()
                    onAdClosed?.invoke()
                }
                
                override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                    Log.e(TAG, "Interstitial ad failed to show: ${p0.message}")
                    interstitialAd = null
                    onAdClosed?.invoke()
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad showed")
                }
            }
            ad.show(activity)
        } ?: run {
            Log.d(TAG, "Interstitial ad not loaded yet")
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
     * Get test banner ad unit ID
     */
    fun getTestBannerAdUnitId(): String {
        return TEST_BANNER_AD_UNIT_ID
    }
}

