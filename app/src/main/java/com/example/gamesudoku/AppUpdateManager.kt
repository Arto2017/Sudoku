package com.artashes.sudoku

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Manages Google Play In-App Updates
 * Prompts users to update when a new version is available
 */
class AppUpdateManager(private val activity: Activity) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val TAG = "AppUpdateManager"
    
    /**
     * Check for updates and prompt user if available
     * Uses FLEXIBLE update mode - user can continue using app while update downloads
     */
    fun checkForUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                // Check if update is allowed (not on metered connection, etc.)
                if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    Log.d(TAG, "Update available, starting flexible update")
                    startFlexibleUpdate(appUpdateInfo)
                } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    // Fallback to immediate update if flexible not allowed
                    Log.d(TAG, "Update available, starting immediate update")
                    startImmediateUpdate(appUpdateInfo)
                }
            } else {
                Log.d(TAG, "No update available")
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for update", e)
        }
    }
    
    /**
     * Start flexible update - downloads in background, user can continue playing
     */
    private fun startFlexibleUpdate(appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                AppUpdateType.FLEXIBLE,
                activity,
                UPDATE_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start flexible update", e)
        }
    }
    
    /**
     * Start immediate update - blocks app until update is installed
     * Only used if flexible update is not allowed
     */
    private fun startImmediateUpdate(appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                AppUpdateType.IMMEDIATE,
                activity,
                UPDATE_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start immediate update", e)
        }
    }
    
    /**
     * Check if update is in progress and handle completion
     * Call this in onResume() to handle update completion
     */
    fun handleUpdateResult(requestCode: Int, resultCode: Int) {
        if (requestCode == UPDATE_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                Log.d(TAG, "Update flow failed! Result code: $resultCode")
            } else {
                Log.d(TAG, "Update completed successfully")
            }
        }
    }
    
    /**
     * Check if update is ready to install (for flexible updates)
     * Call this in onResume() to prompt user to restart after download
     */
    fun checkUpdateStatus() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == com.google.android.play.core.install.model.InstallStatus.DOWNLOADED) {
                // Update downloaded, prompt user to restart
                Log.d(TAG, "Update downloaded, ready to install")
                // You can show a snackbar or dialog here to prompt restart
                // For now, we'll just log it - you can add UI later if needed
            }
        }
    }
    
    companion object {
        const val UPDATE_REQUEST_CODE = 100
    }
}
