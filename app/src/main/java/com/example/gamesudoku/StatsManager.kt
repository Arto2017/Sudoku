package com.example.gamesudoku

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

class StatsManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("sudoku_stats", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_GAME_STATS = "game_stats"
        private const val KEY_GAME_RESULTS = "game_results"
        private const val KEY_ACHIEVEMENTS = "achievements"
    }
    
    fun saveGameResult(result: GameResult) {
        val results = getGameResults().toMutableList()
        results.add(result)
        
        // Keep only last 100 results to prevent storage issues
        if (results.size > 100) {
            results.removeAt(0)
        }
        
        val resultsJson = gson.toJson(results)
        sharedPreferences.edit().putString(KEY_GAME_RESULTS, resultsJson).apply()
        
        // Update overall stats
        updateGameStats(result)
    }
    
    fun getGameResults(): List<GameResult> {
        val resultsJson = sharedPreferences.getString(KEY_GAME_RESULTS, "[]")
        val type = object : TypeToken<List<GameResult>>() {}.type
        return try {
            gson.fromJson(resultsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getGameStats(): GameStats {
        val statsJson = sharedPreferences.getString(KEY_GAME_STATS, null)
        return if (statsJson != null) {
            try {
                gson.fromJson(statsJson, GameStats::class.java)
            } catch (e: Exception) {
                GameStats()
            }
        } else {
            GameStats()
        }
    }
    
    private fun updateGameStats(result: GameResult) {
        val results = getGameResults()
        val stats = calculateStats(results)
        val statsJson = gson.toJson(stats)
        sharedPreferences.edit().putString(KEY_GAME_STATS, statsJson).apply()
    }
    
    private fun calculateStats(results: List<GameResult>): GameStats {
        if (results.isEmpty()) return GameStats()
        
        val totalGamesPlayed = results.size
        val totalGamesCompleted = results.count { it.completed }
        val totalPlayTime = results.sumOf { it.timeInSeconds }
        val totalMistakes = results.sumOf { it.mistakes }
        
        val averageTime = if (totalGamesCompleted > 0) {
            results.filter { it.completed }.map { it.timeInSeconds }.average().toFloat()
        } else 0f
        
        val averageMistakes = if (totalGamesPlayed > 0) {
            results.map { it.mistakes }.average().toFloat()
        } else 0f
        
        val bestTime = if (totalGamesCompleted > 0) {
            results.filter { it.completed }.minOfOrNull { it.timeInSeconds } ?: Int.MAX_VALUE
        } else Int.MAX_VALUE
        
        val gamesBySize = mapOf(
            3 to results.count { it.boardSize == 3 },
            6 to results.count { it.boardSize == 6 },
            9 to results.count { it.boardSize == 9 }
        )
        
        val gamesByDifficulty = mapOf(
            SudokuGenerator.Difficulty.EASY to results.count { it.difficulty == SudokuGenerator.Difficulty.EASY },
            SudokuGenerator.Difficulty.MEDIUM to results.count { it.difficulty == SudokuGenerator.Difficulty.MEDIUM },
            SudokuGenerator.Difficulty.HARD to results.count { it.difficulty == SudokuGenerator.Difficulty.HARD },
            SudokuGenerator.Difficulty.EXPERT to results.count { it.difficulty == SudokuGenerator.Difficulty.EXPERT }
        )
        
        val stats = GameStats(
            totalGamesPlayed = totalGamesPlayed,
            totalGamesCompleted = totalGamesCompleted,
            totalPlayTime = totalPlayTime,
            totalMistakes = totalMistakes,
            averageTime = averageTime,
            averageMistakes = averageMistakes,
            bestTime = bestTime,
            gamesBySize = gamesBySize,
            gamesByDifficulty = gamesByDifficulty
        )
        
        // Calculate achievements
        stats.achievements.clear()
        Achievement.values().forEach { achievement ->
            if (achievement.condition(stats)) {
                stats.achievements.add(achievement)
            }
        }
        
        return stats
    }
    
    fun getRecentResults(limit: Int = 10): List<GameResult> {
        return getGameResults().takeLast(limit).reversed()
    }
    
    fun getResultsBySize(boardSize: Int): List<GameResult> {
        return getGameResults().filter { it.boardSize == boardSize }
    }
    
    fun getResultsByDifficulty(difficulty: SudokuGenerator.Difficulty): List<GameResult> {
        return getGameResults().filter { it.difficulty == difficulty }
    }
    
    fun getBestTime(boardSize: Int, difficulty: SudokuGenerator.Difficulty): Int? {
        return getGameResults()
            .filter { it.boardSize == boardSize && it.difficulty == difficulty && it.completed }
            .minOfOrNull { it.timeInSeconds }
    }
    
    fun getAverageTime(boardSize: Int, difficulty: SudokuGenerator.Difficulty): Float {
        val results = getGameResults()
            .filter { it.boardSize == boardSize && it.difficulty == difficulty && it.completed }
        
        return if (results.isNotEmpty()) {
            results.map { it.timeInSeconds }.average().toFloat()
        } else 0f
    }
    
    fun getTotalMistakes(boardSize: Int, difficulty: SudokuGenerator.Difficulty): Int {
        return getGameResults()
            .filter { it.boardSize == boardSize && it.difficulty == difficulty }
            .sumOf { it.mistakes }
    }
    
    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
    }
    
    fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
    
    fun formatTotalTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, remainingSeconds)
            minutes > 0 -> String.format("%d:%02d", minutes, remainingSeconds)
            else -> String.format("%ds", remainingSeconds)
        }
    }
}


