package com.example.gamesudoku

import android.content.Context

/**
 * Stores per-puzzle attempt state such as current mistakes and failed flag.
 * Keys are namespaced by puzzleId, so parallel realms/puzzles do not collide.
 */
class AttemptStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("attempt_state", Context.MODE_PRIVATE)

    fun getMistakes(puzzleId: String): Int {
        return prefs.getInt(keyMistakes(puzzleId), 0)
    }

    fun setMistakes(puzzleId: String, mistakes: Int) {
        prefs.edit().putInt(keyMistakes(puzzleId), mistakes).apply()
    }

    fun incrementMistakes(puzzleId: String): Int {
        val next = getMistakes(puzzleId) + 1
        setMistakes(puzzleId, next)
        return next
    }

    fun isFailed(puzzleId: String): Boolean {
        return prefs.getBoolean(keyFailed(puzzleId), false)
    }

    fun setFailed(puzzleId: String, failed: Boolean) {
        prefs.edit().putBoolean(keyFailed(puzzleId), failed).apply()
    }

    fun clear(puzzleId: String) {
        prefs.edit()
            .remove(keyMistakes(puzzleId))
            .remove(keyFailed(puzzleId))
            .apply()
    }

    fun resetMistakes(puzzleId: String) {
        setMistakes(puzzleId, 0)
    }

    private fun keyMistakes(puzzleId: String) = "${puzzleId}_mistakes"
    private fun keyFailed(puzzleId: String) = "${puzzleId}_failed"
}


