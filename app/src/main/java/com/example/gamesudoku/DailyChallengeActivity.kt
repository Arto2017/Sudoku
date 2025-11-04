package com.example.gamesudoku

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import android.widget.PopupWindow
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class DailyChallengeActivity : AppCompatActivity() {
    
    private lateinit var sudokuBoardView: SudokuBoardView
    private lateinit var dailyChallengeManager: DailyChallengeManager
    private lateinit var currentPuzzle: DailyChallengeGenerator.DailyPuzzle
    
    // UI Elements
    private lateinit var headerTitle: TextView
    private lateinit var timerText: TextView
    private lateinit var difficultyText: TextView
    private lateinit var hintButton: Button
    private lateinit var notesButton: Button
    
    // Game state
    private var gameStartTime: Long = 0
    private var gameTime: Long = 0
    private var isGameActive = false
    private var movesCount = 0
    private var hintsUsed = 0
    private var gameTimerHandler: Handler? = null
    private var gameTimerRunnable: Runnable? = null
    
    // Mistake tracking (like MainActivity)
    private var totalMistakes = 0
    private lateinit var attemptStore: AttemptStateStore
    
    // Tooltip management
    private var currentTooltip: PopupWindow? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_challenge)
        
        initializeViews()
        initializeGame()
        setupClickListeners()
        startGame()
    }
    
    private fun initializeViews() {
        sudokuBoardView = findViewById(R.id.dailySudokuBoard)
        headerTitle = findViewById(R.id.dailyHeaderTitle)
        timerText = findViewById(R.id.dailyTimerText)
        difficultyText = findViewById(R.id.dailyDifficultyText)
        hintButton = findViewById(R.id.btnDailyHint)
        notesButton = findViewById(R.id.btnDailyNotes)
        
        // Setup back button
        findViewById<ImageButton>(R.id.btnDailyBack).setOnClickListener {
            onBackPressed()
        }
        
        // Setup number buttons (matching MainActivity design)
        setupNumberButtons()
    }
    
    private fun initializeGame() {
        dailyChallengeManager = DailyChallengeManager(this)
        attemptStore = AttemptStateStore(this)
        
        // Generate today's puzzle
        currentPuzzle = DailyChallengeGenerator.generateDailyPuzzle()
        
        // Setup Sudoku board
        sudokuBoardView.setBoardSize(9)
        loadPuzzleToBoard()
        
        // Load mistakes from store for daily challenge
        val dailyChallengeId = "daily_${currentPuzzle.date}"
        totalMistakes = attemptStore.getMistakes(dailyChallengeId)
        updateMistakesHud(totalMistakes)
        
        // Update UI
        updateHeader()
        updateDifficulty()
        
    }
    
    private fun loadPuzzleToBoard() {
        val board = Array(9) { IntArray(9) }
        val fixed = Array(9) { BooleanArray(9) }
        
        // Convert 1D clues array to 2D board
        for (i in currentPuzzle.clues.indices) {
            val row = i / 9
            val col = i % 9
            board[row][col] = currentPuzzle.clues[i]
            fixed[row][col] = currentPuzzle.clues[i] != 0
        }
        
        sudokuBoardView.setBoardState(board, fixed)
        
        // IMPORTANT: Set solution board BEFORE initializing hint system
        // This ensures the solution is preserved and used for hints
        Log.d("DailyChallenge", "Setting solution board. Solution size: ${currentPuzzle.solution.size}")
        sudokuBoardView.setSolutionBoard(currentPuzzle.solution)
        
        // Initialize hint system (won't overwrite explicitly set solution)
        sudokuBoardView.initializeHintSystem()
        
        // Verify solution was set correctly
        Log.d("DailyChallenge", "Hint system initialized")
    }
    
    private fun setupClickListeners() {
        hintButton.setOnClickListener {
            if (sudokuBoardView.revealHint()) {
                hintsUsed++
                showTooltip(hintButton, "Hint! (${sudokuBoardView.getHintsRemaining()} left)")
                
                // Check for completion after revealing hint (in case hint fills last cell)
                if (sudokuBoardView.isBoardComplete()) {
                    completeGame()
                }
            } else {
                // Show error message
                val errorMsg = sudokuBoardView.getLastHintErrorMessage()
                val message = when {
                    errorMsg != null -> errorMsg
                    sudokuBoardView.getHintsRemaining() <= 0 -> "No hints left"
                    sudokuBoardView.getSelectedRow() == -1 || sudokuBoardView.getSelectedCol() == -1 -> "Select cell first"
                    sudokuBoardView.getBoardValue(sudokuBoardView.getSelectedRow(), sudokuBoardView.getSelectedCol()) != 0 -> "Cell not empty"
                    else -> "Cannot hint"
                }
                showTooltip(hintButton, message)
            }
        }
        
        findViewById<Button>(R.id.btnDailyClear).setOnClickListener {
            sudokuBoardView.clearSelected()
            sudokuBoardView.clearNumberHighlight() // Clear number highlighting when clearing cell
        }
        
        notesButton.setOnClickListener {
            sudokuBoardView.togglePencilMode()
            updateNotesButton()
            if (sudokuBoardView.isPencilModeActive()) {
                notesButton.text = "📝✓"
                showTooltip(notesButton, "Select number, tap cell")
            } else {
                notesButton.text = "📝"
                showTooltip(notesButton, "Pencil mode off")
            }
        }
        
        // Board completion listener with mistake tracking
        sudokuBoardView.setOnConflictListener(object : SudokuBoardView.OnConflictListener {
            override fun onConflictDetected() {
                // Increment mistake counter
                val dailyChallengeId = "daily_${currentPuzzle.date}"
                totalMistakes = attemptStore.incrementMistakes(dailyChallengeId)
                updateMistakesHud(totalMistakes)
                
                // Provide haptic feedback
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(150)
                }
                
                // Show brief toast message
                Toast.makeText(this@DailyChallengeActivity, "Mistake made.", Toast.LENGTH_SHORT).show()

                // Warn at 3 mistakes
                if (totalMistakes == 3) {
                    val hud = findViewById<LinearLayout>(R.id.mistakesHud)
                    hud?.animate()?.translationXBy(6f)?.setDuration(50)?.withEndAction {
                        hud.animate().translationX(0f).setDuration(50).start()
                    }?.start()
                    Toast.makeText(this@DailyChallengeActivity, "Last chance — 1 mistake left.", Toast.LENGTH_SHORT).show()
                }

                // Fail at 4 mistakes
                if (totalMistakes >= 4) {
                    attemptStore.setFailed(dailyChallengeId, true)
                    showPuzzleFailedDialog()
                }
            }
        })
    }
    
    private fun startGame() {
        gameStartTime = System.currentTimeMillis()
        isGameActive = true
        startGameTimer()
    }
    
    private fun startGameTimer() {
        gameTimerRunnable = object : Runnable {
            override fun run() {
                if (isGameActive) {
                    gameTime = System.currentTimeMillis() - gameStartTime
                    updateTimer()
                }
                gameTimerHandler?.postDelayed(this, 100) // Update every 100ms for smooth timer
            }
        }
        gameTimerHandler = Handler(Looper.getMainLooper())
        gameTimerHandler?.post(gameTimerRunnable!!)
    }
    
    private fun stopGameTimer() {
        gameTimerHandler?.removeCallbacks(gameTimerRunnable!!)
    }
    
    private fun updateTimer() {
        val seconds = (gameTime / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        timerText.text = String.format("%02d:%02d", minutes, remainingSeconds)
    }
    
    private fun updateHeader() {
        val dateFormatter = SimpleDateFormat("MMM dd", Locale.US)
        dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
        val formattedDate = dateFormatter.format(Date())
        headerTitle.text = formattedDate
    }
    
    private fun updateDifficulty() {
        difficultyText.text = currentPuzzle.difficulty.name
    }
    
    
    
    private fun updateNotesButton() {
        val isPencilModeActive = sudokuBoardView.isPencilModeActive()
        notesButton.text = if (isPencilModeActive) "📝✓" else "📝"
    }
    
    private fun showTooltip(anchorView: View, message: String) {
        // Dismiss previous tooltip if exists
        currentTooltip?.dismiss()
        
        val inflater = LayoutInflater.from(this)
        val tooltipView = inflater.inflate(R.layout.tooltip_small, null)
        val tooltipText = tooltipView.findViewById<TextView>(R.id.tooltipText)
        tooltipText.text = message
        
        val popup = PopupWindow(
            tooltipView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            elevation = 8f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        
        // Measure tooltip to get actual width
        tooltipView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val tooltipWidth = tooltipView.measuredWidth
        
        // Calculate position - center above button
        val offsetX = -(tooltipWidth / 2) + (anchorView.width / 2)
        val offsetY = -anchorView.height - 20 // Above button
        
        // Try to show above, fallback to below if no space
        try {
            popup.showAsDropDown(anchorView, offsetX, offsetY, Gravity.CENTER)
        } catch (e: Exception) {
            // Fallback: show below if not enough space above
            popup.showAsDropDown(anchorView, offsetX, 10, Gravity.CENTER)
        }
        
        currentTooltip = popup
        
        // Auto-dismiss after 1.5 seconds (shorter for tooltips)
        Handler(Looper.getMainLooper()).postDelayed({
            if (popup.isShowing) {
                popup.dismiss()
            }
            if (currentTooltip == popup) {
                currentTooltip = null
            }
        }, 1500)
    }
    
    private fun updateMistakesHud(count: Int) {
        val text = findViewById<TextView>(R.id.mistakesText)
        text?.text = "$count / ∞"
        when {
            count >= 4 -> {
                text?.setTextColor(Color.parseColor("#C62828"))
            }
            count >= 1 -> {
                text?.setTextColor(Color.parseColor("#FF8F00"))
            }
            else -> {
                text?.setTextColor(Color.parseColor("#6B4C2A"))
            }
        }
        // Content description for accessibility
        text?.contentDescription = "Mistakes: $count of infinity"
    }
    
    private fun showPuzzleFailedDialog() {
        isGameActive = false
        stopGameTimer()
        
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_puzzle_failed, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        view.findViewById<Button>(R.id.dialogRetry).setOnClickListener {
            dialog.dismiss()
            // Reset attempt state and restart puzzle
            val dailyChallengeId = "daily_${currentPuzzle.date}"
            attemptStore.clear(dailyChallengeId)
            totalMistakes = 0
            updateMistakesHud(0)
            loadPuzzleToBoard() // Reload the daily puzzle
            sudokuBoardView.clearNumberHighlight()
            gameStartTime = System.currentTimeMillis()
            isGameActive = true
            startGameTimer()
        }

        view.findViewById<Button>(R.id.dialogExit).setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.show()
    }
    
    private fun setupNumberButtons() {
        // Create number buttons dynamically - exactly like MainActivity
        val numberButtonsContainer = findViewById<GridLayout>(R.id.numberButtonsContainer)
        numberButtonsContainer.removeAllViews()
        
        // Set column count to 9 (Daily Challenge is always 9x9)
        numberButtonsContainer.columnCount = 9
        
        for (i in 1..9) {
            val button = TextView(this).apply {
                text = i.toString()
                setTextColor(android.graphics.Color.parseColor("#6B4423"))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24f) // Larger text size
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, 0) // Remove padding for better centering
                setBackgroundResource(R.drawable.fantasy_number_button_background)
                typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
                setOnClickListener {
                    sudokuBoardView.setNumber(i)
                    sudokuBoardView.highlightNumber(i) // Highlight all cells with this number
                    movesCount++
                    highlightActiveNumber(i)
                    
                    // Check for completion after placing number
                    if (sudokuBoardView.isBoardComplete()) {
                        completeGame()
                    }
                }
                isClickable = true
                isFocusable = true
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = resources.getDimensionPixelSize(R.dimen.number_button_height) + 20 // Increase height
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(
                        resources.getDimensionPixelSize(R.dimen.responsive_margin_small),
                        resources.getDimensionPixelSize(R.dimen.responsive_margin_small) / 2,
                        resources.getDimensionPixelSize(R.dimen.responsive_margin_small),
                        resources.getDimensionPixelSize(R.dimen.responsive_margin_small) / 2
                    )
                }
            }
            numberButtonsContainer.addView(button)
        }
    }
    
    private fun highlightActiveNumber(number: Int) {
        val numberButtonsContainer = findViewById<GridLayout>(R.id.numberButtonsContainer)
        for (i in 0 until numberButtonsContainer.childCount) {
            val child = numberButtonsContainer.getChildAt(i)
            if (child is TextView) {
                if (i + 1 == number) {
                    // Highlight active number
                    child.isSelected = true
                    child.setTextColor(Color.BLACK)
                } else {
                    // Reset to normal background
                    child.isSelected = false
                    child.setTextColor(android.graphics.Color.parseColor("#6B4423"))
                }
            }
        }
    }
    
    
    private fun completeGame() {
        isGameActive = false
        stopGameTimer()
        
        val timeSeconds = (gameTime / 1000).toInt()
        
        // Save completion record
        val record = DailyChallengeManager.DailyRecord(
            date = currentPuzzle.date,
            timeSeconds = timeSeconds,
            moves = movesCount,
            difficulty = currentPuzzle.difficulty,
            hintsUsed = hintsUsed
        )
        
        dailyChallengeManager.saveDailyRecord(record)
        
        // Show completion dialog
        showCompletionDialog(record)
    }
    
    private fun showCompletionDialog(record: DailyChallengeManager.DailyRecord) {
        val stats = dailyChallengeManager.getUserStats()
        val timeFormatted = String.format("%02d:%02d", record.timeSeconds / 60, record.timeSeconds % 60)
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_daily_challenge_complete, null)
        
        // Set the stats data
        dialogView.findViewById<TextView>(R.id.completionTime).text = timeFormatted
        dialogView.findViewById<TextView>(R.id.completionHints).text = record.hintsUsed.toString()
        dialogView.findViewById<TextView>(R.id.completionStreak).text = "${stats.streakDays} days"
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // Make background transparent around card for nicer presentation
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        // Setup button click listeners
        dialogView.findViewById<Button>(R.id.btnShare).setOnClickListener {
            dialog.dismiss()
            shareResults()
        }
        
        dialogView.findViewById<Button>(R.id.btnDone).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialog.show()
    }
    
    private fun getCoinsEarned(difficulty: DailyChallengeGenerator.Difficulty, streakDays: Int): Int {
        val baseCoins = when (difficulty) {
            DailyChallengeGenerator.Difficulty.EASY -> 10
            DailyChallengeGenerator.Difficulty.MEDIUM -> 15
            DailyChallengeGenerator.Difficulty.HARD -> 20
        }
        return baseCoins + (streakDays * 2)
    }
    
    private fun shareResults() {
        val timeFormatted = String.format("%02d:%02d", (gameTime / 1000) / 60, (gameTime / 1000) % 60)
        val shareText = "I just completed today's Daily Sudoku Challenge in $timeFormatted! Can you beat my time? 🧩"
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share your achievement"))
    }
    
    override fun onPause() {
        super.onPause()
        // Keep timer running even when app is paused
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopGameTimer()
    }
    
    override fun onBackPressed() {
        if (isGameActive) {
            showFantasyExitDialog()
        } else {
            super.onBackPressed()
        }
    }
    
    private fun showFantasyExitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.fantasy_exit_dialog, null)
        
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Setup button click listeners
        dialogView.findViewById<Button>(R.id.btnContinue).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.btnExit).setOnClickListener {
            dialog.dismiss()
            super.onBackPressed()
        }
        
        // Style the dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
