package com.example.gamesudoku

import org.junit.Test
import org.junit.Assert.*
import java.util.*

/**
 * Comprehensive test suite for Daily Challenge system
 */
class DailyChallengeTest {
    
    @Test
    fun testDeterministicPuzzleGeneration() {
        // Test that same date generates same puzzle
        val date1 = Date(2024, 9, 30) // Sep 30, 2024
        val date2 = Date(2024, 9, 30) // Same date
        
        val puzzle1 = DailyChallengeGenerator.generateDailyPuzzle(date1)
        val puzzle2 = DailyChallengeGenerator.generateDailyPuzzle(date2)
        
        // Should be identical
        assertEquals(puzzle1.date, puzzle2.date)
        assertEquals(puzzle1.puzzleId, puzzle2.puzzleId)
        assertEquals(puzzle1.seed, puzzle2.seed)
        assertEquals(puzzle1.difficulty, puzzle2.difficulty)
        assertArrayEquals(puzzle1.clues, puzzle2.clues)
    }
    
    @Test
    fun testDifferentDatesGenerateDifferentPuzzles() {
        val date1 = Date(2024, 9, 30)
        val date2 = Date(2024, 10, 1)
        
        val puzzle1 = DailyChallengeGenerator.generateDailyPuzzle(date1)
        val puzzle2 = DailyChallengeGenerator.generateDailyPuzzle(date2)
        
        // Should be different
        assertNotEquals(puzzle1.date, puzzle2.date)
        assertNotEquals(puzzle1.puzzleId, puzzle2.puzzleId)
        assertNotEquals(puzzle1.seed, puzzle2.seed)
        assertFalse(Arrays.equals(puzzle1.clues, puzzle2.clues))
    }
    
    @Test
    fun testPuzzleHasSingleSolution() {
        val puzzle = DailyChallengeGenerator.generateDailyPuzzle()
        
        // Convert to 2D array for validation
        val board = Array(9) { IntArray(9) }
        for (i in puzzle.clues.indices) {
            val row = i / 9
            val col = i % 9
            board[row][col] = puzzle.clues[i]
        }
        
        // Count solutions (should be exactly 1)
        val solutionCount = countSolutions(board)
        assertEquals(1, solutionCount)
    }
    
    @Test
    fun testPuzzleDifficultyDistribution() {
        val difficulties = mutableMapOf<DailyChallengeGenerator.Difficulty, Int>()
        
        // Generate puzzles for 30 consecutive days
        for (i in 0 until 30) {
            val date = Date(2024, 9, 1 + i)
            val puzzle = DailyChallengeGenerator.generateDailyPuzzle(date)
            difficulties[puzzle.difficulty] = difficulties.getOrDefault(puzzle.difficulty, 0) + 1
        }
        
        // Should have roughly equal distribution (10 each)
        assertEquals(3, difficulties.size)
        assertTrue(difficulties.values.all { it >= 8 && it <= 12 })
    }
    
    @Test
    fun testPuzzleClueCounts() {
        val puzzle = DailyChallengeGenerator.generateDailyPuzzle()
        val clueCount = puzzle.clues.count { it != 0 }
        
        // Should match expected clue count for difficulty
        val expectedClues = when (puzzle.difficulty) {
            DailyChallengeGenerator.Difficulty.EASY -> 49
            DailyChallengeGenerator.Difficulty.MEDIUM -> 41
            DailyChallengeGenerator.Difficulty.HARD -> 25
        }
        
        assertEquals(expectedClues, clueCount)
    }
    
    @Test
    fun testSolutionVerification() {
        val puzzle = DailyChallengeGenerator.generateDailyPuzzle()
        
        // Generate a valid solution
        val solution = generateValidSolution(puzzle)
        
        // Should verify as correct
        assertTrue(DailyChallengeGenerator.verifyCompletion(puzzle, solution))
        
        // Modify solution to make it incorrect
        solution[0] = if (solution[0] == 1) 2 else 1
        
        // Should verify as incorrect
        assertFalse(DailyChallengeGenerator.verifyCompletion(puzzle, solution))
    }
    
    @Test
    fun testClientHashGeneration() {
        val grid = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
        val userId = "test_user"
        val timestamp = System.currentTimeMillis()
        
        val hash1 = DailyChallengeGenerator.generateClientHash(grid, userId, timestamp)
        val hash2 = DailyChallengeGenerator.generateClientHash(grid, userId, timestamp)
        
        // Same inputs should generate same hash
        assertEquals(hash1, hash2)
        
        // Different inputs should generate different hashes
        val hash3 = DailyChallengeGenerator.generateClientHash(grid, "different_user", timestamp)
        assertNotEquals(hash1, hash3)
    }
    
    @Test
    fun testCursorAIHintGeneration() {
        // Create a test board with some empty cells
        val board = Array(9) { IntArray(9) }
        val fixed = Array(9) { BooleanArray(9) }
        
        // Fill some cells
        board[0][0] = 1
        board[0][1] = 2
        fixed[0][0] = true
        fixed[0][1] = true
        
        // Create mock board implementing CursorBoard
        val mockBoardView = MockCursorBoard()
        mockBoardView.setBoardState(board, fixed)
        mockBoardView.setSelectedCell(0, 2)

        val cursorAI = CursorAI(mockBoardView)
        val hint = cursorAI.suggestHint()

        // Should be able to generate a hint
        assertNotNull(hint)
        val nonNullHint = requireNotNull(hint)  { "Expected hint to be non-null" }
        assertTrue(nonNullHint.cell.row >= 0 && nonNullHint.cell.row < 9)
        assertTrue(nonNullHint.cell.col >= 0 && nonNullHint.cell.col < 9)
        assertTrue(nonNullHint.value >= 1 && nonNullHint.value <= 9)
        assertTrue(nonNullHint.explanation.isNotEmpty())
        assertTrue(nonNullHint.cursorPath.isNotEmpty())
    }
    
    @Test
    fun testAntiCheatValidation() {
        val puzzle = DailyChallengeGenerator.generateDailyPuzzle()
        val completedGrid = generateValidSolution(puzzle)
        
        // Test valid submission
        val validResult = DailyChallengeVerifier.validateCompletionTime(
            puzzle.difficulty,
            300, // 5 minutes
            50   // 50 moves
        )
        assertEquals(DailyChallengeVerifier.ValidationResult.VALID, validResult)
        
        // Test too fast completion
        val tooFastResult = DailyChallengeVerifier.validateCompletionTime(
            puzzle.difficulty,
            10,  // 10 seconds (too fast)
            50
        )
        assertEquals(DailyChallengeVerifier.ValidationResult.INVALID_TIME_TOO_FAST, tooFastResult)
        
        // Test too slow completion
        val tooSlowResult = DailyChallengeVerifier.validateCompletionTime(
            puzzle.difficulty,
            20000, // 5+ hours (too slow)
            50
        )
        assertEquals(DailyChallengeVerifier.ValidationResult.INVALID_TIME_TOO_SLOW, tooSlowResult)
    }
    
    @Test
    fun testSuspiciousPatternDetection() {
        val puzzle = DailyChallengeGenerator.generateDailyPuzzle()
        val completedGrid = generateValidSolution(puzzle)
        
        // Test perfect solution detection
        val patterns = DailyChallengeVerifier.detectSuspiciousPatterns(
            puzzle,
            completedGrid,
            30, // Very fast
            25  // Very few moves
        )
        
        // Should detect suspicious patterns
        assertTrue(patterns.isNotEmpty())
        assertTrue(patterns.contains(DailyChallengeVerifier.SuspiciousPattern.UNREALISTIC_SPEED))
    }
    
    @Test
    fun testPuzzleSignatureGeneration() {
        val puzzle = DailyChallengeGenerator.generateDailyPuzzle()
        
        val signature1 = DailyChallengeVerifier.generatePuzzleSignature(puzzle)
        val signature2 = DailyChallengeVerifier.generatePuzzleSignature(puzzle)
        
        // Same puzzle should generate same signature
        assertEquals(signature1, signature2)
        
        // Should be able to verify signature
        assertTrue(DailyChallengeVerifier.verifyPuzzleSignature(puzzle, signature1))
        
        // Modified puzzle should have different signature
        val modifiedPuzzle = puzzle.copy(clues = puzzle.clues.copyOf())
        modifiedPuzzle.clues[0] = if (modifiedPuzzle.clues[0] == 0) 1 else 0
        
        val modifiedSignature = DailyChallengeVerifier.generatePuzzleSignature(modifiedPuzzle)
        assertNotEquals(signature1, modifiedSignature)
    }
    
    // Helper methods
    
    private fun countSolutions(board: Array<IntArray>): Int {
        val solutions = mutableListOf<Array<IntArray>>()
        val boardCopy = Array(9) { r -> board[r].clone() }
        findSolutions(boardCopy, solutions, 2) // Stop at 2 solutions
        return solutions.size
    }
    
    private fun findSolutions(
        board: Array<IntArray>,
        solutions: MutableList<Array<IntArray>>,
        maxSolutions: Int
    ) {
        if (solutions.size >= maxSolutions) return
        
        // Find cell with fewest candidates
        var bestRow = -1
        var bestCol = -1
        var minPossibilities = 10
        
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                if (board[row][col] == 0) {
                    val possibilities = countValidNumbers(board, row, col)
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
                solutions.add(Array(9) { r -> board[r].clone() })
            }
            return
        }
        
        // Try all valid numbers for the best cell
        for (num in 1..9) {
            if (isValidPlacement(board, bestRow, bestCol, num)) {
                board[bestRow][bestCol] = num
                findSolutions(board, solutions, maxSolutions)
                board[bestRow][bestCol] = 0
            }
        }
    }
    
    private fun countValidNumbers(board: Array<IntArray>, row: Int, col: Int): Int {
        var count = 0
        for (num in 1..9) {
            if (isValidPlacement(board, row, col, num)) {
                count++
            }
        }
        return count
    }
    
    private fun isValidPlacement(board: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        // Check row and column
        for (i in 0 until 9) {
            if (board[row][i] == num || board[i][col] == num) {
                return false
            }
        }
        
        // Check 3x3 box
        val boxRow = (row / 3) * 3
        val boxCol = (col / 3) * 3
        for (r in boxRow until boxRow + 3) {
            for (c in boxCol until boxCol + 3) {
                if (board[r][c] == num) {
                    return false
                }
            }
        }
        return true
    }
    
    private fun generateValidSolution(puzzle: DailyChallengeGenerator.DailyPuzzle): IntArray {
        val board = Array(9) { IntArray(9) }
        
        // Copy puzzle to board
        for (i in puzzle.clues.indices) {
            val row = i / 9
            val col = i % 9
            board[row][col] = puzzle.clues[i]
        }
        
        // Solve the puzzle
        solveSudoku(board)
        
        // Convert back to 1D array
        val solution = IntArray(81)
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                solution[row * 9 + col] = board[row][col]
            }
        }
        
        return solution
    }
    
    private fun solveSudoku(board: Array<IntArray>): Boolean {
        for (row in 0 until 9) {
            for (col in 0 until 9) {
                if (board[row][col] == 0) {
                    for (num in 1..9) {
                        if (isValidPlacement(board, row, col, num)) {
                            board[row][col] = num
                            if (solveSudoku(board)) return true
                            board[row][col] = 0
                        }
                    }
                    return false
                }
            }
        }
        return true
    }
}

/**
 * Mock SudokuBoardView for testing
 */
class MockCursorBoard : CursorBoard {
    private var board: Array<IntArray> = Array(9) { IntArray(9) }
    private var fixed: Array<BooleanArray> = Array(9) { BooleanArray(9) }
    private var selectedRow: Int = 0
    private var selectedCol: Int = 0

    override fun getBoardSize(): Int = board.size

    override fun getComprehensiveHint(row: Int, col: Int): ComprehensiveHintResult {
        if (row !in 0 until board.size || col !in 0 until board.size) {
            return ComprehensiveHintResult(false, 0, "Out of range", HintType.ERROR)
        }
        if (fixed[row][col]) {
            return ComprehensiveHintResult(false, 0, "Cell is fixed", HintType.ERROR)
        }
        if (board[row][col] != 0) {
            return ComprehensiveHintResult(false, 0, "Cell already filled", HintType.ERROR)
        }

        val value = findValidNumber(row, col)
        return if (value != null) {
            ComprehensiveHintResult(true, value, "Try placing $value", HintType.SINGLE_CANDIDATE)
        } else {
            ComprehensiveHintResult(false, 0, "No valid hint", HintType.ERROR)
        }
    }

    override fun applyHint(row: Int, col: Int, value: Int): Boolean {
        board[row][col] = value
        return true
    }

    override fun getSelectedRow(): Int = selectedRow

    override fun getSelectedCol(): Int = selectedCol

    override fun postDelayed(action: () -> Unit, delayMillis: Long) {
        action()
    }

    fun setBoardState(board: Array<IntArray>, fixed: Array<BooleanArray>) {
        this.board = board.map { it.clone() }.toTypedArray()
        this.fixed = fixed.map { it.clone() }.toTypedArray()
    }

    fun setSelectedCell(row: Int, col: Int) {
        selectedRow = row
        selectedCol = col
    }

    private fun findValidNumber(row: Int, col: Int): Int? {
        for (num in 1..board.size) {
            if (isValid(row, col, num)) {
                return num
            }
        }
        return null
    }

    private fun isValid(row: Int, col: Int, num: Int): Boolean {
        for (c in 0 until board.size) {
            if (board[row][c] == num) return false
        }
        for (r in 0 until board.size) {
            if (board[r][col] == num) return false
        }

        val boxSize = 3
        val boxRow = (row / boxSize) * boxSize
        val boxCol = (col / boxSize) * boxSize
        for (r in boxRow until boxRow + boxSize) {
            for (c in boxCol until boxCol + boxSize) {
                if (board[r][c] == num) return false
            }
        }
        return true
    }
}




