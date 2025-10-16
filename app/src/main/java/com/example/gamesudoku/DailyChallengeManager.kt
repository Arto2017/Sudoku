package com.example.gamesudoku

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages daily challenge data, persistence, and statistics
 */
class DailyChallengeManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("daily_challenge", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_DAILY_RECORDS = "daily_records"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_LAST_COMPLETED_DATE = "last_completed_date"
        private const val KEY_BEST_DAILY_TIMES = "best_daily_times"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_TOTAL_COMPLETED = "total_completed"
        private const val KEY_COINS = "coins"
    }
    
    data class DailyRecord(
        val date: String,
        val timeSeconds: Int,
        val moves: Int,
        val rank: Int = 0,
        val difficulty: DailyChallengeGenerator.Difficulty,
        val hintsUsed: Int = 0,
        val completedAt: Long = System.currentTimeMillis()
    )
    
    data class UserStats(
        val userId: String,
        val dailyRecords: Map<String, DailyRecord>,
        val streakDays: Int,
        val bestDailyTimes: Map<String, Int>, // difficulty -> best time
        val totalCompleted: Int,
        val coins: Int
    )
    
    /**
     * Get or create user ID
     */
    fun getUserId(): String {
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId == null) {
            userId = "user_${System.currentTimeMillis()}_${(1000..9999).random()}"
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        return userId
    }
    
    /**
     * Get today's date string in UTC
     */
    fun getTodayDateString(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }
    
    /**
     * Check if user has completed today's challenge
     */
    fun hasCompletedToday(): Boolean {
        val today = getTodayDateString()
        val records = getDailyRecords()
        return records.containsKey(today)
    }
    
    /**
     * Get today's completion record
     */
    fun getTodayRecord(): DailyRecord? {
        val today = getTodayDateString()
        val records = getDailyRecords()
        return records[today]
    }
    
    /**
     * Save daily completion record
     */
    fun saveDailyRecord(record: DailyRecord) {
        val records = getDailyRecords().toMutableMap()
        records[record.date] = record
        saveDailyRecords(records)
        
        // Update streak
        updateStreak(record.date)
        
        // Update best times
        updateBestTime(record.difficulty.name, record.timeSeconds)
        
        // Update total completed
        val totalCompleted = prefs.getInt(KEY_TOTAL_COMPLETED, 0) + 1
        prefs.edit().putInt(KEY_TOTAL_COMPLETED, totalCompleted).apply()
        
        // Award coins
        awardCoins(record.difficulty)
        
        Log.d("DailyChallenge", "Saved record for ${record.date}: ${record.timeSeconds}s, streak: ${getStreakDays()}")
    }
    
    /**
     * Get all daily records
     */
    fun getDailyRecords(): Map<String, DailyRecord> {
        val json = prefs.getString(KEY_DAILY_RECORDS, "{}")
        val type = object : TypeToken<Map<String, DailyRecord>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }
    
    /**
     * Save daily records
     */
    private fun saveDailyRecords(records: Map<String, DailyRecord>) {
        val json = gson.toJson(records)
        prefs.edit().putString(KEY_DAILY_RECORDS, json).apply()
    }
    
    /**
     * Get current streak days
     */
    fun getStreakDays(): Int {
        return prefs.getInt(KEY_STREAK_DAYS, 0)
    }
    
    /**
     * Update streak based on completion date
     */
    private fun updateStreak(completionDate: String) {
        val lastCompletedDate = prefs.getString(KEY_LAST_COMPLETED_DATE, null)
        val currentStreak = getStreakDays()
        
        if (lastCompletedDate == null) {
            // First completion
            prefs.edit()
                .putInt(KEY_STREAK_DAYS, 1)
                .putString(KEY_LAST_COMPLETED_DATE, completionDate)
                .apply()
        } else {
            val yesterday = getYesterdayDateString()
            if (completionDate == yesterday || completionDate == getTodayDateString()) {
                // Consecutive day or same day
                if (completionDate != lastCompletedDate) {
                    prefs.edit()
                        .putInt(KEY_STREAK_DAYS, currentStreak + 1)
                        .putString(KEY_LAST_COMPLETED_DATE, completionDate)
                        .apply()
                }
            } else {
                // Streak broken
                prefs.edit()
                    .putInt(KEY_STREAK_DAYS, 1)
                    .putString(KEY_LAST_COMPLETED_DATE, completionDate)
                    .apply()
            }
        }
    }
    
    /**
     * Get yesterday's date string
     */
    private fun getYesterdayDateString(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(calendar.time)
    }
    
    /**
     * Get best time for difficulty
     */
    fun getBestTime(difficulty: DailyChallengeGenerator.Difficulty): Int {
        val bestTimes = getBestTimes()
        return bestTimes[difficulty.name] ?: Int.MAX_VALUE
    }
    
    /**
     * Get all best times
     */
    fun getBestTimes(): Map<String, Int> {
        val json = prefs.getString(KEY_BEST_DAILY_TIMES, "{}")
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }
    
    /**
     * Update best time for difficulty
     */
    private fun updateBestTime(difficulty: String, timeSeconds: Int) {
        val bestTimes = getBestTimes().toMutableMap()
        val currentBest = bestTimes[difficulty] ?: Int.MAX_VALUE
        if (timeSeconds < currentBest) {
            bestTimes[difficulty] = timeSeconds
            val json = gson.toJson(bestTimes)
            prefs.edit().putString(KEY_BEST_DAILY_TIMES, json).apply()
        }
    }
    
    /**
     * Get total completed count
     */
    fun getTotalCompleted(): Int {
        return prefs.getInt(KEY_TOTAL_COMPLETED, 0)
    }
    
    /**
     * Get current coins
     */
    fun getCoins(): Int {
        return prefs.getInt(KEY_COINS, 0)
    }
    
    /**
     * Award coins for completion
     */
    private fun awardCoins(difficulty: DailyChallengeGenerator.Difficulty) {
        val baseCoins = when (difficulty) {
            DailyChallengeGenerator.Difficulty.EASY -> 10
            DailyChallengeGenerator.Difficulty.MEDIUM -> 15
            DailyChallengeGenerator.Difficulty.HARD -> 20
        }
        
        val streakBonus = getStreakDays() * 2
        val totalCoins = baseCoins + streakBonus
        
        val currentCoins = getCoins()
        prefs.edit().putInt(KEY_COINS, currentCoins + totalCoins).apply()
        
        Log.d("DailyChallenge", "Awarded $totalCoins coins (base: $baseCoins, streak bonus: $streakBonus)")
    }
    
    /**
     * Get user statistics
     */
    fun getUserStats(): UserStats {
        return UserStats(
            userId = getUserId(),
            dailyRecords = getDailyRecords(),
            streakDays = getStreakDays(),
            bestDailyTimes = getBestTimes(),
            totalCompleted = getTotalCompleted(),
            coins = getCoins()
        )
    }
    
    /**
     * Get time until next daily challenge (UTC midnight)
     */
    fun getTimeUntilNextChallenge(): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        return calendar.timeInMillis - System.currentTimeMillis()
    }
    
    /**
     * Format time remaining as HH:MM:SS
     */
    fun formatTimeRemaining(): String {
        val timeRemaining = getTimeUntilNextChallenge()
        val hours = timeRemaining / (1000 * 60 * 60)
        val minutes = (timeRemaining % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (timeRemaining % (1000 * 60)) / 1000
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Reset all data (for testing or user request)
     */
    fun resetAllData() {
        prefs.edit().clear().apply()
        Log.d("DailyChallenge", "Reset all daily challenge data")
    }
    
    /**
     * Get completion rate for current month
     */
    fun getMonthlyCompletionRate(): Float {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        
        val records = getDailyRecords()
        var completedThisMonth = 0
        var totalDaysThisMonth = 0
        
        // Count days in current month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        totalDaysThisMonth = daysInMonth
        
        // Count completed days this month
        for (i in 1..daysInMonth) {
            calendar.set(Calendar.DAY_OF_MONTH, i)
            val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(calendar.time)
            
            if (records.containsKey(dateString)) {
                completedThisMonth++
            }
        }
        
        return if (totalDaysThisMonth > 0) {
            completedThisMonth.toFloat() / totalDaysThisMonth
        } else {
            0f
        }
    }
}




