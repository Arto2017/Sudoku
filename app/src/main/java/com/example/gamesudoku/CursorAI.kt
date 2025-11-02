package com.example.gamesudoku

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.abs

/**
 * Cursor AI - Advanced hint system with cursor movement and logical suggestions
 */
class CursorAI(private val sudokuBoardView: SudokuBoardView) {
    
    private val boardSize: Int
        get() = sudokuBoardView.getBoardSize()
    
    data class HintResult(
        val cell: Cell,
        val value: Int,
        val explanation: String,
        val cursorPath: List<Cell>,
        val technique: HintTechnique
    )
    
    data class Cell(
        val row: Int,
        val col: Int
    )
    
    enum class HintTechnique {
        SINGLE_CANDIDATE,
        HIDDEN_SINGLE,
        NAKED_PAIR,
        POINTING_PAIR,
        BOX_LINE_REDUCTION,
        X_WING,
        SIMPLE_ELIMINATION
    }
    
    interface OnCursorMoveListener {
        fun onCursorMoveTo(cell: Cell, onComplete: () -> Unit)
        fun onHintSuggestion(hint: HintResult, onAccept: () -> Unit, onCancel: () -> Unit)
    }
    
    private var onCursorMoveListener: OnCursorMoveListener? = null
    private var isAnimating = false
    
    /**
     * Set cursor move listener
     */
    fun setOnCursorMoveListener(listener: OnCursorMoveListener) {
        onCursorMoveListener = listener
    }
    
    /**
     * Suggest hint with cursor movement using comprehensive algorithm
     */
    fun suggestHint(): HintResult? {
        // Use the comprehensive hint system
        val hintResult = sudokuBoardView.getComprehensiveHint(selectedRow, selectedCol)
        
        if (hintResult.success) {
            // Convert to CursorAI format
            return HintResult(
                cell = Cell(selectedRow, selectedCol),
                value = hintResult.value,
                explanation = hintResult.explanation,
                cursorPath = listOf(Cell(selectedRow, selectedCol)),
                technique = mapHintTypeToTechnique(hintResult.type)
            )
        }
        
        return null
    }
    
    /**
     * Map HintType to HintTechnique for compatibility
     */
    private fun mapHintTypeToTechnique(type: HintType): HintTechnique {
        return when (type) {
            HintType.SOLUTION -> HintTechnique.SINGLE_CANDIDATE
            HintType.SINGLE_CANDIDATE -> HintTechnique.SINGLE_CANDIDATE
            HintType.SOLVER_CONFIRMED -> HintTechnique.HIDDEN_SINGLE
            HintType.SMART_HINT -> HintTechnique.SIMPLE_ELIMINATION
            HintType.FALLBACK -> HintTechnique.SIMPLE_ELIMINATION
            HintType.CONFLICT -> HintTechnique.SIMPLE_ELIMINATION
            HintType.ERROR -> HintTechnique.SIMPLE_ELIMINATION
        }
    }
    
    /**
     * Get selected cell coordinates
     */
    private val selectedRow: Int
        get() = sudokuBoardView.getSelectedRow()
    
    private val selectedCol: Int
        get() = sudokuBoardView.getSelectedCol()
    
    /**
     * Move cursor through path with animation
     */
    fun moveCursorToPath(cursorPath: List<Cell>, onComplete: () -> Unit) {
        if (cursorPath.isEmpty()) {
            onComplete()
            return
        }
        
        isAnimating = true
        moveCursorToNext(cursorPath, 0, onComplete)
    }
    
    /**
     * Move cursor to next cell in path
     */
    private fun moveCursorToNext(path: List<Cell>, index: Int, onComplete: () -> Unit) {
        if (index >= path.size) {
            isAnimating = false
            onComplete()
            return
        }
        
        val cell = path[index]
        onCursorMoveListener?.onCursorMoveTo(cell) {
            // Add small delay between moves for better UX
            sudokuBoardView.postDelayed({
                moveCursorToNext(path, index + 1, onComplete)
            }, 300)
        }
    }
    
    /**
     * Apply hint to board using comprehensive system
     */
    fun applyHint(hint: HintResult): Boolean {
        return sudokuBoardView.applyHint(hint.cell.row, hint.cell.col, hint.value)
    }
    
    /**
     * Find single candidate (only one possible number for a cell)
     */
    private fun findSingleCandidate(board: Array<IntArray>, fixed: Array<BooleanArray>): HintResult? {
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] == 0 && !fixed[row][col]) {
                    val candidates = getCandidates(board, row, col)
                    if (candidates.size == 1) {
                        val value = candidates.first()
                        val explanation = "Only possible number for this cell"
                        val cursorPath = listOf(Cell(row, col))
                        
                        return HintResult(
                            cell = Cell(row, col),
                            value = value,
                            explanation = explanation,
                            cursorPath = cursorPath,
                            technique = HintTechnique.SINGLE_CANDIDATE
                        )
                    }
                }
            }
        }
        return null
    }
    
    /**
     * Find hidden single (only one cell in row/column/box that can contain a number)
     */
    private fun findHiddenSingle(board: Array<IntArray>, fixed: Array<BooleanArray>): HintResult? {
        val boxSize = kotlin.math.sqrt(boardSize.toDouble()).toInt()
        
        // Check rows
        for (row in 0 until boardSize) {
            for (num in 1..boardSize) {
                if (!isNumberInRow(board, row, num)) {
                    val possibleCells = mutableListOf<Cell>()
                    for (col in 0 until boardSize) {
                        if (board[row][col] == 0 && !fixed[row][col] && canPlaceNumber(board, row, col, num)) {
                            possibleCells.add(Cell(row, col))
                        }
                    }
                    if (possibleCells.size == 1) {
                        val cell = possibleCells.first()
                        val explanation = "Only cell in row ${row + 1} that can contain $num"
                        val cursorPath = listOf(Cell(row, 0), cell) // Move to row first, then cell
                        
                        return HintResult(
                            cell = cell,
                            value = num,
                            explanation = explanation,
                            cursorPath = cursorPath,
                            technique = HintTechnique.HIDDEN_SINGLE
                        )
                    }
                }
            }
        }
        
        // Check columns
        for (col in 0 until boardSize) {
            for (num in 1..boardSize) {
                if (!isNumberInColumn(board, col, num)) {
                    val possibleCells = mutableListOf<Cell>()
                    for (row in 0 until boardSize) {
                        if (board[row][col] == 0 && !fixed[row][col] && canPlaceNumber(board, row, col, num)) {
                            possibleCells.add(Cell(row, col))
                        }
                    }
                    if (possibleCells.size == 1) {
                        val cell = possibleCells.first()
                        val explanation = "Only cell in column ${col + 1} that can contain $num"
                        val cursorPath = listOf(Cell(0, col), cell) // Move to column first, then cell
                        
                        return HintResult(
                            cell = cell,
                            value = num,
                            explanation = explanation,
                            cursorPath = cursorPath,
                            technique = HintTechnique.HIDDEN_SINGLE
                        )
                    }
                }
            }
        }
        
        // Check boxes
        for (boxRow in 0 until boxSize) {
            for (boxCol in 0 until boxSize) {
                for (num in 1..boardSize) {
                    if (!isNumberInBox(board, boxRow, boxCol, num)) {
                        val possibleCells = mutableListOf<Cell>()
                        for (r in boxRow * boxSize until (boxRow + 1) * boxSize) {
                            for (c in boxCol * boxSize until (boxCol + 1) * boxSize) {
                                if (board[r][c] == 0 && !fixed[r][c] && canPlaceNumber(board, r, c, num)) {
                                    possibleCells.add(Cell(r, c))
                                }
                            }
                        }
                        if (possibleCells.size == 1) {
                            val cell = possibleCells.first()
                            val boxNum = boxRow * boxSize + boxCol + 1
                            val explanation = "Only cell in box $boxNum that can contain $num"
                            val cursorPath = listOf(Cell(boxRow * boxSize, boxCol * boxSize), cell) // Move to box first, then cell
                            
                            return HintResult(
                                cell = cell,
                                value = num,
                                explanation = explanation,
                                cursorPath = cursorPath,
                                technique = HintTechnique.HIDDEN_SINGLE
                            )
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Find naked pair (two cells in same unit with only two candidates)
     */
    private fun findNakedPair(board: Array<IntArray>, fixed: Array<BooleanArray>): HintResult? {
        // Check rows for naked pairs
        for (row in 0 until boardSize) {
            val emptyCells = mutableListOf<Cell>()
            for (col in 0 until boardSize) {
                if (board[row][col] == 0 && !fixed[row][col]) {
                    emptyCells.add(Cell(row, col))
                }
            }
            
            if (emptyCells.size >= 2) {
                for (i in emptyCells.indices) {
                    for (j in i + 1 until emptyCells.size) {
                        val cell1 = emptyCells[i]
                        val cell2 = emptyCells[j]
                        val candidates1 = getCandidates(board, cell1.row, cell1.col)
                        val candidates2 = getCandidates(board, cell2.row, cell2.col)
                        
                        if (candidates1.size == 2 && candidates2.size == 2 && candidates1 == candidates2) {
                            // Found naked pair - suggest first cell
                            val value = candidates1.first()
                            val explanation = "Naked pair: cells can only contain ${candidates1.joinToString(" and ")}"
                            val cursorPath = listOf(cell1, cell2)
                            
                            return HintResult(
                                cell = cell1,
                                value = value,
                                explanation = explanation,
                                cursorPath = cursorPath,
                                technique = HintTechnique.NAKED_PAIR
                            )
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Find pointing pair (number can only be in one row/column within a box)
     */
    private fun findPointingPair(board: Array<IntArray>, fixed: Array<BooleanArray>): HintResult? {
        val boxSize = kotlin.math.sqrt(boardSize.toDouble()).toInt()
        for (boxRow in 0 until boxSize) {
            for (boxCol in 0 until boxSize) {
                for (num in 1..boardSize) {
                    if (!isNumberInBox(board, boxRow, boxCol, num)) {
                        val possibleCells = mutableListOf<Cell>()
                        for (r in boxRow * boxSize until (boxRow + 1) * boxSize) {
                            for (c in boxCol * boxSize until (boxCol + 1) * boxSize) {
                                if (board[r][c] == 0 && !fixed[r][c] && canPlaceNumber(board, r, c, num)) {
                                    possibleCells.add(Cell(r, c))
                                }
                            }
                        }
                        
                        if (possibleCells.size == 2) {
                            val cell1 = possibleCells[0]
                            val cell2 = possibleCells[1]
                            
                            // Check if they're in the same row or column
                            if (cell1.row == cell2.row) {
                                // Same row - can eliminate from other cells in that row
                                val boxNum = boxRow * boxSize + boxCol + 1
                                val explanation = "Pointing pair: $num can only be in this row within box $boxNum"
                                val cursorPath = listOf(Cell(cell1.row, 0), cell1, cell2)
                                
                                return HintResult(
                                    cell = cell1,
                                    value = num,
                                    explanation = explanation,
                                    cursorPath = cursorPath,
                                    technique = HintTechnique.POINTING_PAIR
                                )
                            } else if (cell1.col == cell2.col) {
                                // Same column - can eliminate from other cells in that column
                                val boxNum = boxRow * boxSize + boxCol + 1
                                val explanation = "Pointing pair: $num can only be in this column within box $boxNum"
                                val cursorPath = listOf(Cell(0, cell1.col), cell1, cell2)
                                
                                return HintResult(
                                    cell = cell1,
                                    value = num,
                                    explanation = explanation,
                                    cursorPath = cursorPath,
                                    technique = HintTechnique.POINTING_PAIR
                                )
                            }
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Find box line reduction (number can only be in one box within a row/column)
     */
    private fun findBoxLineReduction(board: Array<IntArray>, fixed: Array<BooleanArray>): HintResult? {
        val boxSize = kotlin.math.sqrt(boardSize.toDouble()).toInt()
        
        // Check rows
        for (row in 0 until boardSize) {
            for (num in 1..boardSize) {
                if (!isNumberInRow(board, row, num)) {
                    val possibleBoxes = mutableSetOf<Int>()
                    for (col in 0 until boardSize) {
                        if (board[row][col] == 0 && !fixed[row][col] && canPlaceNumber(board, row, col, num)) {
                            possibleBoxes.add(col / boxSize)
                        }
                    }
                    if (possibleBoxes.size == 1) {
                        val boxCol = possibleBoxes.first()
                        val boxNum = (row / boxSize) * boxSize + boxCol + 1
                        val explanation = "Box line reduction: $num can only be in box $boxNum within row ${row + 1}"
                        
                        // Find first empty cell in that box and row
                        for (col in boxCol * boxSize until (boxCol + 1) * boxSize) {
                            if (board[row][col] == 0 && !fixed[row][col] && canPlaceNumber(board, row, col, num)) {
                                val cursorPath = listOf(Cell(row, 0), Cell(row, col))
                                
                                return HintResult(
                                    cell = Cell(row, col),
                                    value = num,
                                    explanation = explanation,
                                    cursorPath = cursorPath,
                                    technique = HintTechnique.BOX_LINE_REDUCTION
                                )
                            }
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Find simple elimination (basic logical deduction)
     */
    private fun findSimpleElimination(board: Array<IntArray>, fixed: Array<BooleanArray>): HintResult? {
        // Find cell with fewest candidates
        var bestCell: Cell? = null
        var bestCandidates: Set<Int>? = null
        var minCandidates = boardSize + 1
        
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] == 0 && !fixed[row][col]) {
                    val candidates = getCandidates(board, row, col)
                    if (candidates.size < minCandidates && candidates.isNotEmpty()) {
                        minCandidates = candidates.size
                        bestCell = Cell(row, col)
                        bestCandidates = candidates
                    }
                }
            }
        }
        
        if (bestCell != null && bestCandidates != null && bestCandidates.isNotEmpty()) {
            val value = bestCandidates.first()
            val explanation = "Logical deduction: most constrained cell with ${bestCandidates.size} possibilities"
            val cursorPath = listOf(bestCell)
            
            return HintResult(
                cell = bestCell,
                value = value,
                explanation = explanation,
                cursorPath = cursorPath,
                technique = HintTechnique.SIMPLE_ELIMINATION
            )
        }
        
        return null
    }
    
    /**
     * Get candidates for a cell
     */
    private fun getCandidates(board: Array<IntArray>, row: Int, col: Int): Set<Int> {
        val candidates = mutableSetOf<Int>()
        for (num in 1..boardSize) {
            if (canPlaceNumber(board, row, col, num)) {
                candidates.add(num)
            }
        }
        return candidates
    }
    
    /**
     * Check if number can be placed in cell
     */
    private fun canPlaceNumber(board: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        // Check row
        for (c in 0 until boardSize) {
            if (board[row][c] == num) return false
        }
        
        // Check column
        for (r in 0 until boardSize) {
            if (board[r][col] == num) return false
        }
        
        // Check box
        val boxSize = kotlin.math.sqrt(boardSize.toDouble()).toInt()
        val boxRow = (row / boxSize) * boxSize
        val boxCol = (col / boxSize) * boxSize
        for (r in boxRow until boxRow + boxSize) {
            for (c in boxCol until boxCol + boxSize) {
                if (board[r][c] == num) return false
            }
        }
        
        return true
    }
    
    /**
     * Check if number exists in row
     */
    private fun isNumberInRow(board: Array<IntArray>, row: Int, num: Int): Boolean {
        for (col in 0 until boardSize) {
            if (board[row][col] == num) return true
        }
        return false
    }
    
    /**
     * Check if number exists in column
     */
    private fun isNumberInColumn(board: Array<IntArray>, col: Int, num: Int): Boolean {
        for (row in 0 until boardSize) {
            if (board[row][col] == num) return true
        }
        return false
    }
    
    /**
     * Check if number exists in box
     */
    private fun isNumberInBox(board: Array<IntArray>, boxRow: Int, boxCol: Int, num: Int): Boolean {
        val boxSize = kotlin.math.sqrt(boardSize.toDouble()).toInt()
        for (r in boxRow * boxSize until (boxRow + 1) * boxSize) {
            for (c in boxCol * boxSize until (boxCol + 1) * boxSize) {
                if (board[r][c] == num) return true
            }
        }
        return false
    }
    
    /**
     * Check if cursor is currently animating
     */
    fun isAnimating(): Boolean = isAnimating
}




