package com.example.gamesudoku

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.withStyledAttributes
import kotlin.math.min

private const val DEFAULT_HINTS_PER_GAME = 2
private const val EXTENDED_HINTS_PER_GAME = 50

class SudokuBoardView(context: Context, attrs: AttributeSet) : View(context, attrs), CursorBoard {

    interface OnCellSelectedListener {
        fun onCellSelected(row: Int, col: Int, isEditable: Boolean)
    }

    interface OnConflictListener {
        fun onConflictDetected()
    }

    enum class ColorTheme {
        WHITE, BLACK, STIFF_BLUE, LIGHT_YELLOW,
        REALM_ECHOES, REALM_TRIALS, REALM_FLAME, REALM_SHADOWS
    }

    private var boardSize = 9
    private var board: Array<IntArray> = Array(9) { IntArray(9) }
    private var initialBoard: Array<IntArray> = Array(9) { IntArray(9) }
    private var fixed: Array<BooleanArray> = Array(9) { BooleanArray(9) }
    
    // Function to set board size and reinitialize arrays
    fun setBoardSize(size: Int) {
        boardSize = size
        board = Array(size) { IntArray(size) }
        initialBoard = Array(size) { IntArray(size) }
        fixed = Array(size) { BooleanArray(size) }
        resetPuzzle()
        requestLayout() // Request new layout measurement
        invalidate() // Redraw the board
    }
    
    override fun getBoardSize(): Int = boardSize
    
    private var selectedRow = -1
    private var selectedCol = -1
    private var onCellSelectedListener: OnCellSelectedListener? = null
    private var onConflictListener: OnConflictListener? = null
    private var currentTheme = ColorTheme.WHITE

    // Conflict detection and animation
    private var conflictingCells = mutableSetOf<Pair<Int, Int>>()
    private var conflictAnimationProgress = 0f
    private var conflictAnimator: ValueAnimator? = null
    private var lastPlacedNumber = 0
    private var lastPlacedRow = -1
    private var lastPlacedCol = -1
    
    // Success animation
    private var successAnimationProgress = 0f
    private var successAnimator: ValueAnimator? = null
    private var successCells = mutableSetOf<Pair<Int, Int>>()
    
    // Number highlighting - clean implementation
    private var highlightedNumber = 0
    private var highlightedCells = mutableSetOf<Pair<Int, Int>>()
    
    // Hint system data structures
    private var solutionBoard: Array<IntArray>? = null
    private var solutionBoardExplicitlySet: Boolean = false // Track if solution was explicitly set (e.g., Daily Challenge)
    private var candidates: Array<Array<MutableSet<Int>>> = Array(9) { Array(9) { mutableSetOf() } }
    private var isRevealedByHint: Array<BooleanArray> = Array(9) { BooleanArray(9) }
    private var hintsUsed = 0
    private var hintsRemaining = DEFAULT_HINTS_PER_GAME // Configurable limit
    private var maxHintsPerGame = DEFAULT_HINTS_PER_GAME
    private var autoPencilEnabled = false
    private var pencilMarksVisible = false
    private var pencilMode = false // When true, clicking cells adds/removes pencil marks manually
    
    // Track manually added candidates that were removed when placing a number
    // Key: (row, col, value) that was placed, Value: Set of (row, col) cells that had this value as a candidate
    private var removedCandidatesMap = mutableMapOf<Triple<Int, Int, Int>, MutableSet<Pair<Int, Int>>>()

    // Colors with theme support - Pure white background
    private var bgColor = Color.parseColor("#FFFFFF") // Pure white background
    private var boardColor = Color.parseColor("#FFFFFF") // Pure white board
    private var accentColor = Color.parseColor("#8B7355") // Fantasy brown accent
    private var textColor = Color.parseColor("#6B4423") // Enhanced dark brown text
    private var fixedNumberColor = Color.parseColor("#6B4423") // Enhanced dark brown numbers
    private var userNumberColor = Color.parseColor("#8B7355") // Enhanced lighter for user numbers
    private var conflictColor = Color.parseColor("#D2691E") // Fantasy error color

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    
    private val gameSettings = GameSettings.getInstance(context)
    
    // Function to update text size based on cell size
    private fun updateTextSize() {
        // Adjust text size based on board size for better readability
        val textScale = when (boardSize) {
            6 -> 0.7f  // 6x6: larger text for better visibility
            9 -> 0.6f  // 9x9: standard text size
            else -> 0.6f
        }
        paint.textSize = cellSize * textScale
    }

    private var cellSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    init {
        context.withStyledAttributes(attrs, R.styleable.SudokuBoardView) {
            bgColor = getColor(R.styleable.SudokuBoardView_backgroundColor, bgColor)
            accentColor = getColor(R.styleable.SudokuBoardView_accentColor, accentColor)
        }
        resetPuzzle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Ensure the Sudoku board is always square
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        
        // Take the smaller dimension to maintain square aspect ratio
        val size = min(width, height)
        
        // Set both dimensions to the same size (square)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background with pure white color
        val backgroundWhite = Color.parseColor("#FFFFFF") // Pure white background
        canvas.drawColor(backgroundWhite)

        // Calculate cell size based on the measured dimensions
        cellSize = min(width, height) / boardSize.toFloat()
        
        // Update text size to be responsive to cell size
        updateTextSize()
        
        // Calculate board dimensions
        val boardWidth = boardSize * cellSize
        val boardHeight = boardSize * cellSize
        
        // Center the board
        boardLeft = (width - boardWidth) / 2f
        boardTop = (height - boardHeight) / 2f

        // Draw the Sudoku board background (light beige)
        drawBoardBackground(canvas)
        
        // Draw highlights and numbers
        drawHighlights(canvas)
        drawNumberHighlights(canvas)
        drawConflictHighlights(canvas)
        drawSuccessHighlights(canvas)
        drawGridLines(canvas)
        drawNumbers(canvas)
    }

    private fun drawBoardBackground(canvas: Canvas) {
        // Draw pure white background for the Sudoku board
        
        // 1. Draw main board with pure white color
        paint.color = Color.parseColor("#FFFFFF") // Pure white
        paint.alpha = 255 // Full opacity
        canvas.drawRoundRect(
            boardLeft,
            boardTop,
            boardLeft + boardSize * cellSize,
            boardTop + boardSize * cellSize,
            12f, 12f, paint
        )
        
        // 2. Draw subtle border for definition
        paint.color = Color.parseColor("#E0E0E0") // Light gray border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(
            boardLeft,
            boardTop,
            boardLeft + boardSize * cellSize,
            boardTop + boardSize * cellSize,
            12f, 12f, paint
        )
        paint.style = Paint.Style.FILL
    }

    private fun drawHighlights(canvas: Canvas) {
        if (selectedRow == -1 || selectedCol == -1) return
        
        // Use theme accent color for highlights
        val highlightColor = accentColor
        
        // Only draw row/column/box highlights when number highlighting is NOT active
        // But always draw the selected cell highlight
        if (highlightedNumber == 0) {
            // Highlight entire row (light)
            paint.color = Color.argb(20, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))
            canvas.drawRect(
                boardLeft,
                boardTop + selectedRow * cellSize,
                boardLeft + boardSize * cellSize,
                boardTop + (selectedRow + 1) * cellSize,
                paint
            )

            // Highlight entire column (light)
            canvas.drawRect(
                boardLeft + selectedCol * cellSize,
                boardTop,
                boardLeft + (selectedCol + 1) * cellSize,
                boardTop + boardSize * cellSize,
                paint
            )

            // Highlight current box (medium)
            paint.color = Color.argb(40, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))
            val (boxRows, boxCols) = when (boardSize) {
                6 -> Pair(2, 3) // 6x6: 2x3 boxes
                9 -> Pair(3, 3) // 9x9: 3x3 boxes
                else -> Pair(3, 3)
            }
            val boxRow = (selectedRow / boxRows) * boxRows
            val boxCol = (selectedCol / boxCols) * boxCols
            canvas.drawRoundRect(
                boardLeft + boxCol * cellSize,
                boardTop + boxRow * cellSize,
                boardLeft + (boxCol + boxCols) * cellSize,
                boardTop + (boxRow + boxRows) * cellSize,
                12f, 12f, paint
            )
        }

        // Always highlight selected cell (strongest) - even when number highlighting is active
        // This ensures the cell stays visible when adding pencil marks
        paint.color = Color.argb(100, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))
        canvas.drawRoundRect(
            boardLeft + selectedCol * cellSize,
            boardTop + selectedRow * cellSize,
            boardLeft + (selectedCol + 1) * cellSize,
            boardTop + (selectedRow + 1) * cellSize,
            8f, 8f, paint
        )
    }

    private fun drawNumberHighlights(canvas: Canvas) {
        if (highlightedNumber == 0 || highlightedCells.isEmpty()) return

        // Use darker green color for all same numbers
        val highlightColor = Color.parseColor("#439247") // Darker green
        val pulseAlpha = (120 + 60 * Math.sin(System.currentTimeMillis() * 0.0001).toFloat()).toInt()
        
        for ((row, col) in highlightedCells) {
            val cellLeft = boardLeft + col * cellSize
            val cellTop = boardTop + row * cellSize
            val cellRight = cellLeft + cellSize
            val cellBottom = cellTop + cellSize
            
            // Draw highlight with pulsing effect
            paint.color = Color.argb(pulseAlpha, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))
            canvas.drawRoundRect(
                cellLeft + 2f, cellTop + 2f,
                cellRight - 2f, cellBottom - 2f,
                6f, 6f, paint
            )
            
            // Draw border
            paint.color = Color.argb(200, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRoundRect(
                cellLeft + 2f, cellTop + 2f,
                cellRight - 2f, cellBottom - 2f,
                6f, 6f, paint
            )
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawConflictHighlights(canvas: Canvas) {
        pruneConflictingCells()
        if (conflictingCells.isEmpty()) return

        // Create pulsing effect for conflict animation with multiple frequencies
        val pulseAlpha1 = (100 + 80 * Math.sin(conflictAnimationProgress * Math.PI * 3).toFloat()).toInt()
        val pulseAlpha2 = (60 + 40 * Math.sin(conflictAnimationProgress * Math.PI * 6).toFloat()).toInt()
        
        // Draw conflict highlights with enhanced pulsing animation
        for ((row, col) in conflictingCells) {
            val cellLeft = boardLeft + col * cellSize
            val cellTop = boardTop + row * cellSize
            val cellRight = cellLeft + cellSize
            val cellBottom = cellTop + cellSize
            
            if (row !in 0 until boardSize || col !in 0 until boardSize || board[row][col] == 0) {
                continue
            }

            // Draw outer glow effect (larger, more transparent)
            paint.color = Color.argb(pulseAlpha2 / 4, Color.red(conflictColor), Color.green(conflictColor), Color.blue(conflictColor))
            canvas.drawRoundRect(
                cellLeft - 12f, cellTop - 12f,
                cellRight + 12f, cellBottom + 12f,
                16f, 16f, paint
            )
            
            // Draw middle glow effect
            paint.color = Color.argb(pulseAlpha1 / 2, Color.red(conflictColor), Color.green(conflictColor), Color.blue(conflictColor))
            canvas.drawRoundRect(
                cellLeft - 6f, cellTop - 6f,
                cellRight + 6f, cellBottom + 6f,
                12f, 12f, paint
            )
            
            // Draw main conflict highlight
            paint.color = Color.argb(pulseAlpha1, Color.red(conflictColor), Color.green(conflictColor), Color.blue(conflictColor))
            canvas.drawRoundRect(
                cellLeft + 1f, cellTop + 1f,
                cellRight - 1f, cellBottom - 1f,
                8f, 8f, paint
            )
            
            // Draw inner highlight for extra emphasis
            paint.color = Color.argb(pulseAlpha2, Color.red(conflictColor), Color.green(conflictColor), Color.blue(conflictColor))
            canvas.drawRoundRect(
                cellLeft + 4f, cellTop + 4f,
                cellRight - 4f, cellBottom - 4f,
                4f, 4f, paint
            )
        }
        
        // Special highlight for the last placed number (stronger effect with different animation)
        if (lastPlacedRow != -1 && lastPlacedCol != -1 && board.getOrNull(lastPlacedRow)?.getOrNull(lastPlacedCol) != 0) {
            val cellLeft = boardLeft + lastPlacedCol * cellSize
            val cellTop = boardTop + lastPlacedRow * cellSize
            val cellRight = cellLeft + cellSize
            val cellBottom = cellTop + cellSize
            
            // Draw stronger glow for the problematic number with faster pulsing
            val strongPulse = (150 + 100 * Math.sin(conflictAnimationProgress * Math.PI * 8).toFloat()).toInt()
            
            // Outer glow
            paint.color = Color.argb(strongPulse / 3, Color.red(conflictColor), Color.green(conflictColor), Color.blue(conflictColor))
            canvas.drawRoundRect(
                cellLeft - 8f, cellTop - 8f,
                cellRight + 8f, cellBottom + 8f,
                12f, 12f, paint
            )
            
            // Inner glow
            paint.color = Color.argb(strongPulse, Color.red(conflictColor), Color.green(conflictColor), Color.blue(conflictColor))
            canvas.drawRoundRect(
                cellLeft - 2f, cellTop - 2f,
                cellRight + 2f, cellBottom + 2f,
                8f, 8f, paint
            )
        }
    }

    private fun drawSuccessHighlights(canvas: Canvas) {
        if (successCells.isEmpty()) return

        // Create gentle glow effect for success animation
        val glow = (80 + 40 * Math.sin(successAnimationProgress * Math.PI * 2).toFloat()).toInt()
        
        for ((row, col) in successCells) {
            val cellLeft = boardLeft + col * cellSize
            val cellTop = boardTop + row * cellSize
            val cellRight = cellLeft + cellSize
            val cellBottom = cellTop + cellSize
            
            // Draw gentle green glow around successful cells
            val successColor = Color.parseColor("#4CAF50") // Green success color
            paint.color = Color.argb(glow, Color.red(successColor), Color.green(successColor), Color.blue(successColor))
            canvas.drawRoundRect(
                cellLeft - 3f, cellTop - 3f,
                cellRight + 3f, cellBottom + 3f,
                6f, 6f, paint
            )
        }
    }

    private fun drawGridLines(canvas: Canvas) {
        // Beautiful design: very prominent and visible grid lines on light background
        // Box sizes for different Sudoku variants
        val (boxRows, boxCols) = when (boardSize) {
            6 -> Pair(2, 3) // 6x6: 2x3 boxes
            9 -> Pair(3, 3) // 9x9: 3x3 boxes
            else -> Pair(3, 3)
        }
        
        // Use theme colors for grid lines with enhanced visual separation
        val outerBorderColor = textColor // Outer border using theme text color
        val thickGridColor = Color.argb(200, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)) // Thicker grid lines using theme accent
        val thinGridColor = Color.argb(100, Color.red(textColor), Color.green(textColor), Color.blue(textColor)) // Thinner grid lines using theme text color
        
        // Draw thin lines for all cells first - enhanced with 3D effect
        paint.color = thinGridColor // Use theme-based thin grid color
        paint.strokeWidth = 1.5f // Slightly thicker lines for better visibility
        
        // Draw shadow lines first (slightly offset)
        paint.color = Color.parseColor("#5D4037") // Enhanced dark shadow
        paint.alpha = 80
        for (i in 1 until boardSize) {
            // Vertical shadow lines
            val x = boardLeft + i * cellSize + 1.5f
            canvas.drawLine(x, boardTop, x, boardTop + boardSize * cellSize, paint)
            
            // Horizontal shadow lines
            val y = boardTop + i * cellSize + 1.5f
            canvas.drawLine(boardLeft, y, boardLeft + boardSize * cellSize, y, paint)
        }
        
        // Draw main lines
        paint.color = thinGridColor
        paint.alpha = 255
        for (i in 1 until boardSize) {
            // Vertical lines
            val x = boardLeft + i * cellSize
            canvas.drawLine(x, boardTop, x, boardTop + boardSize * cellSize, paint)
            
            // Horizontal lines
            val y = boardTop + i * cellSize
            canvas.drawLine(boardLeft, y, boardLeft + boardSize * cellSize, y, paint)
        }
        
        // Draw thick borders for boxes - much thicker with 3D effect
        paint.strokeWidth = 8f // Thicker lines for block borders
        
        // Draw shadow lines first (slightly offset)
        paint.color = Color.parseColor("#5D4037") // Darker shadow
        paint.alpha = 80
        
        // Draw vertical box borders
        for (i in 0..boardSize step boxCols) {
            val x = boardLeft + i * cellSize + 2f
            canvas.drawLine(x, boardTop, x, boardTop + boardSize * cellSize, paint)
        }
        
        // Draw horizontal box borders
        for (i in 0..boardSize step boxRows) {
            val y = boardTop + i * cellSize + 2f
            canvas.drawLine(boardLeft, y, boardLeft + boardSize * cellSize, y, paint)
        }
        
        // Draw main thick lines
        paint.color = thickGridColor
        paint.alpha = 255
        
        // Draw vertical box borders
        for (i in 0..boardSize step boxCols) {
            val x = boardLeft + i * cellSize
            canvas.drawLine(x, boardTop, x, boardTop + boardSize * cellSize, paint)
        }
        
        // Draw horizontal box borders
        for (i in 0..boardSize step boxRows) {
            val y = boardTop + i * cellSize
            canvas.drawLine(boardLeft, y, boardLeft + boardSize * cellSize, y, paint)
        }
        
        // Draw outer border last (dark brown) - most prominent with enhanced 3D effect
        paint.strokeWidth = 16f // Enhanced thick outer border
        paint.style = Paint.Style.STROKE // Only draw the stroke, not fill
        
        // Draw shadow border first
        paint.color = Color.parseColor("#3E2723") // Enhanced darker shadow
        paint.alpha = 120
        canvas.drawRoundRect(
            boardLeft - 6f, boardTop - 6f, 
            boardLeft + boardSize * cellSize + 6f, 
            boardTop + boardSize * cellSize + 6f, 18f, 18f, paint
        )
        
        // Draw main outer border
        paint.color = outerBorderColor
        paint.alpha = 255
        canvas.drawRoundRect(
            boardLeft - 8f, boardTop - 8f, 
            boardLeft + boardSize * cellSize + 8f, 
            boardTop + boardSize * cellSize + 8f, 20f, 20f, paint
        )
        
        paint.style = Paint.Style.FILL // Reset to fill style
    }

    private fun drawNumbers(canvas: Canvas) {
        // Optimized text size based on board size for better readability, especially for 9x9
        val textSizeMultiplier = when (boardSize) {
            6 -> 0.6f   // Medium text for 6x6
            9 -> 0.5f   // Smaller but still readable text for 9x9
            else -> 0.5f
        }
        paint.textSize = cellSize * textSizeMultiplier
        paint.strokeWidth = 0f

        // Enhanced colors for different types of numbers
        val fixedNumberColor = Color.parseColor("#6B4423") // Enhanced darker for pre-filled numbers
        val userNumberColor = Color.parseColor("#8B7355")  // Enhanced lighter for user numbers

        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] != 0) {
                    val x = boardLeft + col * cellSize + cellSize / 2
                    val y = boardTop + row * cellSize + cellSize / 2 + paint.textSize / 3

                    // Enhanced styling for fixed vs user numbers
                    if (fixed[row][col]) {
                        // Pre-filled numbers: enhanced darker and bold with shadow
                        paint.color = fixedNumberColor
                        paint.isFakeBoldText = true
                        
                        // Draw shadow for fixed numbers
                        paint.color = Color.argb(60, 0, 0, 0)
                        canvas.drawText(board[row][col].toString(), x + 1f, y + 1f, paint)
                        
                        // Draw main text
                        paint.color = fixedNumberColor
                        canvas.drawText(board[row][col].toString(), x, y, paint)
                    } else {
                        // User numbers: enhanced lighter and normal weight
                        paint.color = userNumberColor
                        paint.isFakeBoldText = false
                        
                        // Draw subtle shadow for user numbers
                        paint.color = Color.argb(40, 0, 0, 0)
                        canvas.drawText(board[row][col].toString(), x + 0.5f, y + 0.5f, paint)
                        
                        // Draw main text
                        paint.color = userNumberColor
                        canvas.drawText(board[row][col].toString(), x, y, paint)
                    }
                }
            }
        }
        
        // Draw pencil marks for empty cells if enabled
        if (pencilMarksVisible) {
            drawPencilMarks(canvas)
        }
        
        // Reset paint properties
        paint.isFakeBoldText = false
    }
    
    private fun drawPencilMarks(canvas: Canvas) {
        val pencilMarkSize = cellSize * 0.25f // Bigger text for pencil marks - more readable
        paint.textSize = pencilMarkSize
        paint.color = Color.parseColor("#666666") // Darker gray for better visibility
        paint.isFakeBoldText = false
        
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] == 0 && candidates[row][col].isNotEmpty()) {
                    val cellLeft = boardLeft + col * cellSize
                    val cellTop = boardTop + row * cellSize
                    
                    // Arrange candidates in a 3x3 mini-grid
                    val candidatesList = candidates[row][col].sorted()
                    val gridSize = 3
                    val markSpacing = cellSize / (gridSize + 1)
                    
                    for ((index, candidate) in candidatesList.withIndex()) {
                        if (index >= 9) break // Max 9 candidates
                        
                        val gridRow = index / gridSize
                        val gridCol = index % gridSize
                        
                        val x = cellLeft + (gridCol + 1) * markSpacing
                        val y = cellTop + (gridRow + 1) * markSpacing + pencilMarkSize / 3
                        
                        canvas.drawText(candidate.toString(), x, y, paint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                
                if (x >= boardLeft && x <= boardLeft + boardSize * cellSize &&
                    y >= boardTop && y <= boardTop + boardSize * cellSize) {
                    selectedRow = ((y - boardTop) / cellSize).toInt()
                    selectedCol = ((x - boardLeft) / cellSize).toInt()
                    
                    // Notify listener about cell selection
                    onCellSelectedListener?.onCellSelected(
                        selectedRow, 
                        selectedCol, 
                        !fixed[selectedRow][selectedCol]
                    )
                    
                    // Highlight the number if the clicked cell contains a number
                    val clickedNumber = board[selectedRow][selectedCol]
                    if (clickedNumber != 0) {
                        highlightNumber(clickedNumber)
                    } else {
                        clearNumberHighlight()
                    }
                    
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Calculate cell size based on available space with optimized size-specific adjustments
        val availableWidth = w - 24f  // Reduced padding for more space
        val availableHeight = h - 24f
        
        // Optimized cell size based on board size to maximize 9x9 visibility
        val sizeMultiplier = when (boardSize) {
            6 -> 0.95f  // Almost full size for 6x6
            9 -> 1.0f   // Full size for 9x9 to maximize visibility
            else -> 1.0f
        }
        
        // Calculate optimal cell size that fits within available space
        val maxCellSize = min(availableWidth, availableHeight) / boardSize.toFloat()
        cellSize = maxCellSize * sizeMultiplier
        
        // Ensure the board doesn't exceed available space with better handling for 9x9
        val totalBoardSize = boardSize * cellSize
        if (totalBoardSize > availableWidth || totalBoardSize > availableHeight) {
            // Recalculate with optimized conservative multipliers
            val conservativeMultiplier = when (boardSize) {
                3 -> 0.75f
                6 -> 0.85f
                9 -> 0.95f  // Keep 9x9 as large as possible
                else -> 0.9f
            }
            cellSize = maxCellSize * conservativeMultiplier
        }
        
        // Center the board
        boardLeft = (w - boardSize * cellSize) / 2
        boardTop = (h - boardSize * cellSize) / 2
    }



    fun resetPuzzle(difficulty: SudokuGenerator.Difficulty = SudokuGenerator.Difficulty.EASY) {
        resetPuzzle(difficulty, null)
    }
    
    fun resetPuzzle(difficulty: SudokuGenerator.Difficulty, seed: String?) {
        Log.d("SudokuBoardView", "Resetting puzzle: size=$boardSize, difficulty=$difficulty, seed=${seed ?: "random"}")
        
        val puzzle = SudokuGenerator.generatePuzzle(boardSize, difficulty, seed)
        Log.d("SudokuBoardView", "Generated puzzle with ${puzzle.board.sumOf { it.count { cell -> cell != 0 } }} filled cells")
        
        board = puzzle.board
        initialBoard = puzzle.board.map { it.clone() }.toTypedArray() // Store initial state
        fixed = puzzle.fixed
        selectedRow = -1
        selectedCol = -1
        // Clear any existing conflicts when resetting
        clearConflicts()
        // Clear removed candidates map when resetting puzzle
        removedCandidatesMap.clear()
        // Clear number highlighting when resetting puzzle
        clearNumberHighlight()
        // Clear success animation
        clearSuccessAnimation()
        
        // IMPORTANT: Store the complete solution for hints (same approach as Daily Challenge)
        if (puzzle.solution != null) {
            // Convert 2D solution array to 1D IntArray
            val solutionArray = IntArray(boardSize * boardSize)
            for (row in 0 until boardSize) {
                for (col in 0 until boardSize) {
                    solutionArray[row * boardSize + col] = puzzle.solution[row][col]
                }
            }
            setSolutionBoard(solutionArray)
            Log.d("SudokuBoardView", "Stored complete solution for quest puzzle hints")
        } else {
            Log.w("SudokuBoardView", "No solution provided with puzzle, hints will use solver")
            // Solution not provided, will be generated in initializeHintSystem()
            solutionBoardExplicitlySet = false
        }
        
        // Initialize hint system for new puzzle
        initializeHintSystem()
        
        invalidate()
        
        Log.d("SudokuBoardView", "Puzzle reset complete")
    }

    fun setNumber(number: Int) {
        if (selectedRow != -1 && selectedCol != -1 && !fixed[selectedRow][selectedCol]) {
            // If in pencil mode and cell is empty, add/remove pencil mark instead of placing number
            if (pencilMode && board[selectedRow][selectedCol] == 0 && number != 0) {
                val cellCandidates = candidates[selectedRow][selectedCol]
                if (cellCandidates.contains(number)) {
                    // Remove pencil mark if it exists
                    cellCandidates.remove(number)
                } else {
                    // Add pencil mark if it doesn't exist
                    cellCandidates.add(number)
                }
                // Keep the cell selected - don't clear selection
                // The selection will remain visible to show where the mark was added
                invalidate()
                return
            }
            
            // Store the previous value for conflict detection
            val previousValue = board[selectedRow][selectedCol]
            
            // Don't do anything if the cell already has this value
            if (previousValue == number) {
                return
            }
            
            // When placing a number, remember which cells had that value as a candidate BEFORE removing it
            // so we can restore them later when the number is deleted
            if (number != 0) {
                val cellsWithValue = mutableSetOf<Pair<Int, Int>>()
                val (boxRows, boxCols) = when (boardSize) {
                    6 -> Pair(2, 3)
                    9 -> Pair(3, 3)
                    else -> Pair(3, 3)
                }
                
                // Check same row for cells that have this value as a candidate
                for (c in 0 until boardSize) {
                    if (c != selectedCol && board[selectedRow][c] == 0 && candidates[selectedRow][c].contains(number)) {
                        cellsWithValue.add(Pair(selectedRow, c))
                    }
                }
                // Check same column
                for (r in 0 until boardSize) {
                    if (r != selectedRow && board[r][selectedCol] == 0 && candidates[r][selectedCol].contains(number)) {
                        cellsWithValue.add(Pair(r, selectedCol))
                    }
                }
                // Check same box
                val boxRow = (selectedRow / boxRows) * boxRows
                val boxCol = (selectedCol / boxCols) * boxCols
                for (r in boxRow until boxRow + boxRows) {
                    for (c in boxCol until boxCol + boxCols) {
                        if ((r != selectedRow || c != selectedCol) && board[r][c] == 0 && candidates[r][c].contains(number)) {
                            cellsWithValue.add(Pair(r, c))
                        }
                    }
                }
                
                // Store this information so we can restore later when number is deleted
                if (cellsWithValue.isNotEmpty()) {
                    removedCandidatesMap[Triple(selectedRow, selectedCol, number)] = cellsWithValue
                }
            }
            
            // When removing a number, restore it ONLY to cells where it was manually added before
            if (number == 0 && previousValue != 0) {
                val key = Triple(selectedRow, selectedCol, previousValue)
                val cellsToRestore = removedCandidatesMap.remove(key)
                if (cellsToRestore != null) {
                    // Restore the value only to cells where it was manually added before
                    for ((r, c) in cellsToRestore) {
                        if (board[r][c] == 0 && isValidMove(r, c, previousValue)) {
                            candidates[r][c].add(previousValue)
                        }
                    }
                }
                if (lastPlacedRow == selectedRow && lastPlacedCol == selectedCol) {
                    lastPlacedRow = -1
                    lastPlacedCol = -1
                    lastPlacedNumber = 0
                }
            }
            
            // Set the number
            board[selectedRow][selectedCol] = number
            
            // Clear pencil marks when a number is placed
            if (number != 0) {
                candidates[selectedRow][selectedCol].clear()
                // Ensure we're not keeping stale conflict markers for this cell
                conflictingCells.remove(Pair(selectedRow, selectedCol))
            }
            
            // Update candidates for affected cells
            if (number != 0) {
                // When placing a number, remove it from candidates in related cells
                updateCandidatesAfterPlacement(selectedRow, selectedCol, number)
            }
            
            // Check for conflicts
            if (number != 0) {
                val conflicts = findConflicts(selectedRow, selectedCol, number)
                if (conflicts.isNotEmpty()) {
                    // Start conflict animation
                    startConflictAnimation(conflicts)
                    lastPlacedNumber = number

                    lastPlacedRow = selectedRow
                    lastPlacedCol = selectedCol
                    onConflictListener?.onConflictDetected()
                } else {
                    // Start success animation for correct placement
                    startSuccessAnimation(selectedRow, selectedCol)
                }
            } else {
                if (lastPlacedRow == selectedRow && lastPlacedCol == selectedCol) {
                    lastPlacedRow = -1
                    lastPlacedCol = -1
                    lastPlacedNumber = 0
                }
            }

            recomputeConflicts()
            
            invalidate()
        }
    }

    fun clearSelected() {
        if (selectedRow != -1 && selectedCol != -1 && !fixed[selectedRow][selectedCol]) {
            val previousValue = board[selectedRow][selectedCol]
            
            // When removing a number, restore it ONLY to cells where it was manually added before
            if (previousValue != 0) {
                val key = Triple(selectedRow, selectedCol, previousValue)
                val cellsToRestore = removedCandidatesMap.remove(key)
                if (cellsToRestore != null) {
                    // Restore the value only to cells where it was manually added before
                    for ((r, c) in cellsToRestore) {
                        if (board[r][c] == 0 && isValidMove(r, c, previousValue)) {
                            candidates[r][c].add(previousValue)
                        }
                    }
                }
            }
            
            board[selectedRow][selectedCol] = 0
            
            // Clear pencil marks (candidates) when clearing the cell
            candidates[selectedRow][selectedCol].clear()
            // Remove this cell from conflict tracking if it was highlighted
            val removedCell = Pair(selectedRow, selectedCol)
            if (conflictingCells.remove(removedCell) && conflictingCells.isEmpty()) {
                clearConflicts()
            }
            if (lastPlacedRow == selectedRow && lastPlacedCol == selectedCol) {
                lastPlacedRow = -1
                lastPlacedCol = -1
                lastPlacedNumber = 0
            }

            recomputeConflicts()
            
            invalidate()
        }
    }
    

    fun isValidSolution(): Boolean {
        // Check rows
        for (row in 0 until boardSize) {
            val seen = mutableSetOf<Int>()
            for (col in 0 until boardSize) {
                if (board[row][col] == 0 || !seen.add(board[row][col])) return false
            }
        }

        // Check columns
        for (col in 0 until boardSize) {
            val seen = mutableSetOf<Int>()
            for (row in 0 until boardSize) {
                if (board[row][col] == 0 || !seen.add(board[row][col])) return false
            }
        }

        // Check boxes based on Sudoku rules
        when (boardSize) {
            6 -> {
                // 6x6 Sudoku: check 2x3 boxes
                for (boxRow in 0 until 3) { // 3 rows of boxes
                    for (boxCol in 0 until 2) { // 2 columns of boxes
                        val seen = mutableSetOf<Int>()
                        for (row in boxRow * 2 until (boxRow + 1) * 2) {
                            for (col in boxCol * 3 until (boxCol + 1) * 3) {
                                if (board[row][col] == 0 || !seen.add(board[row][col])) return false
                            }
                        }
                    }
                }
            }
            9 -> {
                // 9x9 Sudoku: check 3x3 boxes
                for (boxRow in 0 until 3) {
                    for (boxCol in 0 until 3) {
                        val seen = mutableSetOf<Int>()
                        for (row in boxRow * 3 until (boxRow + 1) * 3) {
                            for (col in boxCol * 3 until (boxCol + 1) * 3) {
                                if (board[row][col] == 0 || !seen.add(board[row][col])) return false
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    fun getHint(): String {
        if (selectedRow == -1 || selectedCol == -1) {
            return "Select a cell first"
        }
        if (fixed[selectedRow][selectedCol]) {
            return "This is a fixed number"
        }
        if (board[selectedRow][selectedCol] != 0) {
            return "Cell already has a number"
        }
        
        // Simple hint: find a valid number for the selected cell
        for (num in 1..boardSize) {
            if (isValidMove(selectedRow, selectedCol, num)) {
                return "Try placing $num in this cell"
            }
        }
        return "No valid numbers for this cell"
    }
    
    fun getFilledCellCount(): Int {
        var count = 0
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] != 0) {
                    count++
                }
            }
        }
        return count
    }
    
    fun getUserFilledCellCount(): Int {
        var count = 0
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] != 0 && !fixed[row][col]) {
                    count++
                }
            }
        }
        return count
    }
    
    fun getEmptyCellCount(): Int {
        var count = 0
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] == 0) {
                    count++
                }
            }
        }
        return count
    }
    
    fun getOriginalEmptyCellCount(): Int {
        var count = 0
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (fixed[row][col] == false) {
                    count++
                }
            }
        }
        return count
    }

    private fun isValidMove(row: Int, col: Int, num: Int): Boolean {
        // Check row
        for (c in 0 until boardSize) {
            if (c != col && board[row][c] == num) return false
        }

        // Check column
        for (r in 0 until boardSize) {
            if (r != row && board[r][col] == num) return false
        }

        // Check box based on Sudoku rules
        when (boardSize) {
            6 -> {
                // 6x6 Sudoku: check 2x3 boxes
                val boxRow = (row / 2) * 2
                val boxCol = (col / 3) * 3
                for (r in boxRow until boxRow + 2) {
                    for (c in boxCol until boxCol + 3) {
                        if ((r != row || c != col) && board[r][c] == num) return false
                    }
                }
            }
            9 -> {
                // 9x9 Sudoku: check 3x3 boxes
                val boxRow = (row / 3) * 3
                val boxCol = (col / 3) * 3
                for (r in boxRow until boxRow + 3) {
                    for (c in boxCol until boxCol + 3) {
                        if ((r != row || c != col) && board[r][c] == num) return false
                    }
                }
            }
        }
        return true
    }

    fun getBoardState(): Array<IntArray> = board.map { it.clone() }.toTypedArray()
    fun getFixedState(): Array<BooleanArray> = fixed.map { it.clone() }.toTypedArray()

    fun setBoardState(boardState: Array<IntArray>, fixedState: Array<BooleanArray>) {
        board = boardState.map { it.clone() }.toTypedArray()
        fixed = fixedState.map { it.clone() }.toTypedArray()
        // Rebuild initial board using fixed cells to preserve original puzzle state
        initialBoard = Array(boardSize) { row ->
            IntArray(boardSize) { col ->
                if (row < fixed.size && col < fixed[row].size && fixed[row][col]) {
                    board[row][col]
                } else {
                    0
                }
            }
        }
        // Clear conflicts when loading a new board state
        clearConflicts()
        // Clear removed candidates map when loading new board state
        removedCandidatesMap.clear()
        invalidate()
    }

    fun setOnCellSelectedListener(listener: OnCellSelectedListener) {
        onCellSelectedListener = listener
    }

    fun setOnConflictListener(listener: OnConflictListener) {
        onConflictListener = listener
    }


    
    fun getInitialBoardState(): Array<IntArray> = initialBoard.map { it.clone() }.toTypedArray()
    
    fun isBoardComplete(): Boolean {
        // First check if all cells are filled
        for (i in 0 until boardSize) {
            for (j in 0 until boardSize) {
                if (board[i][j] == 0) return false
            }
        }
        
        // Then verify the solution is valid (no conflicts)
        return isSolutionValid()
    }
    
    private fun isSolutionValid(): Boolean {
        // Check each row
        for (row in 0 until boardSize) {
            val numbers = mutableSetOf<Int>()
            for (col in 0 until boardSize) {
                if (board[row][col] != 0) {
                    if (!numbers.add(board[row][col])) {
                        return false // Duplicate found in row
                    }
                }
            }
        }
        
        // Check each column
        for (col in 0 until boardSize) {
            val numbers = mutableSetOf<Int>()
            for (row in 0 until boardSize) {
                if (board[row][col] != 0) {
                    if (!numbers.add(board[row][col])) {
                        return false // Duplicate found in column
                    }
                }
            }
        }
        
        // Check boxes based on Sudoku rules
        when (boardSize) {
            6 -> {
                // 6x6 Sudoku: check 2x3 boxes
                for (boxRow in 0 until 3) { // 3 rows of boxes
                    for (boxCol in 0 until 2) { // 2 columns of boxes
                        val numbers = mutableSetOf<Int>()
                        for (r in boxRow * 2 until boxRow * 2 + 2) {
                            for (c in boxCol * 3 until boxCol * 3 + 3) {
                                if (board[r][c] != 0) {
                                    if (!numbers.add(board[r][c])) {
                                        return false // Duplicate found in box
                                    }
                                }
                            }
                        }
                    }
                }
            }
            9 -> {
                // 9x9 Sudoku: check 3x3 boxes
                for (boxRow in 0 until 3) {
                    for (boxCol in 0 until 3) {
                        val numbers = mutableSetOf<Int>()
                        for (r in boxRow * 3 until boxRow * 3 + 3) {
                            for (c in boxCol * 3 until boxCol * 3 + 3) {
                                if (board[r][c] != 0) {
                                    if (!numbers.add(board[r][c])) {
                                        return false // Duplicate found in box
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
        
        return true
    }

    // Conflict detection methods
    private fun findConflicts(row: Int, col: Int, number: Int): Set<Pair<Int, Int>> {
        val conflicts = mutableSetOf<Pair<Int, Int>>()
        
        // Check row conflicts
        for (c in 0 until boardSize) {
            if (c != col && board[row][c] == number) {
                conflicts.add(Pair(row, c))
            }
        }
        
        // Check column conflicts
        for (r in 0 until boardSize) {
            if (r != row && board[r][col] == number) {
                conflicts.add(Pair(r, col))
            }
        }
        
        // Check box conflicts based on Sudoku rules
        when (boardSize) {
            6 -> {
                // 6x6 Sudoku: check 2x3 boxes
                val boxRow = (row / 2) * 2
                val boxCol = (col / 3) * 3
                for (r in boxRow until boxRow + 2) {
                    for (c in boxCol until boxCol + 3) {
                        if ((r != row || c != col) && board[r][c] == number) {
                            conflicts.add(Pair(r, c))
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
                        if ((r != row || c != col) && board[r][c] == number) {
                            conflicts.add(Pair(r, c))
                        }
                    }
                }
            }
        }
        
        return conflicts
    }
    
    private fun startConflictAnimation(conflicts: Set<Pair<Int, Int>>) {
        // Stop any existing animation
        conflictAnimator?.cancel()
        
        // Set conflicting cells
        conflictingCells.clear()
        conflictingCells.addAll(conflicts)
        
        // Create pulsing animation
        conflictAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000 // 2 seconds for one complete cycle
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                conflictAnimationProgress = animator.animatedValue as Float
                invalidate()
            }
        }
        
        conflictAnimator?.start()
    }
    
    private fun clearConflicts() {
        conflictAnimator?.cancel()
        conflictingCells.clear()
        lastPlacedRow = -1
        lastPlacedCol = -1
        lastPlacedNumber = 0
        conflictAnimationProgress = 0f
        invalidate()
    }

    private fun pruneConflictingCells() {
        if (conflictingCells.isEmpty()) {
            return
        }

        val iterator = conflictingCells.iterator()
        while (iterator.hasNext()) {
            val (row, col) = iterator.next()
            if (row !in 0 until boardSize || col !in 0 until boardSize || board[row][col] == 0) {
                iterator.remove()
            }
        }

        if (conflictingCells.isEmpty()) {
            clearConflicts()
        }
    }

    private fun startSuccessAnimation(row: Int, col: Int) {
        // Stop any existing success animation
        successAnimator?.cancel()
        
        // Set success cell
        successCells.clear()
        successCells.add(Pair(row, col))
        
        // Create pulsing glow animation
        successAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000 // 1 second for one complete cycle
            repeatCount = 1 // Only play once
            
            addUpdateListener { animator ->
                successAnimationProgress = animator.animatedValue as Float
                invalidate()
            }
        }
        
        successAnimator?.start()
    }

    private fun clearSuccessAnimation() {
        successAnimator?.cancel()
        successCells.clear()
        successAnimationProgress = 0f
    }
    
    fun hasConflicts(): Boolean {
        return conflictingCells.isNotEmpty()
    }
    
    fun getConflictingCells(): Set<Pair<Int, Int>> {
        return conflictingCells.toSet()
    }
    
    // Method to manually check for conflicts in the entire board (for debugging/testing)
    fun checkAllConflicts() {
        val allConflicts = mutableSetOf<Pair<Int, Int>>()
        
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] != 0) {
                    val conflicts = findConflicts(row, col, board[row][col])
                    allConflicts.addAll(conflicts)
                }
            }
        }
        
        if (allConflicts.isNotEmpty()) {
            startConflictAnimation(allConflicts)
        } else {
            clearConflicts()
        }
    }
    
    /**
     * Recompute conflict state for the entire board to ensure highlights match actual duplicates.
     */
    private fun recomputeConflicts() {
        pruneConflictingCells()

        val newConflicts = mutableSetOf<Pair<Int, Int>>()

        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                val value = board[row][col]
                if (value != 0) {
                    val conflicts = findConflicts(row, col, value)
                    if (conflicts.isNotEmpty()) {
                        newConflicts.add(Pair(row, col))
                        newConflicts.addAll(conflicts)
                    }
                }
            }
        }

        if (newConflicts.isEmpty()) {
            if (conflictingCells.isNotEmpty()) {
                clearConflicts()
            }
            return
        }

        if (newConflicts != conflictingCells) {
            startConflictAnimation(newConflicts)
        } else if (conflictAnimator?.isRunning != true) {
            startConflictAnimation(newConflicts)
        }
    }

    // Number highlighting methods - clean implementation
    fun highlightNumber(number: Int) {
        if (successCells.isNotEmpty()) {
            clearSuccessAnimation()
        }
        highlightedNumber = number
        highlightedCells.clear()
        
        // Find all cells containing this number
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] == number) {
                    highlightedCells.add(Pair(row, col))
                }
            }
        }
        
        invalidate()
    }
    
    fun clearNumberHighlight() {
        highlightedNumber = 0
        highlightedCells.clear()
        invalidate()
    }

    fun setTheme(theme: ColorTheme) {
        currentTheme = theme
        updateColors()
        invalidate()
    }

    fun setRealmTheme(realmId: String) {
        val theme = when (realmId) {
            "echoes" -> ColorTheme.REALM_ECHOES
            "trials" -> ColorTheme.REALM_TRIALS
            "flame" -> ColorTheme.REALM_FLAME
            "shadows" -> ColorTheme.REALM_SHADOWS
            else -> ColorTheme.WHITE
        }
        setTheme(theme)
    }

    private fun updateColors() {
        when (currentTheme) {
            ColorTheme.WHITE -> {
                bgColor = Color.parseColor("#FFFFFF") // Pure white background
                boardColor = Color.parseColor("#FFFFFF") // Pure white board
                accentColor = Color.parseColor("#8B7355") // Fantasy brown accent
                textColor = Color.parseColor("#6B4423") // Dark brown text
                fixedNumberColor = Color.parseColor("#6B4423") // Dark brown numbers
                userNumberColor = Color.parseColor("#8B7355") // Lighter brown numbers
                conflictColor = Color.parseColor("#D2691E") // Fantasy error color
            }
            ColorTheme.BLACK -> {
                bgColor = Color.parseColor("#FFFFFF") // Pure white background
                boardColor = Color.parseColor("#FFFFFF") // Pure white board
                accentColor = Color.parseColor("#8B7355") // Fantasy brown accent
                textColor = Color.parseColor("#6B4423") // Dark brown text
                fixedNumberColor = Color.parseColor("#6B4423") // Dark brown numbers
                userNumberColor = Color.parseColor("#8B7355") // Lighter brown numbers
                conflictColor = Color.parseColor("#D2691E") // Fantasy error color
            }
            ColorTheme.STIFF_BLUE -> {
                bgColor = Color.parseColor("#FFFFFF") // Pure white background
                boardColor = Color.parseColor("#FFFFFF") // Pure white board
                accentColor = Color.parseColor("#8B7355") // Fantasy brown accent
                textColor = Color.parseColor("#6B4423") // Dark brown text
                fixedNumberColor = Color.parseColor("#6B4423") // Dark brown numbers
                userNumberColor = Color.parseColor("#8B7355") // Lighter brown numbers
                conflictColor = Color.parseColor("#D2691E") // Fantasy error color
            }
            ColorTheme.LIGHT_YELLOW -> {
                bgColor = Color.parseColor("#FFFFFF") // Pure white background
                boardColor = Color.parseColor("#FFFFFF") // Pure white board
                accentColor = Color.parseColor("#8B7355") // Fantasy brown accent
                textColor = Color.parseColor("#6B4423") // Dark brown text
                fixedNumberColor = Color.parseColor("#6B4423") // Dark brown numbers
                userNumberColor = Color.parseColor("#8B7355") // Lighter brown numbers
                conflictColor = Color.parseColor("#D2691E") // Fantasy error color
            }
            ColorTheme.REALM_ECHOES -> {
                // Pure white and bright
                bgColor = Color.parseColor("#FFFFFF") // Pure white background
                boardColor = Color.parseColor("#FFFFFF") // Pure white board
                accentColor = Color.parseColor("#8B7355") // Gentle brown
                textColor = Color.parseColor("#6B4423") // Dark brown
                fixedNumberColor = Color.parseColor("#6B4423") // Dark brown
                userNumberColor = Color.parseColor("#8B7355") // Lighter brown
                conflictColor = Color.parseColor("#D2691E") // Warm error
            }
            ColorTheme.REALM_TRIALS -> {
                // Pure white and bright
                bgColor = Color.parseColor("#FFFFFF") // Pure white background
                boardColor = Color.parseColor("#FFFFFF") // Pure white board
                accentColor = Color.parseColor("#8B7355") // Fantasy brown
                textColor = Color.parseColor("#6B4423") // Dark brown
                fixedNumberColor = Color.parseColor("#6B4423") // Dark brown
                userNumberColor = Color.parseColor("#8B7355") // Lighter brown
                conflictColor = Color.parseColor("#D2691E") // Fantasy error
            }
            ColorTheme.REALM_FLAME -> {
                // Pure white and bright
                bgColor = Color.parseColor("#FFFFFF") // Pure white background
                boardColor = Color.parseColor("#FFFFFF") // Pure white board
                accentColor = Color.parseColor("#8B7355") // Fantasy brown
                textColor = Color.parseColor("#6B4423") // Dark brown
                fixedNumberColor = Color.parseColor("#6B4423") // Dark brown
                userNumberColor = Color.parseColor("#8B7355") // Lighter brown
                conflictColor = Color.parseColor("#D2691E") // Fantasy error
            }
            ColorTheme.REALM_SHADOWS -> {
                // Pure white and bright
                bgColor = Color.parseColor("#FFFFFF") // Pure white background
                boardColor = Color.parseColor("#FFFFFF") // Pure white board
                accentColor = Color.parseColor("#8B7355") // Fantasy brown
                textColor = Color.parseColor("#6B4423") // Dark brown
                fixedNumberColor = Color.parseColor("#6B4423") // Dark brown
                userNumberColor = Color.parseColor("#8B7355") // Lighter brown
                conflictColor = Color.parseColor("#D2691E") // Fantasy error
            }
        }
    }
    
    // ===== HINT SYSTEM METHODS =====
    
    /**
     * Initialize hint system - compute solution and initial candidates
     */
    fun initializeHintSystem() {
        // Generate solution if not present and not explicitly set
        if (solutionBoard == null) {
            solutionBoard = generateSolution()
            solutionBoardExplicitlySet = false // Auto-generated, not explicitly set
        }
        
        // Clear previous hint data
        hintsUsed = 0
        maxHintsPerGame = if (gameSettings.isExtendedHintsEnabled()) {
            EXTENDED_HINTS_PER_GAME
        } else {
            DEFAULT_HINTS_PER_GAME
        }
        hintsRemaining = maxHintsPerGame // Reset hints remaining based on settings
        isRevealedByHint = Array(boardSize) { BooleanArray(boardSize) }
        
        // Clear all candidates - player will add pencil marks manually
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                candidates[row][col].clear()
            }
        }
    }
    
    /**
     * Set the complete solution array (81 numbers for 9x9)
     * This stores the full solved grid so hints can read directly from it
     * Format: 1D array converted to 2D array[row][col] for easy access
     * Example: For position (x=5, y=6), we read solutionBoard[5][6]
     */
    fun setSolutionBoard(solution: IntArray) {
        if (solution.size != boardSize * boardSize) {
            Log.e("SudokuBoardView", "Solution size ${solution.size} doesn't match board size ${boardSize * boardSize}")
            return
        }
        
        // Create 2D array to store complete solution: solutionBoard[row][col]
        solutionBoard = Array(boardSize) { IntArray(boardSize) }
        
        // Convert 1D array to 2D array
        // Index calculation: for position (row, col), index = row * boardSize + col
        for (i in solution.indices) {
            val row = i / boardSize  // Row = index / boardSize
            val col = i % boardSize  // Col = index % boardSize
            solutionBoard!![row][col] = solution[i]
        }
        
        solutionBoardExplicitlySet = true // Mark that solution was explicitly set
        
        Log.d("SudokuBoardView", "Solution board stored in 2D array. Size: ${boardSize}x${boardSize}")
        // Use safe indices based on board size to avoid ArrayIndexOutOfBoundsException
        val lastRow = boardSize - 1
        val lastCol = boardSize - 1
        val midRow = boardSize / 2
        val midCol = boardSize / 2
        Log.d("SudokuBoardView", "Example: Solution[0,0]=${solutionBoard!![0][0]}, Solution[0,1]=${solutionBoard!![0][1]}, Solution[$midRow,$midCol]=${solutionBoard!![midRow][midCol]}, Solution[$lastRow,$lastCol]=${solutionBoard!![lastRow][lastCol]}")
    }
    
    /**
     * Generate solution board using the existing solver
     */
    private fun generateSolution(): Array<IntArray>? {
        val solution = Array(boardSize) { IntArray(boardSize) }
        
        // Copy current board to solution
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                solution[row][col] = board[row][col]
            }
        }
        
        // Use existing solver to complete the solution
        if (SudokuGenerator.solveBoard(solution, boardSize)) {
            return solution
        }
        return null
    }
    
    /**
     * Compute candidates for a specific cell using elimination
     */
    private fun computeCandidatesForCell(row: Int, col: Int): MutableSet<Int> {
        val candidates = mutableSetOf<Int>()
        
        // Start with all possible numbers
        for (num in 1..boardSize) {
            candidates.add(num)
        }
        
        // Remove numbers present in same row
        for (c in 0 until boardSize) {
            if (board[row][c] != 0) {
                candidates.remove(board[row][c])
            }
        }
        
        // Remove numbers present in same column
        for (r in 0 until boardSize) {
            if (board[r][col] != 0) {
                candidates.remove(board[r][col])
            }
        }
        
        // Remove numbers present in same box
        val (boxRows, boxCols) = when (boardSize) {
            6 -> Pair(2, 3) // 6x6: 2x3 boxes
            9 -> Pair(3, 3) // 9x9: 3x3 boxes
            else -> Pair(3, 3)
        }
        val boxRow = (row / boxRows) * boxRows
        val boxCol = (col / boxCols) * boxCols
        for (r in boxRow until boxRow + boxRows) {
            for (c in boxCol until boxCol + boxCols) {
                if (board[r][c] != 0) {
                    candidates.remove(board[r][c])
                }
            }
        }
        
        return candidates
    }
    
    /**
     * Compute candidates for all empty cells
     */
    fun computeAllCandidates() {
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] == 0) {
                    candidates[row][col] = computeCandidatesForCell(row, col)
                } else {
                    candidates[row][col].clear()
                }
            }
        }
    }
    
    /**
     * Update candidates after a number is placed or removed
     * Only removes the placed number from related cells (doesn't auto-compute)
     */
    private fun updateCandidatesAfterPlacement(row: Int, col: Int, value: Int) {
        if (value == 0) return // Nothing to update when removing
        
        // Only remove the placed number from related cells (preserve manual pencil marks)
        // Update candidates for cells in same row
        for (c in 0 until boardSize) {
            if (c != col && board[row][c] == 0) {
                candidates[row][c].remove(value)
            }
        }
        
        // Update candidates for cells in same column
        for (r in 0 until boardSize) {
            if (r != row && board[r][col] == 0) {
                candidates[r][col].remove(value)
            }
        }
        
        // Update candidates for cells in same box
        val (boxRows, boxCols) = when (boardSize) {
            6 -> Pair(2, 3) // 6x6: 2x3 boxes
            9 -> Pair(3, 3) // 9x9: 3x3 boxes
            else -> Pair(3, 3)
        }
        val boxRow = (row / boxRows) * boxRows
        val boxCol = (col / boxCols) * boxCols
        for (r in boxRow until boxRow + boxRows) {
            for (c in boxCol until boxCol + boxCols) {
                if ((r != row || c != col) && board[r][c] == 0) {
                    candidates[r][c].remove(value)
                }
            }
        }
    }
    
    /**
     * Restore candidates after a number is removed
     * Only adds the removed value back to candidates if it's valid AND not already present
     * This preserves manually added pencil marks without duplicating them
     */
    private fun restoreCandidatesAfterRemoval(row: Int, col: Int, removedValue: Int) {
        if (removedValue == 0) return // Nothing to restore
        
        // Restore candidates for cells in same row
        for (c in 0 until boardSize) {
            if (c != col && board[row][c] == 0) {
                // Only add if it's valid AND not already in the candidates (preserve manual marks)
                if (isValidMove(row, c, removedValue) && !candidates[row][c].contains(removedValue)) {
                    candidates[row][c].add(removedValue)
                }
            }
        }
        
        // Restore candidates for cells in same column
        for (r in 0 until boardSize) {
            if (r != row && board[r][col] == 0) {
                // Only add if it's valid AND not already in the candidates (preserve manual marks)
                if (isValidMove(r, col, removedValue) && !candidates[r][col].contains(removedValue)) {
                    candidates[r][col].add(removedValue)
                }
            }
        }
        
        // Restore candidates for cells in same box
        val (boxRows, boxCols) = when (boardSize) {
            6 -> Pair(2, 3) // 6x6: 2x3 boxes
            9 -> Pair(3, 3) // 9x9: 3x3 boxes
            else -> Pair(3, 3)
        }
        val boxRow = (row / boxRows) * boxRows
        val boxCol = (col / boxCols) * boxCols
        for (r in boxRow until boxRow + boxRows) {
            for (c in boxCol until boxCol + boxCols) {
                if ((r != row || c != col) && board[r][c] == 0) {
                    // Only add if it's valid AND not already in the candidates (preserve manual marks)
                    if (isValidMove(r, c, removedValue) && !candidates[r][c].contains(removedValue)) {
                        candidates[r][c].add(removedValue)
                    }
                }
            }
        }
    }
    
    /**
     * Reveal hint for selected cell
     * SIMPLIFIED: Read directly from solution array using (row, col) coordinates
     * This is the simplest and most reliable approach - just read from the stored complete solution
     */
    fun revealHint(): Boolean {
        if (selectedRow == -1 || selectedCol == -1) {
            lastHintErrorMessage = "Select a cell first"
            return false // No cell selected
        }
        
        if (board[selectedRow][selectedCol] != 0) {
            lastHintErrorMessage = "Cell already has a number"
            return false // Cell is not empty
        }
        
        if (hintsRemaining <= 0) {
            lastHintErrorMessage = "No hints remaining"
            return false // No hints remaining
        }
        
        // Check for incorrect user numbers FIRST - prevent hints if there are conflicts
        // Player must fix or remove incorrect numbers before using hints
        if (hasIncorrectUserEnteredNumbers()) {
            lastHintErrorMessage = "Incorrect answers in board, fix or remove them first"
            return false
        }
        
        // SIMPLIFIED: If solution board exists, read directly from it using (row, col)
        // This is exactly what the user suggested - read from the array and place on board
        if (solutionBoard != null && solutionBoardExplicitlySet) {
            // Validate bounds
            if (selectedRow >= 0 && selectedRow < boardSize && 
                selectedCol >= 0 && selectedCol < boardSize) {
                
                // Read directly from solution array: solution[row][col]
                val solutionValue = solutionBoard!![selectedRow][selectedCol]
                
                // Validate the value is valid (1-9 for 9x9, etc)
                if (solutionValue < 1 || solutionValue > boardSize) {
                    Log.e("SudokuBoardView", "Hint: Invalid solution value=$solutionValue at ($selectedRow, $selectedCol)")
                    lastHintErrorMessage = "Invalid solution value"
                    return false
                }
                
                Log.d("SudokuBoardView", "Hint: Reading from solution array[$selectedRow][$selectedCol] = $solutionValue")
                
                // Place the number directly from solution array onto the board
                board[selectedRow][selectedCol] = solutionValue
                isRevealedByHint[selectedRow][selectedCol] = true
                hintsUsed++
                hintsRemaining--
                
                // Update candidates for affected cells
                updateCandidatesAfterPlacement(selectedRow, selectedCol, solutionValue)
                candidates[selectedRow][selectedCol].clear()
                
                lastHintErrorMessage = null
                invalidate()
                return true
            } else {
                Log.e("SudokuBoardView", "Hint: Invalid row/col bounds: ($selectedRow, $selectedCol), boardSize=$boardSize")
                lastHintErrorMessage = "Invalid cell position"
                return false
            }
        }
        
        // Fallback for puzzles without explicit solution (regular puzzles)
        if (solutionBoard == null) {
            lastHintErrorMessage = "No solution available"
            return false
        }
        
        // Use solution board value for regular puzzles (if valid)
        // Note: hasIncorrectUserEnteredNumbers() is already checked at the top of this function
        val solutionValue = solutionBoard!![selectedRow][selectedCol]
        if (isValidMove(selectedRow, selectedCol, solutionValue)) {
            board[selectedRow][selectedCol] = solutionValue
            isRevealedByHint[selectedRow][selectedCol] = true
            hintsUsed++
            hintsRemaining--
            
            updateCandidatesAfterPlacement(selectedRow, selectedCol, solutionValue)
            candidates[selectedRow][selectedCol].clear()
            
            lastHintErrorMessage = null
            invalidate()
            return true
        }
        
        // Last resort: try other techniques (shouldn't happen if solution is correct)
        val hintResult = findValidHintFallback(selectedRow, selectedCol)
        if (hintResult != null) {
            board[selectedRow][selectedCol] = hintResult
            isRevealedByHint[selectedRow][selectedCol] = true
            hintsUsed++
            hintsRemaining--
            
            updateCandidatesAfterPlacement(selectedRow, selectedCol, hintResult)
            candidates[selectedRow][selectedCol].clear()
            
            lastHintErrorMessage = null
            invalidate()
            return true
        }
        
        lastHintErrorMessage = "Cannot generate hint"
        return false
    }
    
    private var lastHintErrorMessage: String? = null
    
    fun getLastHintErrorMessage(): String? = lastHintErrorMessage
    
    /**
     * Check if there are any incorrect user-entered numbers on the board
     * Returns true if any conflicts found (user-entered numbers that violate Sudoku rules)
     */
    private fun hasIncorrectUserEnteredNumbers(): Boolean {
        // Check all non-empty cells for conflicts
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                if (board[row][col] != 0) {
                    // Only check user-entered values (not fixed cells or hints)
                    if (!fixed[row][col] && !isRevealedByHint[row][col]) {
                        // Check if this number conflicts with other cells
                        val conflicts = findConflicts(row, col, board[row][col])
                        if (conflicts.isNotEmpty()) {
                            // Found at least one conflict with user-entered number
                            return true
                        }
                        
                        // Also check if the number is correct according to solution
                        // If solution exists and user-entered value doesn't match, it's incorrect
                        if (solutionBoard != null) {
                            if (board[row][col] != solutionBoard!![row][col]) {
                                // User-entered value doesn't match solution
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false // No incorrect numbers found
    }
    
    /**
     * Fallback hint finder - only used if solution board approach fails
     * This should rarely be needed for Daily Challenge
     */
    private fun findValidHintFallback(row: Int, col: Int): Int? {
        // Find candidates for this cell based on current board state
        val cellCandidates = computeCandidatesForCell(row, col)
        
        // Check for naked single (only one candidate)
        if (cellCandidates.size == 1) {
            return cellCandidates.first()
        }
        
        // Check for hidden single in row
        for (num in 1..boardSize) {
            if (cellCandidates.contains(num)) {
                var countInRow = 0
                for (c in 0 until boardSize) {
                    if (c != col && board[row][c] == 0) {
                        val candidates = computeCandidatesForCell(row, c)
                        if (candidates.contains(num)) {
                            countInRow++
                        }
                    }
                }
                if (countInRow == 0) {
                    // This number can only go in this cell in the row
                    return num
                }
            }
        }
        
        // Check for hidden single in column
        for (num in 1..boardSize) {
            if (cellCandidates.contains(num)) {
                var countInCol = 0
                for (r in 0 until boardSize) {
                    if (r != row && board[r][col] == 0) {
                        val candidates = computeCandidatesForCell(r, col)
                        if (candidates.contains(num)) {
                            countInCol++
                        }
                    }
                }
                if (countInCol == 0) {
                    // This number can only go in this cell in the column
                    return num
                }
            }
        }
        
        // Check for hidden single in box
        val (boxRows, boxCols) = when (boardSize) {
            6 -> Pair(2, 3) // 6x6: 2x3 boxes
            9 -> Pair(3, 3) // 9x9: 3x3 boxes
            else -> Pair(3, 3)
        }
        val boxRow = (row / boxRows) * boxRows
        val boxCol = (col / boxCols) * boxCols
        
        for (num in 1..boardSize) {
            if (cellCandidates.contains(num)) {
                var countInBox = 0
                for (r in boxRow until boxRow + boxRows) {
                    for (c in boxCol until boxCol + boxCols) {
                        if ((r != row || c != col) && board[r][c] == 0) {
                            val candidates = computeCandidatesForCell(r, c)
                            if (candidates.contains(num)) {
                                countInBox++
                            }
                        }
                    }
                }
                if (countInBox == 0) {
                    // This number can only go in this cell in the box
                    return num
                }
            }
        }
        
        // If no specific technique found, return first valid candidate
        // This ensures we never place an invalid number
        for (num in cellCandidates.sorted()) {
            if (isValidMove(row, col, num)) {
                return num
            }
        }
        
        return null // No valid hint found
    }
    
    /**
     * Find a valid hint for the given cell using Sudoku solving techniques
     * 1. Try solution board value if valid
     * 2. Try naked single (only one candidate)
     * 3. Try hidden single (only one place for number in row/column/box)
     */
    private fun findValidHint(row: Int, col: Int): Int? {
        // First, try to use solution board value if it's valid for current state
        if (solutionBoard != null) {
            val solutionValue = solutionBoard!![row][col]
            if (isValidMove(row, col, solutionValue)) {
                return solutionValue
            }
        }
        
        // Find candidates for this cell based on current board state
        val cellCandidates = computeCandidatesForCell(row, col)
        
        // Check for naked single (only one candidate)
        if (cellCandidates.size == 1) {
            return cellCandidates.first()
        }
        
        // Check for hidden single in row
        for (num in 1..boardSize) {
            if (cellCandidates.contains(num)) {
                var countInRow = 0
                for (c in 0 until boardSize) {
                    if (c != col && board[row][c] == 0) {
                        val candidates = computeCandidatesForCell(row, c)
                        if (candidates.contains(num)) {
                            countInRow++
                        }
                    }
                }
                if (countInRow == 0) {
                    // This number can only go in this cell in the row
                    return num
                }
            }
        }
        
        // Check for hidden single in column
        for (num in 1..boardSize) {
            if (cellCandidates.contains(num)) {
                var countInCol = 0
                for (r in 0 until boardSize) {
                    if (r != row && board[r][col] == 0) {
                        val candidates = computeCandidatesForCell(r, col)
                        if (candidates.contains(num)) {
                            countInCol++
                        }
                    }
                }
                if (countInCol == 0) {
                    // This number can only go in this cell in the column
                    return num
                }
            }
        }
        
        // Check for hidden single in box
        val (boxRows, boxCols) = when (boardSize) {
            6 -> Pair(2, 3) // 6x6: 2x3 boxes
            9 -> Pair(3, 3) // 9x9: 3x3 boxes
            else -> Pair(3, 3)
        }
        val boxRow = (row / boxRows) * boxRows
        val boxCol = (col / boxCols) * boxCols
        
        for (num in 1..boardSize) {
            if (cellCandidates.contains(num)) {
                var countInBox = 0
                for (r in boxRow until boxRow + boxRows) {
                    for (c in boxCol until boxCol + boxCols) {
                        if ((r != row || c != col) && board[r][c] == 0) {
                            val candidates = computeCandidatesForCell(r, c)
                            if (candidates.contains(num)) {
                                countInBox++
                            }
                        }
                    }
                }
                if (countInBox == 0) {
                    // This number can only go in this cell in the box
                    return num
                }
            }
        }
        
        // If no specific technique found, return first valid candidate
        // This ensures we never place an invalid number
        for (num in cellCandidates.sorted()) {
            if (isValidMove(row, col, num)) {
                return num
            }
        }
        
        return null // No valid hint found
    }
    
    /**
     * Toggle pencil marks visibility
     */
    fun togglePencilMarks() {
        pencilMarksVisible = !pencilMarksVisible
        // No automatic computation - player must add marks manually
        invalidate()
    }
    
    /**
     * Toggle pencil mode (manual pencil mark entry)
     */
    fun togglePencilMode() {
        pencilMode = !pencilMode
        // Enable visibility when entering pencil mode
        if (pencilMode) {
            pencilMarksVisible = true
        }
        invalidate()
    }
    
    /**
     * Check if pencil mode is active
     */
    fun isPencilModeActive(): Boolean = pencilMode
    
    /**
     * Get hints remaining count
     */
    fun getHintsRemaining(): Int = hintsRemaining
    
    /**
     * Get hints used count
     */
    fun getHintsUsed(): Int = hintsUsed
    
    /**
     * Check if pencil marks are visible
     */
    fun isPencilMarksVisible(): Boolean = pencilMarksVisible
    
    /**
     * Get selected row (for external access)
     */
    override fun getSelectedRow(): Int = selectedRow
    
    /**
     * Get selected column (for external access)
     */
    override fun getSelectedCol(): Int = selectedCol
    
    /**
     * Get board value at specific position (for external access)
     */
    fun getBoardValue(row: Int, col: Int): Int = board[row][col]

    fun getSolutionForSaving(): IntArray? {
        val solution = solutionBoard ?: return null
        val flattened = IntArray(boardSize * boardSize)
        var index = 0
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                flattened[index++] = solution[row][col]
            }
        }
        return flattened
    }

    fun setHintsState(savedRemaining: Int?, savedUsed: Int?, savedMax: Int?) {
        savedMax?.let { maxHintsPerGame = it }
        savedUsed?.let { hintsUsed = it.coerceAtLeast(0) }
        savedRemaining?.let { hintsRemaining = it.coerceAtLeast(0) }
    }

    fun getMaxHintsPerGame(): Int = maxHintsPerGame
    
    /**
     * Update hint limits based on current settings and recalculate remaining hints
     * This is called when settings change during gameplay
     */
    fun updateHintsFromSettings() {
        val newMaxHints = if (gameSettings.isExtendedHintsEnabled()) {
            EXTENDED_HINTS_PER_GAME
        } else {
            DEFAULT_HINTS_PER_GAME
        }
        
        // Only update if the max hints changed
        if (newMaxHints != maxHintsPerGame) {
            maxHintsPerGame = newMaxHints
            
            // Recalculate remaining hints: new remaining = new max - hints used
            hintsRemaining = (newMaxHints - hintsUsed).coerceAtLeast(0)
        }
    }
    
    /**
     * Get comprehensive hint for a cell
     */
    override fun getComprehensiveHint(row: Int, col: Int): ComprehensiveHintResult {
        if (row == -1 || col == -1) {
            return ComprehensiveHintResult(false, 0, "No cell selected", HintType.ERROR)
        }
        if (fixed[row][col]) {
            return ComprehensiveHintResult(false, 0, "Cell is fixed", HintType.ERROR)
        }
        if (board[row][col] != 0) {
            return ComprehensiveHintResult(false, 0, "Cell already has a number", HintType.ERROR)
        }
        
        // Simple hint: find a valid number for the selected cell
        for (num in 1..boardSize) {
            if (isValidMove(row, col, num)) {
                return ComprehensiveHintResult(true, num, "Try placing $num", HintType.SINGLE_CANDIDATE)
            }
        }
        return ComprehensiveHintResult(false, 0, "No valid numbers for this cell", HintType.ERROR)
    }
    
    /**
     * Apply hint to board
     */
    override fun applyHint(row: Int, col: Int, value: Int): Boolean {
        board[row][col] = value
        invalidate()
        return true
    }

    override fun postDelayed(action: () -> Unit, delayMillis: Long) {
        super.postDelayed({ action() }, delayMillis)
    }
}

/**
 * Data classes for comprehensive hint system
 */
data class ComprehensiveHintResult(
    val success: Boolean,
    val value: Int,
    val explanation: String,
    val type: HintType
)

enum class HintType {
    SOLUTION,
    SINGLE_CANDIDATE,
    SOLVER_CONFIRMED,
    SMART_HINT,
    FALLBACK,
    CONFLICT,
    ERROR
}