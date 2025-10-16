package com.example.gamesudoku

import android.util.Log

object SudokuGenerator {
    enum class Difficulty { EASY, MEDIUM, HARD, EXPERT }
    
    /**
     * Public method to solve a Sudoku board
     */
    fun solveBoard(board: Array<IntArray>, boardSize: Int): Boolean {
        return solveSudoku(board, boardSize)
    }

    fun generatePuzzle(boardSize: Int, difficulty: Difficulty = Difficulty.MEDIUM): Puzzle {
        Log.d("SudokuGenerator", "Generating puzzle: size=$boardSize, difficulty=$difficulty")
        
        val solution = generateValidSolution(boardSize)
        Log.d("SudokuGenerator", "Generated solution successfully")
        
        val (board, fixed) = removeNumbersFromSolution(solution, boardSize, difficulty)
        Log.d("SudokuGenerator", "Removed numbers. Board has ${board.sumOf { it.count { cell -> cell != 0 } }} filled cells")
        
        return Puzzle(board, fixed)
    }

    private fun generateValidSolution(boardSize: Int): Array<IntArray> {
        val board = Array(boardSize) { IntArray(boardSize) }
        
        // For 6x6, generate a random valid solution
        if (boardSize == 6) {
            return generateRandom6x6Solution()
        }
        
        // For other sizes, use the solver
        solveSudoku(board, boardSize)
        return board
    }

    private fun generateRandom6x6Solution(): Array<IntArray> {
        val board = Array(6) { IntArray(6) }
        
        // Start with a random first row
        val firstRow = (1..6).toMutableList()
        firstRow.shuffle()
        
        // Fill the first row
        for (col in 0 until 6) {
            board[0][col] = firstRow[col]
        }
        
        // Generate the rest of the solution using the solver
        solveSudoku(board, 6)
        return board
    }

    private fun solveSudoku(board: Array<IntArray>, boardSize: Int): Boolean {
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] == 0) {
                    for (num in 1..boardSize) {
                        if (isValidPlacement(board, row, col, num, boardSize)) {
                            board[row][col] = num
                            if (solveSudoku(board, boardSize)) return true
                            board[row][col] = 0
                        }
                    }
                    return false
                }
            }
        }
        return true
    }

    private fun removeNumbersFromSolution(
        solution: Array<IntArray>,
        boardSize: Int,
        difficulty: Difficulty
    ): Pair<Array<IntArray>, Array<BooleanArray>> {
        val board = Array(boardSize) { r -> solution[r].clone() }
        val fixed = Array(boardSize) { BooleanArray(boardSize) }

        val totalCells = boardSize * boardSize
        val cellsToKeep = when (difficulty) {
            Difficulty.EASY -> {
                if (boardSize == 6) {
                    22 // 6x6 Easy: 22 numbers (remove 14)
                } else {
                    49 // 9x9 Easy: 49 numbers (remove 32)
                }
            }
            Difficulty.MEDIUM -> {
                if (boardSize == 6) {
                    16 // 6x6 Medium: 16 numbers (remove 20)
                } else {
                    41 // 9x9 Medium: 41 numbers (remove 40)
                }
            }
            Difficulty.HARD -> {
                if (boardSize == 6) {
                    12 // 6x6 Hard: 12 numbers (remove 24)
                } else {
                    30 // 9x9 Hard: 30 numbers (remove 51)
                }
            }
            Difficulty.EXPERT -> {
                if (boardSize == 6) {
                    10 // 6x6 Expert: 10 numbers (remove 26) - very challenging
                } else {
                    25 // 9x9 Expert: 25 numbers (remove 56) - extremely challenging
                }
            }
        }

        Log.d("SudokuGenerator", "Total cells: $totalCells, keeping: $cellsToKeep")

        // All board sizes now use proper uniqueness checking

        // Create a list of all positions
        val positions = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                positions.add(Pair(r, c))
            }
        }
        
        // Target-based removal: try to remove exactly the number we want
        val targetToRemove = totalCells - cellsToKeep
        var removedCount = 0
        var attempts = 0
        val maxAttempts = totalCells * 3 // Prevent infinite loops
        
        while (removedCount < targetToRemove && attempts < maxAttempts) {
            attempts++
            
            // Try to remove a random number
            val randomPosition = positions.random()
            val (row, col) = randomPosition
            
            if (board[row][col] != 0) {
                val temp = board[row][col]
                board[row][col] = 0
                
                // Check if the puzzle still has a unique solution
                if (hasUniqueSolution(board, boardSize)) {
                    removedCount++
                    Log.d("SudokuGenerator", "Successfully removed number at ($row, $col), removed: $removedCount/$targetToRemove")
                } else {
                    // If not unique, restore the number
                    board[row][col] = temp
                    Log.d("SudokuGenerator", "Restored number at ($row, $col) - would break uniqueness")
                }
            }
        }

        // Mark fixed cells
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                fixed[r][c] = board[r][c] != 0
            }
        }

        val finalFilledCells = board.sumOf { it.count { cell -> cell != 0 } }
        Log.d("SudokuGenerator", "Target: $cellsToKeep, Removed: $removedCount, Final filled: $finalFilledCells")
        Log.d("SudokuGenerator", "Final board has $finalFilledCells filled cells (target was $cellsToKeep)")
        
        // Verify uniqueness one more time
        val isUnique = hasUniqueSolution(board, boardSize)
        Log.d("SudokuGenerator", "Final uniqueness check: $isUnique")
        
        return Pair(board, fixed)
    }



    private fun hasUniqueSolution(board: Array<IntArray>, boardSize: Int): Boolean {
        val solutions = mutableListOf<Array<IntArray>>()
        val boardCopy = Array(boardSize) { r -> board[r].clone() }
        findSolutions(boardCopy, boardSize, solutions, 2) // Only need to find 2 solutions to check uniqueness
        return solutions.size == 1
    }

    private fun findSolutions(
        board: Array<IntArray>, 
        boardSize: Int, 
        solutions: MutableList<Array<IntArray>>, 
        maxSolutions: Int
    ) {
        if (solutions.size >= maxSolutions) return // Stop if we found enough solutions
        
        // Find the cell with the fewest possible values (most constrained)
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
        
        // For 6x6 Sudoku, there are 2x3 boxes
        // For 9x9 Sudoku, there are 3x3 boxes
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

data class Puzzle(val board: Array<IntArray>, val fixed: Array<BooleanArray>)