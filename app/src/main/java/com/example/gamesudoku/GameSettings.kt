package com.artashes.sudoku

import android.content.Context

class GameSettings private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: GameSettings? = null

        fun getInstance(context: Context): GameSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GameSettings(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val PREFS_NAME = "game_settings"
        private const val KEY_EXTENDED_HINTS = "extended_hints_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isExtendedHintsEnabled(): Boolean {
        return prefs.getBoolean(KEY_EXTENDED_HINTS, false)
    }

    fun setExtendedHintsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXTENDED_HINTS, enabled).apply()
    }
}


