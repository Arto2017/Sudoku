package com.artashes.sudoku

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Manages Google Play In-App Review functionality
 * Shows a beautiful native rating dialog with stars
 */
class InAppReviewManager(private val activity: Activity) {
    
    companion object {
        private const val TAG = "InAppReviewManager"
    }
    
    /**
     * Request and show the in-app review dialog
     * This shows a beautiful native dialog with stars where users can rate the app
     * 
     * IMPORTANT: Google Play controls when the dialog appears to prevent over-prompting.
     * The dialog may not show every time - Google limits it based on:
     * - How recently the user was asked to rate
     * - User's previous rating behavior
     * - Device and account factors
     * - Whether the app is published on Play Store
     * 
     * During development, the dialog often doesn't appear. This method will try In-App Review
     * but always fallback to opening Play Store directly to ensure the user can always rate.
     */
    fun requestReview(onComplete: (() -> Unit)? = null) {
        try {
            Log.d(TAG, "Requesting in-app review...")
            
            // Get the ReviewManager
            val reviewManager = ReviewManagerFactory.create(activity)
            
            // Request review info
            val request = reviewManager.requestReviewFlow()
            request.addOnCompleteListener { requestTask ->
                if (requestTask.isSuccessful) {
                    // Review info obtained successfully
                    val reviewInfo = requestTask.result
                    Log.d(TAG, "Review info obtained successfully, launching review flow...")
                    
                    // Launch the review flow
                    val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener { flowTask ->
                        // Review flow completed (user rated or dismissed)
                        Log.d(TAG, "Review flow completed (success: ${flowTask.isSuccessful})")
                        
                        // IMPORTANT: Even if flowTask.isSuccessful, Google may not have shown the dialog.
                        // This is especially common during development. So we always provide a fallback
                        // after a short delay to ensure the user can rate.
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // Always open Play Store as fallback to ensure user can rate
                            // This ensures the button always does something visible
                            Log.d(TAG, "Opening Play Store as fallback to ensure rating is possible")
                            openPlayStoreFallback()
                        }, 1000) // 1 second delay - if In-App Review dialog appeared, user will see it first
                        
                        onComplete?.invoke()
                    }
                } else {
                    // Failed to get review info
                    val exception = requestTask.exception
                    Log.e(TAG, "Failed to request review flow: ${exception?.message}")
                    
                    // Fallback to opening Play Store directly
                    openPlayStoreFallback()
                    onComplete?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting review: ${e.message}", e)
            // Fallback to opening Play Store directly
            openPlayStoreFallback()
            onComplete?.invoke()
        }
    }
    
    /**
     * Fallback method to open Play Store if In-App Review is not available
     */
    private fun openPlayStoreFallback() {
        val packageName = activity.packageName
        Log.d(TAG, "Attempting to open Play Store for package: $packageName")
        
        // Method 1: Try Play Store app with market:// URI
        try {
            val marketUri = android.net.Uri.parse("market://details?id=$packageName")
            val marketIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, marketUri)
            marketIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            
            val resolveInfo = activity.packageManager.resolveActivity(marketIntent, 0)
            if (resolveInfo != null) {
                activity.startActivity(marketIntent)
                Log.d(TAG, "✅ Successfully opened Play Store app")
                return
            } else {
                Log.w(TAG, "Play Store app not found, trying browser...")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open Play Store app: ${e.message}, trying browser...")
        }
        
        // Method 2: Try browser with web URL
        try {
            val webUri = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
            webIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            
            val resolveInfo = activity.packageManager.resolveActivity(webIntent, 0)
            if (resolveInfo != null) {
                activity.startActivity(webIntent)
                Log.d(TAG, "✅ Successfully opened Play Store in browser")
                return
            } else {
                Log.e(TAG, "No browser available to open Play Store")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Play Store in browser: ${e.message}", e)
        }
        
        // Method 3: Try alternative Play Store package name
        try {
            val playStoreIntent = activity.packageManager.getLaunchIntentForPackage("com.android.vending")
            if (playStoreIntent != null) {
                playStoreIntent.data = android.net.Uri.parse("market://details?id=$packageName")
                playStoreIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(playStoreIntent)
                Log.d(TAG, "✅ Successfully opened Play Store using package name")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open Play Store using package name: ${e.message}")
        }
        
        // If all methods failed, show error message to user
        Log.e(TAG, "❌ All methods failed to open Play Store")
        android.widget.Toast.makeText(
            activity, 
            "Unable to open Play Store. Please search for \"Sudoku\" in Google Play Store.", 
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
    
    /**
     * Direct method to open Play Store (bypasses In-App Review API)
     * Use this if you want to always open Play Store directly
     */
    fun openPlayStoreDirectly() {
        openPlayStoreFallback()
    }
}

