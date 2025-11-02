package com.example.gamesudoku

import org.junit.Test
import org.junit.Assert.*

class UniqueSolutionTest {

    @Test
    fun testSudokuGeneratorUniqueSolutions() {
        // Test 6x6 puzzles
        testPuzzleUniqueness(6, SudokuGenerator.Difficulty.EASY)
        testPuzzleUniqueness(6, SudokuGenerator.Difficulty.MEDIUM)
        testPuzzleUniqueness(6, SudokuGenerator.Difficulty.HARD)
        testPuzzleUniqueness(6, SudokuGenerator.Difficulty.EXPERT)
        
        // Test 9x9 puzzles
        testPuzzleUniqueness(9, SudokuGenerator.Difficulty.EASY)
        testPuzzleUniqueness(9, SudokuGenerator.Difficulty.MEDIUM)
        testPuzzleUniqueness(9, SudokuGenerator.Difficulty.HARD)
        testPuzzleUniqueness(9, SudokuGenerator.Difficulty.EXPERT)
    }
    
    private fun testPuzzleUniqueness(boardSize: Int, difficulty: SudokuGenerator.Difficulty) {
        println("Testing $boardSize x $boardSize ${difficulty.name} puzzle uniqueness...")
        
        // Generate multiple puzzles and verify they have unique solutions
        for (i in 1..5) {
            val puzzle = SudokuGenerator.generatePuzzle(boardSize, difficulty)
            
            // Verify the puzzle has a unique solution
            val hasUniqueSolution = hasUniqueSolution(puzzle.board, boardSize)
            assertTrue("Puzzle $i should have unique solution", hasUniqueSolution)
            
            // Verify the puzzle is solvable
            val isSolvable = SudokuGenerator.solveBoard(puzzle.board, boardSize)
            assertTrue("Puzzle $i should be solvable", isSolvable)
            
            println("  Puzzle $i: ${puzzle.board.sumOf { it.count { cell -> cell != 0 } }} clues, unique: $hasUniqueSolution")
        }
    }
    
    private fun hasUniqueSolution(board: Array<IntArray>, boardSize: Int): Boolean {
        val solutions = mutableListOf<Array<IntArray>>()
        val boardCopy = Array(boardSize) { r -> board[r].clone() }
        findSolutions(boardCopy, boardSize, solutions, 2)
        return solutions.size == 1
    }
    
    private fun findSolutions(
        board: Array<IntArray>, 
        boardSize: Int, 
        solutions: MutableList<Array<IntArray>>, 
        maxSolutions: Int
    ) {
        if (solutions.size >= maxSolutions) return
        
        // Find the cell with the fewest possible values
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
                findSolutions(board, boardSize, solutions, maxSolutions)
                board[bestRow][bestCol] = 0
            }
        }
    }
    
    private fun countValidNumbers(board: Array<IntArray>, row: Int, col: Int, boardSize: Int): Int {
        var count = 0
        for (num in 1..boardSize) {
            if (isValidPlacement(board, row, col, num, boardSize)) {
                count++
            }
        }
        return count
    }
    
    private fun isValidPlacement(board: Array<IntArray>, row: Int, col: Int, num: Int, boardSize: Int): Boolean {
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
}




