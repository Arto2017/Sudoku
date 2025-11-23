package com.artashes.sudoku

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

object GameNotification {
    
    /**
     * Show a styled in-game notification that matches the fantasy theme
     * @param activity The activity to show notification in
     * @param message The message to display
     * @param icon The emoji/icon to show (optional, default: ✨)
     * @param duration Duration in milliseconds (default: 2000ms)
     */
    fun show(
        activity: AppCompatActivity,
        message: String,
        icon: String = "✨",
        duration: Long = 2000
    ) {
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val notificationView = LayoutInflater.from(activity)
            .inflate(R.layout.notification_message, null)
        
        val iconView = notificationView.findViewById<TextView>(R.id.notificationIcon)
        val textView = notificationView.findViewById<TextView>(R.id.notificationText)
        
        iconView.text = icon
        textView.text = message
        
        // Add to root view with proper layout params
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
            topMargin = (16 * activity.resources.displayMetrics.density).toInt()
        }
        rootView.addView(notificationView, layoutParams)
        
        // Set initial state (invisible and translated up)
        notificationView.alpha = 0f
        notificationView.translationY = -50f
        
        // Animate in
        val fadeIn = ObjectAnimator.ofFloat(notificationView, "alpha", 0f, 1f)
        val slideIn = ObjectAnimator.ofFloat(notificationView, "translationY", -50f, 0f)
        fadeIn.duration = 300
        slideIn.duration = 300
        fadeIn.interpolator = AccelerateDecelerateInterpolator()
        slideIn.interpolator = AccelerateDecelerateInterpolator()
        
        fadeIn.start()
        slideIn.start()
        
        // Auto-dismiss after duration
        notificationView.postDelayed({
            // Animate out
            val fadeOut = ObjectAnimator.ofFloat(notificationView, "alpha", 1f, 0f)
            val slideOut = ObjectAnimator.ofFloat(notificationView, "translationY", 0f, -30f)
            fadeOut.duration = 300
            slideOut.duration = 300
            fadeOut.interpolator = AccelerateDecelerateInterpolator()
            slideOut.interpolator = AccelerateDecelerateInterpolator()
            
            fadeOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rootView.removeView(notificationView)
                }
            })
            
            fadeOut.start()
            slideOut.start()
        }, duration)
    }
    
    /**
     * Show a success notification (green icon)
     */
    fun showSuccess(activity: AppCompatActivity, message: String, duration: Long = 2000) {
        show(activity, message, "✓", duration)
    }
    
    /**
     * Show an error/warning notification (red icon)
     */
    fun showError(activity: AppCompatActivity, message: String, duration: Long = 2000) {
        show(activity, message, "⚠", duration)
    }
    
    /**
     * Show an info notification (blue icon)
     */
    fun showInfo(activity: AppCompatActivity, message: String, duration: Long = 2000) {
        show(activity, message, "ℹ", duration)
    }
}

