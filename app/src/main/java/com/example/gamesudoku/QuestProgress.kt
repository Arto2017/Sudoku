package com.example.gamesudoku

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class QuestLevel(
    val id: Int,
    val name: String,
    val world: QuestWorld,
    val boardSize: Int,
    val difficulty: SudokuGenerator.Difficulty,
    val gamesRequired: Int,
    val isUnlocked: Boolean = false,
    val isCompleted: Boolean = false,
    val gamesCompleted: Int = 0,
    val bestTime: Long = 0,
    val stars: Int = 0
)

enum class QuestWorld {
    MYSTIC_FOREST,  // 6x6 puzzles
    ANCIENT_TEMPLE  // 9x9 puzzles
}

class QuestProgress(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("quest_progress", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val mysticForestLevels = listOf(
        // Mystic Forest - 6x6 puzzles (12 levels)
        QuestLevel(1, "Forest Path", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.EASY, 2, isUnlocked = true),
        QuestLevel(2, "Mystic Grove", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.EASY, 2),
        QuestLevel(3, "Enchanted Clearing", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.EASY, 2),
        QuestLevel(4, "Whispering Woods", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.MEDIUM, 2),
        QuestLevel(5, "Moonlit Meadow", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.MEDIUM, 2),
        QuestLevel(6, "Crystal Cavern", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.MEDIUM, 2),
        QuestLevel(7, "Ancient Roots", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(8, "Spirit Sanctuary", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(9, "Mystic Gateway", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(10, "Forest Guardian", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(11, "Nature's Heart", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(12, "Ancient Tree", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.HARD, 5) // Boss
    )
    
    private val ancientTempleLevels = listOf(
        // Ancient Temple - 9x9 puzzles (18 levels)
        QuestLevel(13, "Temple Entrance", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.EASY, 2),
        QuestLevel(14, "Sacred Halls", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.EASY, 2),
        QuestLevel(15, "Golden Chamber", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.EASY, 2),
        QuestLevel(16, "Mystic Altar", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.EASY, 2),
        QuestLevel(17, "Ancient Library", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.MEDIUM, 2),
        QuestLevel(18, "Crystal Sanctum", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.MEDIUM, 2),
        QuestLevel(19, "Guardian's Path", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.MEDIUM, 2),
        QuestLevel(20, "Sacred Geometry", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.MEDIUM, 2),
        QuestLevel(21, "Temple Archives", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.MEDIUM, 2),
        QuestLevel(22, "Mystic Forge", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.MEDIUM, 2),
        QuestLevel(23, "Ancient Wisdom", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(24, "Guardian's Trial", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(25, "Sacred Runes", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(26, "Temple Master", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(27, "Ancient Secrets", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(28, "Mystic Convergence", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(29, "Temple Guardian", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.HARD, 3),
        QuestLevel(30, "Sudoku Master Seal", QuestWorld.ANCIENT_TEMPLE, 9, SudokuGenerator.Difficulty.HARD, 5) // Boss
    )
    
    private val allLevels = mysticForestLevels + ancientTempleLevels
    
    fun getLevelsForWorld(world: QuestWorld): List<QuestLevel> {
        return when (world) {
            QuestWorld.MYSTIC_FOREST -> mysticForestLevels
            QuestWorld.ANCIENT_TEMPLE -> ancientTempleLevels
        }.map { level ->
            val savedLevel = getLevelProgress(level.id)
            level.copy(
                isUnlocked = savedLevel.isUnlocked,
                isCompleted = savedLevel.isCompleted,
                gamesCompleted = savedLevel.gamesCompleted,
                bestTime = savedLevel.bestTime,
                stars = savedLevel.stars
            )
        }
    }
    
    fun getCurrentLevelForWorld(world: QuestWorld): QuestLevel? {
        return getLevelsForWorld(world).find { !it.isCompleted }
    }
    
    fun getCompletedLevelsForWorld(world: QuestWorld): Int {
        return getLevelsForWorld(world).count { it.isCompleted }
    }
    
    fun getTotalStarsForWorld(world: QuestWorld): Int {
        return getLevelsForWorld(world).sumOf { it.stars }
    }
    
    fun getProgressPercentageForWorld(world: QuestWorld): Int {
        val levels = getLevelsForWorld(world)
        val totalLevels = levels.size
        val completedLevels = levels.count { it.isCompleted }
        return if (totalLevels > 0) (completedLevels * 100) / totalLevels else 0
    }
    
    fun isWorldUnlocked(world: QuestWorld): Boolean {
        return when (world) {
            QuestWorld.MYSTIC_FOREST -> true // Always unlocked
            QuestWorld.ANCIENT_TEMPLE -> {
                // Unlock Ancient Temple when Mystic Forest is completed
                val mysticForestLevels = getLevelsForWorld(QuestWorld.MYSTIC_FOREST)
                mysticForestLevels.all { it.isCompleted }
            }
        }
    }
    
    fun recordGameCompletion(levelId: Int, timeInSeconds: Long, mistakes: Int) {
        val level = getLevelProgress(levelId)
        val newGamesCompleted = level.gamesCompleted + 1
        val newBestTime = if (level.bestTime == 0L || timeInSeconds < level.bestTime) timeInSeconds else level.bestTime
        
        // Calculate stars based on performance
        val stars = calculateStars(timeInSeconds, mistakes, level.boardSize)
        
        val updatedLevel = level.copy(
            gamesCompleted = newGamesCompleted,
            bestTime = newBestTime,
            stars = maxOf(level.stars, stars),
            isCompleted = newGamesCompleted >= level.gamesRequired
        )
        
        saveLevelProgress(updatedLevel)
        
        // Unlock next level if current level is completed
        if (updatedLevel.isCompleted) {
            unlockNextLevel(levelId)
        }
    }
    
    private fun calculateStars(timeInSeconds: Long, mistakes: Int, boardSize: Int): Int {
        val baseTime = when (boardSize) {
            6 -> 300L
            9 -> 600L
            else -> 600L
        }
        
        val timeScore = when {
            timeInSeconds <= baseTime * 0.5 -> 3
            timeInSeconds <= baseTime * 0.8 -> 2
            timeInSeconds <= baseTime -> 1
            else -> 0
        }
        
        val mistakeScore = when {
            mistakes == 0 -> 2
            mistakes <= 3 -> 1
            else -> 0
        }
        
        return minOf(timeScore + mistakeScore, 3)
    }
    
    private fun unlockNextLevel(completedLevelId: Int) {
        val nextLevelId = completedLevelId + 1
        val nextLevel = allLevels.find { it.id == nextLevelId }
        if (nextLevel != null) {
            val savedNextLevel = getLevelProgress(nextLevelId)
            if (!savedNextLevel.isUnlocked) {
                saveLevelProgress(savedNextLevel.copy(isUnlocked = true))
            }
        }
    }
    
    private fun getLevelProgress(levelId: Int): QuestLevel {
        val json = sharedPreferences.getString("level_$levelId", null)
        return if (json != null) {
            gson.fromJson(json, QuestLevel::class.java)
        } else {
            allLevels.find { it.id == levelId } ?: QuestLevel(levelId, "Unknown", QuestWorld.MYSTIC_FOREST, 6, SudokuGenerator.Difficulty.EASY, 5)
        }
    }
    
    private fun saveLevelProgress(level: QuestLevel) {
        val json = gson.toJson(level)
        sharedPreferences.edit().putString("level_${level.id}", json).apply()
    }

    // Reset all quest progress
    fun resetAllProgress() {
        sharedPreferences.edit().clear().apply()
    }

    // Reset progress for a specific world
    fun resetWorldProgress(world: QuestWorld) {
        val levelsToReset = when (world) {
            QuestWorld.MYSTIC_FOREST -> mysticForestLevels
            QuestWorld.ANCIENT_TEMPLE -> ancientTempleLevels
        }
        levelsToReset.forEach { level ->
            val resetLevel = level.copy(
                isUnlocked = level.id == levelsToReset.first().id, // Only first level of the world unlocked
                isCompleted = false,
                gamesCompleted = 0,
                bestTime = 0,
                stars = 0
            )
            saveLevelProgress(resetLevel)
        }
    }
}
