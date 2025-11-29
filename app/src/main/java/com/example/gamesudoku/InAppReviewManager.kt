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
     * 
     * If the dialog doesn't appear, it will fallback to opening Play Store directly.
     */
    fun requestReview(onComplete: (() -> Unit)? = null) {
        try {
            // Get the ReviewManager
            val reviewManager = ReviewManagerFactory.create(activity)
            
            // Request review info
            val request = reviewManager.requestReviewFlow()
            request.addOnCompleteListener { requestTask ->
                if (requestTask.isSuccessful) {
                    // Review info obtained successfully
                    val reviewInfo = requestTask.result
                    
                    // Launch the review flow
                    val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener { flowTask ->
                        // Review flow completed (user rated or dismissed)
                        Log.d(TAG, "Review flow completed")
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
        try {
            val packageName = activity.packageName
            val marketUri = android.net.Uri.parse("market://details?id=$packageName")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, marketUri)
            activity.startActivity(intent)
            Log.d(TAG, "Opened Play Store as fallback")
        } catch (e: Exception) {
            try {
                // Try browser as last resort
                val packageName = activity.packageName
                val webUri = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
                activity.startActivity(intent)
                Log.d(TAG, "Opened Play Store in browser as fallback")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Play Store: ${e2.message}")
                android.widget.Toast.makeText(activity, "Unable to open Play Store", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

