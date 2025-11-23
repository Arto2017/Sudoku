package com.artashes.sudoku

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class PlayNowStateManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    data class PlayNowState(
        val boardSize: Int,
        val difficulty: String,
        val isQuickPlay: Boolean,
        val secondsElapsed: Int,
        val mistakes: Int,
        val hintsRemaining: Int,
        val hintsUsed: Int,
        val maxHints: Int,
        val board: List<List<Int>>,
        val fixed: List<List<Boolean>>,
        val solution: List<Int>?,
        val selectedNumber: Int,
        val gameStarted: Boolean,
        val savedAt: Long
    )

    fun saveState(
        boardSize: Int,
        difficulty: SudokuGenerator.Difficulty,
        isQuickPlay: Boolean,
        secondsElapsed: Int,
        mistakes: Int,
        hintsRemaining: Int,
        hintsUsed: Int,
        maxHints: Int,
        board: Array<IntArray>,
        fixed: Array<BooleanArray>,
        solution: IntArray?,
        selectedNumber: Int,
        gameStarted: Boolean
    ) {
        val state = PlayNowState(
            boardSize = boardSize,
            difficulty = difficulty.name,
            isQuickPlay = isQuickPlay,
            secondsElapsed = secondsElapsed,
            mistakes = mistakes,
            hintsRemaining = hintsRemaining,
            hintsUsed = hintsUsed,
            maxHints = maxHints,
            board = board.map { it.toList() },
            fixed = fixed.map { it.toList() },
            solution = solution?.toList(),
            selectedNumber = selectedNumber,
            gameStarted = gameStarted,
            savedAt = System.currentTimeMillis()
        )

        sharedPreferences.edit()
            .putString(KEY_STATE, gson.toJson(state))
            .apply()
    }

    fun loadState(): PlayNowState? {
        val json = sharedPreferences.getString(KEY_STATE, null) ?: return null
        return try {
            gson.fromJson(json, PlayNowState::class.java)
        } catch (ex: JsonSyntaxException) {
            clearState()
            null
        }
    }

    fun hasSavedState(): Boolean = sharedPreferences.contains(KEY_STATE)

    fun clearState() {
        sharedPreferences.edit().remove(KEY_STATE).apply()
    }

    companion object {
        private const val PREFS_NAME = "play_now_state"
        private const val KEY_STATE = "state"
    }
}

