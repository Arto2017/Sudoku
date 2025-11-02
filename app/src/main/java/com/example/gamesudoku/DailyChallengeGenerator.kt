package com.example.gamesudoku

import android.util.Log
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * Daily Challenge Generator - Creates deterministic daily puzzles
 * Uses UTC date as seed to ensure same puzzle for all users on same day
 */
object DailyChallengeGenerator {
    
    enum class Difficulty { EASY, MEDIUM, HARD }
    
    data class DailyPuzzle(
        val date: String,           // YYYY-MM-DD format
        val puzzleId: String,       // Same as date
        val seed: String,           // SHA256 hash of date
        val clues: IntArray,        // 81 cells, 0 for empty
        val solution: IntArray,     // 81 cells, complete solution
        val difficulty: Difficulty,
        val minClues: Int,
        val signature: String = ""  // Optional server signature
    )
    
    data class DailySubmission(
        val puzzleId: String,
        val userId: String,
        val completedGrid: IntArray,
        val timeSeconds: Int,
        val movesCount: Int,
        val clientHash: String
    )
    
    data class DailyResult(
        val accepted: Boolean,
        val rankToday: Int = 0,
        val totalCompleted: Int = 0,
        val streakDays: Int = 0,
        val reward: Reward = Reward("coins", 0)
    )
    
    data class Reward(
        val type: String,
        val amount: Int
    )
    
    /**
     * Generate daily puzzle for a specific date
     * Uses deterministic seed based on UTC date
     */
    fun generateDailyPuzzle(date: Date = Date()): DailyPuzzle {
        val dateString = formatDate(date)
        val seed = generateSeed(dateString)
        val difficulty = determineDifficulty(date)
        val targetClues = getTargetCluesForDifficulty(difficulty)
        
        Log.d("DailyChallenge", "Generating puzzle for $dateString with difficulty $difficulty")
        
        // Create seeded random number generator
        val rng = SeededRandom(seed)
        
        // Generate solved grid
        val solvedGrid = generateFullSolution(rng)
        Log.d("DailyChallenge", "Generated solved grid")
        
        // Remove cells to create puzzle
        val puzzle = removeCellsToTargetClues(solvedGrid, rng, targetClues)
        Log.d("DailyChallenge", "Created puzzle with ${puzzle.count { it != 0 }} clues")
        
        // Verify single solution
        val hasSingleSolution = countSolutions(puzzle.to2DArray(), 2, 9) == 1
        if (!hasSingleSolution) {
            Log.w("DailyChallenge", "Puzzle does not have unique solution, regenerating...")
            return generateDailyPuzzle(date) // Retry with different seed
        }
        
        // Convert solved grid to IntArray format (81 cells)
        val solutionArray = IntArray(81)
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                solutionArray[row * 9 + col] = solvedGrid[row][col]
            }
        }
        
        return DailyPuzzle(
            date = dateString,
            puzzleId = dateString,
            seed = seed,
            clues = puzzle,
            solution = solutionArray,
            difficulty = difficulty,
            minClues = targetClues
        )
    }
    
    /**
     * Format date as YYYY-MM-DD in UTC
     */
    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(date)
    }
    
    /**
     * Generate deterministic seed from date string
     */
    private fun generateSeed(dateString: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(dateString.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Determine difficulty based on date (cycles through difficulties)
     */
    fun determineDifficulty(date: Date): Difficulty {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = date
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        
        return when (dayOfYear % 3) {
            0 -> Difficulty.EASY
            1 -> Difficulty.MEDIUM
            else -> Difficulty.HARD
        }
    }
    
    /**
     * Get target number of clues for difficulty
     */
    private fun getTargetCluesForDifficulty(difficulty: Difficulty): Int {
        return when (difficulty) {
            Difficulty.EASY -> 49    // 9x9 Easy: 49 clues
            Difficulty.MEDIUM -> 41  // 9x9 Medium: 41 clues  
            Difficulty.HARD -> 25    // 9x9 Hard: 25 clues
        }
    }
    
    /**
     * Generate a complete solved Sudoku grid using seeded random
     */
    private fun generateFullSolution(rng: SeededRandom): Array<IntArray> {
        val boardSize = 9
        val board = Array(boardSize) { IntArray(boardSize) }
        
        // Start with a random first row
        val firstRow = (1..boardSize).toMutableList()
        firstRow.shuffle(rng.random)
        
        // Fill the first row
        for (col in 0 until boardSize) {
            board[0][col] = firstRow[col]
        }
        
        // Generate the rest using backtracking with seeded random
        solveSudokuSeeded(board, rng, boardSize)
        return board
    }
    
    /**
     * Solve Sudoku using seeded random for number selection
     */
    private fun solveSudokuSeeded(board: Array<IntArray>, rng: SeededRandom, boardSize: Int = 9): Boolean {
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] == 0) {
                    // Get valid numbers and shuffle them using seeded random
                    val validNumbers = mutableListOf<Int>()
                    for (num in 1..boardSize) {
                        if (isValidPlacement(board, row, col, num, boardSize)) {
                            validNumbers.add(num)
                        }
                    }
                    validNumbers.shuffle(rng.random)
                    
                    for (num in validNumbers) {
                        board[row][col] = num
                        if (solveSudokuSeeded(board, rng, boardSize)) return true
                        board[row][col] = 0
                    }
                    return false
                }
            }
        }
        return true
    }
    
    /**
     * Remove cells from solved grid to create puzzle
     */
    private fun removeCellsToTargetClues(
        solvedGrid: Array<IntArray>, 
        rng: SeededRandom, 
        targetClues: Int
    ): IntArray {
        val board = Array(9) { r -> solvedGrid[r].clone() }
        val totalCells = 81
        val targetToRemove = totalCells - targetClues
        var removedCount = 0
        var attempts = 0
        val maxAttempts = totalCells * 3
        
        // Create list of all positions and shuffle using seeded random
        val positions = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                positions.add(Pair(r, c))
            }
        }
        positions.shuffle(rng.random)
        
        while (removedCount < targetToRemove && attempts < maxAttempts) {
            attempts++
            
            val randomPosition = positions[rng.random.nextInt(positions.size)]
            val (row, col) = randomPosition
            
            if (board[row][col] != 0) {
                val temp = board[row][col]
                board[row][col] = 0
                
                // Check if puzzle still has unique solution
                if (countSolutions(board, 2, 9) == 1) {
                    removedCount++
                } else {
                    // Restore the number if it breaks uniqueness
                    board[row][col] = temp
                }
            }
        }
        
        // Convert 2D array to 1D array
        val result = IntArray(81)
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                result[row * 9 + col] = board[row][col]
            }
        }
        
        return result
    }
    
    /**
     * Count solutions to verify uniqueness (stops at 2)
     */
    private fun countSolutions(board: Array<IntArray>, limit: Int = 2, boardSize: Int = 9): Int {
        val solutions = mutableListOf<Array<IntArray>>()
        val boardCopy = Array(boardSize) { r -> board[r].clone() }
        findSolutions(boardCopy, solutions, limit, boardSize)
        return solutions.size
    }
    
    /**
     * Find solutions using backtracking (stops after finding limit solutions)
     */
    private fun findSolutions(
        board: Array<IntArray>, 
        solutions: MutableList<Array<IntArray>>, 
        maxSolutions: Int,
        boardSize: Int = 9
    ) {
        if (solutions.size >= maxSolutions) return
        
        // Find cell with fewest candidates
        var bestRow = -1
        var bestCol = -1
        var minPossibilities = boardSize + 1
        
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] == 0) {
                    val possibilities = countValidNumbers(board, row, col, boardSize)
                    if (possibilities < minPossibilities) {
                        minPossibilities = possibilities
                        bestRow = row
                        bestCol = col
                    }
                }
            }
        }
        
        if (bestRow == -1) {
            // Board is complete
            if (solutions.size < maxSolutions) {
                solutions.add(Array(boardSize) { r -> board[r].clone() })
            }
            return
        }
        
        // Try all valid numbers for the best cell
        for (num in 1..boardSize) {
            if (isValidPlacement(board, bestRow, bestCol, num, boardSize)) {
                board[bestRow][bestCol] = num
                findSolutions(board, solutions, maxSolutions, boardSize)
                board[bestRow][bestCol] = 0
            }
        }
    }
    
    /**
     * Count valid numbers for a cell
     */
    private fun countValidNumbers(board: Array<IntArray>, row: Int, col: Int, boardSize: Int = 9): Int {
        var count = 0
        for (num in 1..boardSize) {
            if (isValidPlacement(board, row, col, num, boardSize)) {
                count++
            }
        }
        return count
    }
    
    /**
     * Check if number placement is valid
     */
    private fun isValidPlacement(board: Array<IntArray>, row: Int, col: Int, num: Int, boardSize: Int = 9): Boolean {
        // Check row and column
        for (i in 0 until boardSize) {
            if (board[row][i] == num || board[i][col] == num) {
                return false
            }
        }
        
        // Check boxes based on Sudoku rules
        when (boardSize) {
            6 -> {
                // 6x6 Sudoku: check 2x3 boxes
                val boxRow = (row / 2) * 2
                val boxCol = (col / 3) * 3
                for (r in boxRow until boxRow + 2) {
                    for (c in boxCol until boxCol + 3) {
                        if (board[r][c] == num) {
                            return false
                        }
                    }
                }
            }
            9 -> {
                // 9x9 Sudoku: check 3x3 boxes
                val boxRow = (row / 3) * 3
                val boxCol = (col / 3) * 3
                for (r in boxRow until boxRow + 3) {
                    for (c in boxCol until boxCol + 3) {
                        if (board[r][c] == num) {
                            return false
                        }
                    }
                }
            }
        }
        return true
    }
    
    /**
     * Convert 1D array to 2D array for compatibility
     */
    private fun IntArray.to2DArray(): Array<IntArray> {
        val result = Array(9) { IntArray(9) }
        for (i in indices) {
            val row = i / 9
            val col = i % 9
            result[row][col] = this[i]
        }
        return result
    }
    
    /**
     * Generate client hash for submission verification
     */
    fun generateClientHash(grid: IntArray, userId: String, timestamp: Long): String {
        val data = "${grid.joinToString(",")}|$userId|$timestamp"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Verify puzzle completion
     */
    fun verifyCompletion(puzzle: DailyPuzzle, completedGrid: IntArray): Boolean {
        // Check if all cells are filled
        if (completedGrid.any { it == 0 }) return false
        
        // Convert to 2D array and verify solution
        val board = completedGrid.to2DArray()
        return isValidSolution(board)
    }
    
    /**
     * Check if board is a valid Sudoku solution
     */
    private fun isValidSolution(board: Array<IntArray>): Boolean {
        // Check rows
        for (row in 0 until 9) {
            val seen = mutableSetOf<Int>()
            for (col in 0 until 9) {
                if (!seen.add(board[row][col])) return false
            }
        }
        
        // Check columns
        for (col in 0 until 9) {
            val seen = mutableSetOf<Int>()
            for (row in 0 until 9) {
                if (!seen.add(board[row][col])) return false
            }
        }
        
        // Check 3x3 boxes
        for (boxRow in 0 until 3) {
            for (boxCol in 0 until 3) {
                val seen = mutableSetOf<Int>()
                for (r in boxRow * 3 until (boxRow + 1) * 3) {
                    for (c in boxCol * 3 until (boxCol + 1) * 3) {
                        if (!seen.add(board[r][c])) return false
                    }
                }
            }
        }
        
        return true
    }
}

/**
 * Seeded random number generator for deterministic puzzle generation
 */
class SeededRandom(seed: String) {
    val random: Random
    
    init {
        // Convert hex seed to long by taking first 16 characters and using hash
        val seedHash = seed.hashCode().toLong()
        random = Random(seedHash)
    }
}
