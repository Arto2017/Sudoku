package com.artashes.sudoku

import android.content.Context
import java.util.*

/**
 * Manages rate limiting for rewarded ads based on puzzle type (6×6 vs 9×9).
 * Implements AdMob best practices: 2 ads per 30 minutes maximum.
 * 
 * 6×6 Puzzles:
 * - Cooldown: 10 minutes (AdMob compliant - allows 6 ads/hour max)
 * - Max per puzzle: 2 ads (very conservative limit)
 * - Max per hour: 6 ads
 * - Max per day: 30 ads (conservative limit)
 * 
 * 9×9 Puzzles:
 * - Cooldown: 15 minutes (AdMob best practice - allows 4 ads/hour max)
 * - Max per puzzle: 4 ads (conservative limit)
 * - Max per hour: 4 ads
 * - Max per day: 40 ads (conservative limit)
 * 
 * Note: These limits apply to ALL rewarded ads (hints + mistake recovery)
 */
class AdRateLimiter(context: Context) {
    private val prefs = context.getSharedPreferences("ad_rate_limiter", Context.MODE_PRIVATE)
    
    companion object {
        // 6×6 Puzzle limits
        // AdMob best practice: 2 ads per 30 minutes = 15 minutes cooldown
        // Using 10 minutes for 6×6 to allow slightly more flexibility
        private const val COOLDOWN_6X6_MS = 10 * 60 * 1000L // 10 minutes (AdMob compliant)
        private const val MAX_ADS_PER_PUZZLE_6X6 = 2 // Limited to 2 ads per puzzle
        private const val MAX_ADS_PER_HOUR_6X6 = 6 // Max 6 ads per hour (10 min cooldown allows this)
        private const val MAX_ADS_PER_DAY_6X6 = 30 // Reduced from 40 to be more conservative
        
        // 9×9 Puzzle limits
        // AdMob best practice: 2 ads per 30 minutes = 15 minutes cooldown
        private const val COOLDOWN_9X9_MS = 15 * 60 * 1000L // 15 minutes (AdMob best practice)
        private const val MAX_ADS_PER_PUZZLE_9X9 = 4 // Limited to 4 ads per puzzle
        private const val MAX_ADS_PER_HOUR_9X9 = 4 // Max 4 ads per hour (15 min cooldown allows this)
        private const val MAX_ADS_PER_DAY_9X9 = 40 // Reduced from 50 to be more conservative
        
        // Storage keys
        private const val KEY_LAST_AD_TIME = "last_ad_time"
        private const val KEY_DAILY_AD_COUNT = "daily_ad_count"
        private const val KEY_LAST_AD_DATE = "last_ad_date"
        private const val KEY_HOURLY_AD_COUNT = "hourly_ad_count"
        private const val KEY_HOURLY_AD_TIMESTAMP = "hourly_ad_timestamp"
        private const val KEY_PUZZLE_AD_COUNT = "puzzle_ad_count_"
        private const val KEY_PUZZLE_ID = "current_puzzle_id"
    }
    
    /**
     * Check if an ad can be shown based on puzzle size and all limits
     * @param boardSize The size of the puzzle (6 or 9)
     * @param puzzleId Unique identifier for the current puzzle (to track per-puzzle limits)
     * @return true if ad can be shown, false otherwise
     */
    fun canShowAd(boardSize: Int, puzzleId: String? = null): Boolean {
        val now = System.currentTimeMillis()
        val today = getTodayDateString()
        
        // Get limits based on board size
        val cooldown = if (boardSize == 6) COOLDOWN_6X6_MS else COOLDOWN_9X9_MS
        val maxPerPuzzle = if (boardSize == 6) MAX_ADS_PER_PUZZLE_6X6 else MAX_ADS_PER_PUZZLE_9X9
        val maxPerHour = if (boardSize == 6) MAX_ADS_PER_HOUR_6X6 else MAX_ADS_PER_HOUR_9X9
        val maxPerDay = if (boardSize == 6) MAX_ADS_PER_DAY_6X6 else MAX_ADS_PER_DAY_9X9
        
        // Check if we need to reset daily count (new day)
        val lastAdDate = prefs.getString(KEY_LAST_AD_DATE, null)
        if (lastAdDate != today) {
            // New day - reset daily and hourly counts
            prefs.edit()
                .putInt(KEY_DAILY_AD_COUNT, 0)
                .putString(KEY_LAST_AD_DATE, today)
                .putInt(KEY_HOURLY_AD_COUNT, 0)
                .putLong(KEY_HOURLY_AD_TIMESTAMP, now)
                .apply()
        }
        
        // Check daily limit
        val dailyCount = prefs.getInt(KEY_DAILY_AD_COUNT, 0)
        if (dailyCount >= maxPerDay) {
            return false
        }
        
        // Check hourly limit (rolling 1-hour window)
        val hourlyTimestamp = prefs.getLong(KEY_HOURLY_AD_TIMESTAMP, now)
        val hourlyCount = prefs.getInt(KEY_HOURLY_AD_COUNT, 0)
        val timeSinceHourlyReset = now - hourlyTimestamp
        
        if (timeSinceHourlyReset >= 60 * 60 * 1000L) {
            // More than 1 hour passed - reset hourly count
            prefs.edit()
                .putInt(KEY_HOURLY_AD_COUNT, 0)
                .putLong(KEY_HOURLY_AD_TIMESTAMP, now)
                .apply()
        } else if (hourlyCount >= maxPerHour) {
            return false
        }
        
        // Check per-puzzle limit (if puzzleId provided)
        if (puzzleId != null) {
            val currentPuzzleId = prefs.getString(KEY_PUZZLE_ID, null)
            if (currentPuzzleId != puzzleId) {
                // New puzzle - reset puzzle count
                prefs.edit()
                    .putString(KEY_PUZZLE_ID, puzzleId)
                    .putInt("${KEY_PUZZLE_AD_COUNT}$puzzleId", 0)
                    .apply()
            }
            
            val puzzleCount = prefs.getInt("${KEY_PUZZLE_AD_COUNT}$puzzleId", 0)
            if (puzzleCount >= maxPerPuzzle) {
                return false
            }
        }
        
        // Check cooldown period
        val lastAdTime = prefs.getLong(KEY_LAST_AD_TIME, 0)
        if (lastAdTime > 0) {
            val timeSinceLastAd = now - lastAdTime
            if (timeSinceLastAd < cooldown) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Record that an ad was shown
     * @param boardSize The size of the puzzle (6 or 9)
     * @param puzzleId Unique identifier for the current puzzle
     */
    fun recordAdShown(boardSize: Int, puzzleId: String? = null) {
        val now = System.currentTimeMillis()
        val today = getTodayDateString()
        
        // Update last ad time
        prefs.edit()
            .putLong(KEY_LAST_AD_TIME, now)
            .putString(KEY_LAST_AD_DATE, today)
            .apply()
        
        // Increment daily count
        val currentDailyCount = prefs.getInt(KEY_DAILY_AD_COUNT, 0)
        prefs.edit()
            .putInt(KEY_DAILY_AD_COUNT, currentDailyCount + 1)
            .apply()
        
        // Increment hourly count
        val hourlyTimestamp = prefs.getLong(KEY_HOURLY_AD_TIMESTAMP, now)
        val timeSinceHourlyReset = now - hourlyTimestamp
        
        if (timeSinceHourlyReset >= 60 * 60 * 1000L) {
            // More than 1 hour passed - reset hourly count
            prefs.edit()
                .putInt(KEY_HOURLY_AD_COUNT, 1)
                .putLong(KEY_HOURLY_AD_TIMESTAMP, now)
                .apply()
        } else {
            val currentHourlyCount = prefs.getInt(KEY_HOURLY_AD_COUNT, 0)
            prefs.edit()
                .putInt(KEY_HOURLY_AD_COUNT, currentHourlyCount + 1)
                .apply()
        }
        
        // Increment per-puzzle count (if puzzleId provided)
        if (puzzleId != null) {
            val currentPuzzleCount = prefs.getInt("${KEY_PUZZLE_AD_COUNT}$puzzleId", 0)
            prefs.edit()
                .putString(KEY_PUZZLE_ID, puzzleId)
                .putInt("${KEY_PUZZLE_AD_COUNT}$puzzleId", currentPuzzleCount + 1)
                .apply()
        }
    }
    
    /**
     * Get time remaining until next ad can be shown (in seconds)
     * @param boardSize The size of the puzzle (6 or 9)
     * @return seconds remaining, or 0 if ad can be shown now
     */
    fun getCooldownRemainingSeconds(boardSize: Int): Long {
        val now = System.currentTimeMillis()
        val lastAdTime = prefs.getLong(KEY_LAST_AD_TIME, 0)
        
        if (lastAdTime == 0L) {
            return 0
        }
        
        val cooldown = if (boardSize == 6) COOLDOWN_6X6_MS else COOLDOWN_9X9_MS
        val timeSinceLastAd = now - lastAdTime
        val remaining = cooldown - timeSinceLastAd
        
        return if (remaining > 0) {
            (remaining / 1000) + 1 // Round up to next second
        } else {
            0
        }
    }
    
    /**
     * Get remaining ads available today
     * @param boardSize The size of the puzzle (6 or 9)
     * @return number of ads that can still be watched today
     */
    fun getRemainingAdsToday(boardSize: Int): Int {
        val today = getTodayDateString()
        val lastAdDate = prefs.getString(KEY_LAST_AD_DATE, null)
        val maxPerDay = if (boardSize == 6) MAX_ADS_PER_DAY_6X6 else MAX_ADS_PER_DAY_9X9
        
        // Reset if new day
        if (lastAdDate != today) {
            return maxPerDay
        }
        
        val dailyCount = prefs.getInt(KEY_DAILY_AD_COUNT, 0)
        return (maxPerDay - dailyCount).coerceAtLeast(0)
    }
    
    /**
     * Get a user-friendly message explaining why ad can't be shown
     * @param boardSize The size of the puzzle (6 or 9)
     * @param puzzleId Unique identifier for the current puzzle
     */
    fun getBlockedMessage(boardSize: Int, puzzleId: String? = null): String {
        val today = getTodayDateString()
        val lastAdDate = prefs.getString(KEY_LAST_AD_DATE, null)
        val maxPerDay = if (boardSize == 6) MAX_ADS_PER_DAY_6X6 else MAX_ADS_PER_DAY_9X9
        val maxPerPuzzle = if (boardSize == 6) MAX_ADS_PER_PUZZLE_6X6 else MAX_ADS_PER_PUZZLE_9X9
        val maxPerHour = if (boardSize == 6) MAX_ADS_PER_HOUR_6X6 else MAX_ADS_PER_HOUR_9X9
        
        // Check daily limit first
        if (lastAdDate == today) {
            val dailyCount = prefs.getInt(KEY_DAILY_AD_COUNT, 0)
            if (dailyCount >= maxPerDay) {
                return "You've reached the daily limit of $maxPerDay ads. Please try again tomorrow."
            }
        }
        
        // Check hourly limit
        val now = System.currentTimeMillis()
        val hourlyTimestamp = prefs.getLong(KEY_HOURLY_AD_TIMESTAMP, now)
        val timeSinceHourlyReset = now - hourlyTimestamp
        if (timeSinceHourlyReset < 60 * 60 * 1000L) {
            val hourlyCount = prefs.getInt(KEY_HOURLY_AD_COUNT, 0)
            if (hourlyCount >= maxPerHour) {
                val minutesRemaining = ((60 * 60 * 1000L - timeSinceHourlyReset) / (60 * 1000)).toInt()
                return "You've reached the hourly limit of $maxPerHour ads. Please wait ${minutesRemaining} minutes."
            }
        }
        
        // Check per-puzzle limit
        if (puzzleId != null) {
            val puzzleCount = prefs.getInt("${KEY_PUZZLE_AD_COUNT}$puzzleId", 0)
            if (puzzleCount >= maxPerPuzzle) {
                return "You've used all $maxPerPuzzle hints for this puzzle. Start a new puzzle to get more hints."
            }
        }
        
        // Check cooldown
        val cooldownSeconds = getCooldownRemainingSeconds(boardSize)
        if (cooldownSeconds > 0) {
            val minutes = cooldownSeconds / 60
            val seconds = cooldownSeconds % 60
            if (minutes > 0) {
                return "Please wait ${minutes}m ${seconds}s before watching another ad."
            } else {
                return "Please wait ${seconds}s before watching another ad."
            }
        }
        
        return "Ad is not available right now."
    }
    
    /**
     * Get today's date as a string (YYYY-MM-DD format)
     */
    private fun getTodayDateString(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Month is 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d-%02d-%02d", year, month, day)
    }
    
    /**
     * Reset puzzle-specific tracking (call when starting a new puzzle)
     * @param puzzleId Unique identifier for the new puzzle
     */
    fun resetPuzzleTracking(puzzleId: String) {
        prefs.edit()
            .putString(KEY_PUZZLE_ID, puzzleId)
            .putInt("${KEY_PUZZLE_AD_COUNT}$puzzleId", 0)
            .apply()
    }
}
