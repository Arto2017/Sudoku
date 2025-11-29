package com.artashes.sudoku

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
            .remove(keyMaxMistakes(puzzleId))
            .apply()
    }

    fun resetMistakes(puzzleId: String) {
        setMistakes(puzzleId, 0)
    }

    fun getMaxMistakes(puzzleId: String, defaultMax: Int): Int {
        return prefs.getInt(keyMaxMistakes(puzzleId), defaultMax)
    }

    fun setMaxMistakes(puzzleId: String, maxMistakes: Int) {
        prefs.edit().putInt(keyMaxMistakes(puzzleId), maxMistakes).apply()
    }

    fun incrementMaxMistakes(puzzleId: String, defaultMax: Int): Int {
        val currentMax = getMaxMistakes(puzzleId, defaultMax)
        val newMax = currentMax + 1
        setMaxMistakes(puzzleId, newMax)
        return newMax
    }

    fun resetMaxMistakes(puzzleId: String, defaultMax: Int) {
        setMaxMistakes(puzzleId, defaultMax)
    }

    private fun keyMistakes(puzzleId: String) = "${puzzleId}_mistakes"
    private fun keyFailed(puzzleId: String) = "${puzzleId}_failed"
    private fun keyMaxMistakes(puzzleId: String) = "${puzzleId}_max_mistakes"
}


