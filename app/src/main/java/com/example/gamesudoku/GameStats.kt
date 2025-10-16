package com.example.gamesudoku

import java.io.Serializable
import java.util.*

data class GameResult(
    val id: String = UUID.randomUUID().toString(),
    val boardSize: Int,
    val difficulty: SudokuGenerator.Difficulty,
    val timeInSeconds: Int,
    val mistakes: Int,
    val completed: Boolean,
    val date: Date = Date(),
    val performanceRating: Int = 0
) : Serializable

data class GameStats(
    val totalGamesPlayed: Int = 0,
    val totalGamesCompleted: Int = 0,
    val totalPlayTime: Int = 0, // in seconds
    val totalMistakes: Int = 0,
    val averageTime: Float = 0f,
    val averageMistakes: Float = 0f,
    val bestTime: Int = Int.MAX_VALUE,
    val gamesBySize: Map<Int, Int> = mapOf(3 to 0, 6 to 0, 9 to 0),
    val gamesByDifficulty: Map<SudokuGenerator.Difficulty, Int> = mapOf(
        SudokuGenerator.Difficulty.EASY to 0,
        SudokuGenerator.Difficulty.MEDIUM to 0,
        SudokuGenerator.Difficulty.HARD to 0,
        SudokuGenerator.Difficulty.EXPERT to 0
    ),
    val achievements: MutableSet<Achievement> = mutableSetOf()
) : Serializable

enum class Achievement(
    val title: String,
    val description: String,
    val iconResId: Int,
    val condition: (GameStats) -> Boolean
) {
    FIRST_GAME(
        "First Steps",
        "Complete your first Sudoku puzzle",
        R.drawable.ic_puzzle,
        { stats -> stats.totalGamesCompleted >= 1 }
    ),
    
    
    SPEED_DEMON_6X6(
        "Speed Demon (6x6)",
        "Complete a 6x6 puzzle in under 2 minutes",
        R.drawable.ic_timer,
        { stats -> stats.bestTime < 120 }
    ),
    
    SPEED_DEMON_9X9(
        "Speed Demon (9x9)",
        "Complete a 9x9 puzzle in under 5 minutes",
        R.drawable.ic_timer,
        { stats -> stats.bestTime < 300 }
    ),
    
    PERFECTIONIST(
        "Perfectionist",
        "Complete a puzzle with 0 mistakes",
        R.drawable.ic_check,
        { stats -> stats.averageMistakes == 0f }
    ),
    
    DEDICATED_PLAYER(
        "Dedicated Player",
        "Play 50 games",
        R.drawable.ic_puzzle,
        { stats -> stats.totalGamesPlayed >= 50 }
    ),
    
    MASTER_PLAYER(
        "Master Player",
        "Complete 100 games",
        R.drawable.ic_puzzle,
        { stats -> stats.totalGamesCompleted >= 100 }
    ),
    
    ALL_SIZES(
        "Size Explorer",
        "Play both board sizes (6x6, 9x9)",
        R.drawable.ic_sudoku_grid,
        { stats -> stats.gamesBySize.values.all { it > 0 } }
    ),
    
    ALL_DIFFICULTIES(
        "Difficulty Master",
        "Play all difficulty levels (Easy, Medium, Hard, Expert)",
        R.drawable.ic_difficulty,
        { stats -> stats.gamesByDifficulty.values.all { it > 0 } }
    ),
    
    MARATHON_PLAYER(
        "Marathon Player",
        "Play for more than 1 hour total",
        R.drawable.ic_timer,
        { stats -> stats.totalPlayTime >= 3600 }
    ),
    
    BEGINNER_MASTER(
        "Beginner Master",
        "Complete 20 easy puzzles",
        R.drawable.ic_puzzle,
        { stats -> stats.gamesByDifficulty[SudokuGenerator.Difficulty.EASY] ?: 0 >= 20 }
    ),
    
    INTERMEDIATE_MASTER(
        "Intermediate Master",
        "Complete 20 medium puzzles",
        R.drawable.ic_puzzle,
        { stats -> stats.gamesByDifficulty[SudokuGenerator.Difficulty.MEDIUM] ?: 0 >= 20 }
    ),
    
    HARD_MASTER(
        "Hard Master",
        "Complete 20 hard puzzles",
        R.drawable.ic_puzzle,
        { stats -> stats.gamesByDifficulty[SudokuGenerator.Difficulty.HARD] ?: 0 >= 20 }
    ),
    
    EXPERT_MASTER(
        "Expert Master",
        "Complete 20 expert puzzles",
        R.drawable.ic_puzzle,
        { stats -> stats.gamesByDifficulty[SudokuGenerator.Difficulty.EXPERT] ?: 0 >= 20 }
    )
}


