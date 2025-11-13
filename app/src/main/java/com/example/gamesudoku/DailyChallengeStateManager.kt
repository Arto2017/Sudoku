package com.example.gamesudoku

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages saving and restoring daily challenge game state
 * Saves state only for the current day's challenge
 * Clears saved state if date changes and game wasn't completed
 */
class DailyChallengeStateManager(private val context: Context) {
    
    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dailyChallengeManager = DailyChallengeManager(context)
    
    data class DailyChallengeState(
        val date: String, // YYYY-MM-DD format (UTC)
        val boardSize: Int,
        val difficulty: String,
        val gameTime: Long, // Milliseconds elapsed
        val gameStartTime: Long, // When game started (for resuming timer)
        val movesCount: Int,
        val hintsUsed: Int,
        val hintsRemaining: Int,
        val maxHints: Int,
        val mistakes: Int,
        val board: List<List<Int>>,
        val fixed: List<List<Boolean>>,
        val solution: List<Int>,
        val selectedNumber: Int,
        val isGameActive: Boolean,
        val savedAt: Long
    )
    
    /**
     * Save daily challenge game state
     * Only saves if game is not completed
     */
    fun saveState(
        date: String,
        boardSize: Int,
        difficulty: DailyChallengeGenerator.Difficulty,
        gameTime: Long,
        gameStartTime: Long,
        movesCount: Int,
        hintsUsed: Int,
        hintsRemaining: Int,
        maxHints: Int,
        mistakes: Int,
        board: Array<IntArray>,
        fixed: Array<BooleanArray>,
        solution: IntArray,
        selectedNumber: Int,
        isGameActive: Boolean,
        isCompleted: Boolean = false // Don't save if completed
    ) {
        // Don't save if game is completed
        if (isCompleted) {
            clearState()
            Log.d("DailyChallenge", "Game completed, clearing saved state")
            return
        }
        
        val state = DailyChallengeState(
            date = date,
            boardSize = boardSize,
            difficulty = difficulty.name,
            gameTime = gameTime,
            gameStartTime = gameStartTime,
            movesCount = movesCount,
            hintsUsed = hintsUsed,
            hintsRemaining = hintsRemaining,
            maxHints = maxHints,
            mistakes = mistakes,
            board = board.map { it.toList() },
            fixed = fixed.map { it.toList() },
            solution = solution.toList(),
            selectedNumber = selectedNumber,
            isGameActive = isGameActive,
            savedAt = System.currentTimeMillis()
        )
        
        sharedPreferences.edit()
            .putString(KEY_STATE, gson.toJson(state))
            .apply()
        
        Log.d("DailyChallenge", "Saved game state for date: $date")
    }
    
    /**
     * Load daily challenge game state
     * Returns null if no saved state, or if saved state is for a different date
     */
    fun loadState(): DailyChallengeState? {
        val json = sharedPreferences.getString(KEY_STATE, null) ?: return null
        
        return try {
            val state = gson.fromJson(json, DailyChallengeState::class.java)
            
            // Check if saved state is for today
            val today = dailyChallengeManager.getTodayDateString()
            
            if (state.date != today) {
                // Date has changed - clear old state
                Log.d("DailyChallenge", "Date changed from ${state.date} to $today, clearing old state")
                
                // Check if the previous day's game was completed (for logging only)
                val previousDayCompleted = dailyChallengeManager.getDailyRecords().containsKey(state.date)
                if (!previousDayCompleted) {
                    Log.d("DailyChallenge", "Previous day's challenge (${state.date}) was not completed, discarding saved state")
                } else {
                    Log.d("DailyChallenge", "Previous day's challenge (${state.date}) was completed")
                }
                
                clearState()
                return null
            }
            
            // Check if today's challenge is already completed
            if (dailyChallengeManager.hasCompletedToday()) {
                Log.d("DailyChallenge", "Today's challenge already completed, clearing saved state")
                clearState()
                return null
            }
            
            Log.d("DailyChallenge", "Loaded game state for date: ${state.date}")
            state
        } catch (ex: JsonSyntaxException) {
            Log.e("DailyChallenge", "Error loading state", ex)
            clearState()
            null
        }
    }
    
    /**
     * Check if there's a saved state for today
     */
    fun hasSavedState(): Boolean {
        val state = loadState()
        return state != null
    }
    
    /**
     * Clear saved state
     */
    fun clearState() {
        sharedPreferences.edit().remove(KEY_STATE).apply()
        Log.d("DailyChallenge", "Cleared saved state")
    }
    
    companion object {
        private const val PREFS_NAME = "daily_challenge_state"
        private const val KEY_STATE = "daily_challenge_game_state"
    }
}

