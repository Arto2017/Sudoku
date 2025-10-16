package com.example.gamesudoku

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.graphics.drawable.ColorDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import android.content.Intent
import android.animation.ObjectAnimator
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.graphics.Typeface
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity() {
    private lateinit var sudokuBoard: SudokuBoardView
    private lateinit var timerText: TextView
    private lateinit var titleText: TextView
    private lateinit var statsManager: StatsManager
    private var secondsElapsed = 0
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var gameStarted = false // Track if player has started playing (separate from timer running state)
    private var boardSize = 9
    private var selectedNumber = 1
    private var currentDifficulty = SudokuGenerator.Difficulty.EASY
    private var totalMistakes = 0
    private lateinit var attemptStore: AttemptStateStore

    private var gameResultSaved = false // Flag to prevent duplicate saves
    private var questPuzzleId: String? = null // Current quest puzzle ID
    private var realmId: String? = null // Current realm ID
    

    companion object {
        private const val KEY_SECONDS = "secondsElapsed"
        private const val KEY_BOARD = "boardState"
        private const val KEY_FIXED = "fixedState"
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            secondsElapsed++
            updateTimerText()
            handler.postDelayed(this, 1000)
        }
    }

    private fun updateTimerText() {
        val minutes = secondsElapsed / 60
        val seconds = secondsElapsed % 60
        timerText.text = String.format("%02d:%02d", minutes, seconds)
    }
    
    private fun updateProgress() {
        val progressText = findViewById<TextView>(R.id.progressText)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        
        val originalEmptyCells = sudokuBoard.getOriginalEmptyCellCount()
        val currentEmptyCells = sudokuBoard.getEmptyCellCount()
        val filledCells = originalEmptyCells - currentEmptyCells
        val percentage = if (originalEmptyCells > 0) (filledCells * 100) / originalEmptyCells else 0
        
        progressText.text = "$percentage%"
        progressBar.progress = percentage
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Get board size from intent
        boardSize = intent.getIntExtra("board_size", intent.getIntExtra(MainMenuActivity.EXTRA_BOARD_SIZE, 9))
        questPuzzleId = intent.getStringExtra("quest_puzzle_id")
        realmId = intent.getStringExtra("realm_id")
        
        // Set difficulty from intent (for Quick Play) or quest puzzle
        val difficultyString = intent.getStringExtra("difficulty") ?: intent.getStringExtra(QuickPlayActivity.EXTRA_DIFFICULTY)
        val isQuickPlay = difficultyString != null
        if (difficultyString != null) {
            currentDifficulty = when (difficultyString) {
                "EASY" -> SudokuGenerator.Difficulty.EASY
                "MEDIUM" -> SudokuGenerator.Difficulty.MEDIUM
                "HARD" -> SudokuGenerator.Difficulty.HARD
                "EXPERT" -> SudokuGenerator.Difficulty.EXPERT
                else -> SudokuGenerator.Difficulty.EASY
            }
        }

        // Initialize stats manager
        statsManager = StatsManager(this)
        attemptStore = AttemptStateStore(this)
        

        sudokuBoard = findViewById(R.id.sudokuBoard)
        timerText = findViewById(R.id.timerText)
        titleText = findViewById(R.id.titleText)
        val progressText = findViewById<TextView>(R.id.progressText)

        // Set board size and generate initial puzzle
        sudokuBoard.setBoardSize(boardSize)
        
        // Apply realm theme if this is a quest puzzle
        if (questPuzzleId != null && realmId != null) {
            val currentRealmId = realmId!! // Force non-null since we checked it
            sudokuBoard.setRealmTheme(currentRealmId)
            
            // Update title with realm information
            titleText?.text = "6×6 Realm of Echoes"
        }
        
        // Add banner animation
        titleText?.alpha = 0f
        titleText?.animate()
            ?.alpha(1f)
            ?.setDuration(1000)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.start()
        
        // Generate puzzle with appropriate difficulty
        if (questPuzzleId != null || isQuickPlay) {
            sudokuBoard.resetPuzzle(currentDifficulty)
        } else {
            sudokuBoard.resetPuzzle()
        }
        
        // Set up conflict listener
        sudokuBoard.setOnConflictListener(object : SudokuBoardView.OnConflictListener {
            override fun onConflictDetected() {
                // Increment mistake counter
                questPuzzleId?.let { id ->
                    totalMistakes = attemptStore.incrementMistakes(id)
                } ?: run {
                    totalMistakes++
                }
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
                Toast.makeText(this@MainActivity, "Mistake made.", Toast.LENGTH_SHORT).show()

                // Warn at 3 mistakes
                if (totalMistakes == 3) {
                    val hud = findViewById<LinearLayout>(R.id.mistakesHud)
                    hud.animate().translationXBy(6f).setDuration(50).withEndAction {
                        hud.animate().translationX(0f).setDuration(50).start()
                    }.start()
                    Toast.makeText(this@MainActivity, "Last chance — 1 mistake left.", Toast.LENGTH_SHORT).show()
                }

                // Fail at 4 mistakes
                if (totalMistakes >= 4) {
                    questPuzzleId?.let { id -> attemptStore.setFailed(id, true) }
                    showPuzzleFailedDialog()
                }
            }
        })
        
        // Apply initial color theme (default to light beige theme)
        // Theme functionality removed - using fixed warm theme

        // Restore saved state
        savedInstanceState?.let {
            secondsElapsed = it.getInt(KEY_SECONDS, 0)
            val savedBoard = it.getSerializable(KEY_BOARD) as? Array<IntArray>
            val savedFixed = it.getSerializable(KEY_FIXED) as? Array<BooleanArray>
            if (savedBoard != null && savedFixed != null) {
                sudokuBoard.setBoardState(savedBoard, savedFixed)
            }
        }

        // Set up difficulty spinner with custom styling
        val difficultySpinner = findViewById<Spinner>(R.id.difficultySpinner)
        val difficultyContainer = findViewById<LinearLayout>(R.id.difficultyContainer)
        
        // Hide difficulty settings if this is a quest puzzle or Quick Play
        if (questPuzzleId != null) {
            difficultyContainer.visibility = View.GONE
            // Update title to show quest information
            titleText?.text = "Quest Puzzle"
        } else if (isQuickPlay) {
            difficultyContainer.visibility = View.GONE
            // Update title to show Quick Play information
            titleText?.text = "Quick Play"
        } else {
            // Only show difficulty settings for regular games
            val difficultyAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.difficulty_levels,
                R.layout.spinner_item_selected
            ).apply {
                setDropDownViewResource(R.layout.spinner_item_compact)
            }
            difficultySpinner.adapter = difficultyAdapter
            difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    currentDifficulty = when (pos) {
                        0 -> SudokuGenerator.Difficulty.EASY
                        1 -> SudokuGenerator.Difficulty.MEDIUM
                        else -> SudokuGenerator.Difficulty.HARD
                    }
                    sudokuBoard.resetPuzzle(currentDifficulty)
                    sudokuBoard.clearNumberHighlight() // Clear number highlighting when changing difficulty
                    secondsElapsed = 0
                    totalMistakes = 0
                    gameStarted = false // Reset game started flag when changing difficulty

                    gameResultSaved = false // Reset flag for new game
                    updateTimerText()
                    stopTimer()
                    updateProgress()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // Set up number buttons
        setupNumberButtons()

        // Set up action buttons
        setupActionButtons()

        // Back (icon) button at top-left
        findViewById<ImageButton>(R.id.gameBackButton)?.setOnClickListener {
            showBackToMenuDialog()
        }

        // Start timer
        startTimer()
        updateProgress()
        // Load mistakes from store (quest only)
        questPuzzleId?.let { id ->
            totalMistakes = attemptStore.getMistakes(id)
        }
        updateMistakesHud(totalMistakes)

        // One-time tooltip explaining mistakes
        val help = findViewById<ImageButton>(R.id.mistakesHelp)
        val prefs = getSharedPreferences("attempt_state", Context.MODE_PRIVATE)
        val tooltipShownKey = "tooltip_mistakes_shown"
        if (!prefs.getBoolean(tooltipShownKey, false)) {
            Toast.makeText(this, "You may make up to 4 mistakes. On the 4th the puzzle fails.", Toast.LENGTH_LONG).show()
            prefs.edit().putBoolean(tooltipShownKey, true).apply()
        }
        help.setOnClickListener {
            Toast.makeText(this, "You can make 4 mistakes. On the 4th the puzzle fails.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupNumberButtons() {
        // Create number buttons dynamically based on board size
        val numberButtonsContainer = findViewById<GridLayout>(R.id.numberButtonsContainer)
        numberButtonsContainer.removeAllViews()
        
        // Set column count based on board size
        numberButtonsContainer.columnCount = boardSize
        
        for (i in 1..boardSize) {
            val button = TextView(this).apply {
                text = i.toString()
                setTextColor(Color.parseColor("#6B4423"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f) // Larger text size
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0) // Remove padding for better centering
                setBackgroundResource(R.drawable.fantasy_number_button_background)
                setTypeface(Typeface.create("serif", Typeface.BOLD))
                setOnClickListener {
                    selectedNumber = i
                    sudokuBoard.setNumber(i)
                    sudokuBoard.highlightNumber(i) // Highlight all cells with this number
                    startTimer()
                    updateProgress()
                    highlightActiveNumber(i)
                    
                    // Check for completion after placing number
                    if (sudokuBoard.isBoardComplete()) {
                        checkVictory()
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

        // No initial selection - let user choose
        selectedNumber = 0
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
                     child.setTextColor(Color.parseColor("#6B4423"))
                }
            }
        }
    }

    private fun setupActionButtons() {
        // Clear button
        findViewById<Button>(R.id.clearButton).setOnClickListener { 
            sudokuBoard.clearSelected()
            sudokuBoard.clearNumberHighlight() // Clear number highlighting when clearing cell
        }



        // Reveal Hint button
        findViewById<Button>(R.id.revealHintButton).setOnClickListener {
            if (sudokuBoard.revealHint()) {
                startTimer() // Start timer when hint is used (player is playing)
                updateProgress()
                // Show success feedback
                Toast.makeText(this, "Hint revealed! (${sudokuBoard.getHintsRemaining()} remaining)", Toast.LENGTH_SHORT).show()
            } else {
                // Show error message
                val message = when {
                    sudokuBoard.getHintsRemaining() <= 0 -> "No hints remaining!"
                    sudokuBoard.getSelectedRow() == -1 || sudokuBoard.getSelectedCol() == -1 -> "Select an empty cell first"
                    sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol()) != 0 -> "Cell is not empty"
                    else -> "Cannot provide hint - puzzle may be unsolvable"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        // Pencil Marks button
        findViewById<Button>(R.id.pencilMarksButton).setOnClickListener {
            startTimer() // Start timer when pencil marks are used (player is playing)
            sudokuBoard.togglePencilMarks()
            val button = findViewById<Button>(R.id.pencilMarksButton)
            if (sudokuBoard.isPencilMarksVisible()) {
                button.text = "📝✓"
                Toast.makeText(this, "Pencil marks enabled", Toast.LENGTH_SHORT).show()
            } else {
                button.text = "📝"
                Toast.makeText(this, "Pencil marks disabled", Toast.LENGTH_SHORT).show()
            }
        }


        // Reset button - hide in quest mode
        val resetButton = findViewById<Button>(R.id.resetButton)
        if (questPuzzleId != null) {
            // Hide reset button in quest mode
            resetButton.visibility = View.GONE
        } else {
            // Show reset button only in regular mode
            resetButton.setOnClickListener {
                sudokuBoard.resetPuzzle(currentDifficulty)
                sudokuBoard.clearNumberHighlight() // Clear number highlighting when resetting
                secondsElapsed = 0
                totalMistakes = 0
                gameResultSaved = false
                gameStarted = false // Reset game started flag for new game
                updateTimerText()
                stopTimer()
                updateProgress()
            }
        }

        // Back to menu button
        findViewById<Button>(R.id.backToMenuButton).setOnClickListener {
            showBackToMenuDialog()
        }
    }

    private fun showBackToMenuDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_back_to_menu, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        // Make background transparent around card for nicer presentation
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        view.findViewById<Button>(R.id.dialogCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.dialogConfirm).setOnClickListener {
            dialog.dismiss()
            // Clear attempt state so starting later is fresh
            questPuzzleId?.let { id -> attemptStore.clear(id) }
            finish()
        }

        dialog.show()
    }

    private fun startTimer() {
        if (!running) {
            running = true
            gameStarted = true // Mark that player has started playing
            handler.post(tickRunnable)
        }
    }

    private fun updateMistakesHud(count: Int) {
        val text = findViewById<TextView>(R.id.mistakesText)
        val icon = findViewById<ImageView>(R.id.mistakesIcon)
        text.text = "$count / 4"
        when {
            count >= 4 -> {
                text.setTextColor(Color.parseColor("#C62828"))
                icon.setColorFilter(Color.parseColor("#C62828"))
            }
            count >= 1 -> {
                text.setTextColor(Color.parseColor("#FF8F00"))
                icon.setColorFilter(Color.parseColor("#FF8F00"))
            }
            else -> {
                text.setTextColor(Color.parseColor("#6B4C2A"))
                icon.setColorFilter(Color.parseColor("#6B4C2A"))
            }
        }
        // Content description for accessibility
        text.contentDescription = "Mistakes: $count of 4"
    }

    private fun showPuzzleFailedDialog() {
        stopTimer()
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_puzzle_failed, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<Button>(R.id.dialogRetry).setOnClickListener {
            dialog.dismiss()
            // Reset attempt state and restart puzzle
            questPuzzleId?.let { id -> attemptStore.clear(id) }
            totalMistakes = 0
            updateMistakesHud(0)
            sudokuBoard.resetPuzzle(currentDifficulty)
            sudokuBoard.clearNumberHighlight() // Clear number highlighting when retrying
            secondsElapsed = 0
            totalMistakes = 0
            gameResultSaved = false
            updateTimerText()
            startTimer()
            updateProgress()
        }

        view.findViewById<Button>(R.id.dialogExit).setOnClickListener {
            dialog.dismiss()
            // Return to puzzle list / realm map
            questPuzzleId?.let { _ ->
                val intent = Intent(this, RealmQuestActivity::class.java)
                    .apply { putExtra("realm_id", realmId) }
                startActivity(intent)
            }
            finish()
        }

        dialog.setCancelable(false)
        dialog.show()
    }

    private fun stopTimer() {
        running = false
        handler.removeCallbacks(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
    }

    override fun onResume() {
        super.onResume()
        if (gameStarted) {
            startTimer() // Resume timer if game was started
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SECONDS, secondsElapsed)
        outState.putSerializable(KEY_BOARD, sudokuBoard.getBoardState())
        outState.putSerializable(KEY_FIXED, sudokuBoard.getFixedState())
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }

    // Check for victory
    private fun checkVictory() {
        if (sudokuBoard.isBoardComplete() && !gameResultSaved) {
            stopTimer()
            gameResultSaved = true
            
            // Save game result
            val timeText = timerText.text.toString()
            val gameResult = GameResult(
                boardSize = boardSize,
                difficulty = currentDifficulty,
                timeInSeconds = secondsElapsed,
                mistakes = totalMistakes,
                completed = true
            )
            statsManager.saveGameResult(gameResult)
            
            // Clear attempt state so failed flag/mistakes do not persist after success
            questPuzzleId?.let { id -> attemptStore.clear(id) }
            
            // Record quest progress if this is a quest puzzle
            questPuzzleId?.let { puzzleId ->
                val questCodex = QuestCodex(this)
                questCodex.recordPuzzleCompletion(realmId ?: "", puzzleId, secondsElapsed.toLong(), totalMistakes)
                
                // Show quest completion dialog
                showQuestCompletionDialog(puzzleId, realmId ?: "", timeText, totalMistakes)
            } ?: run {
                // Show regular victory dialog for non-quest games
                showVictoryDialog(timeText, totalMistakes)
            }
        }
    }

    private fun showVictoryDialog(timeText: String, mistakes: Int) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("🎉 Victory!")
            .setMessage("Congratulations! You completed the puzzle!\n\nTime: $timeText\nMistakes: $mistakes")
            .setPositiveButton("New Game") { _, _ ->
                // Start new game with same settings
                sudokuBoard.resetPuzzle(currentDifficulty)
                sudokuBoard.clearNumberHighlight() // Clear number highlighting when starting new game
                secondsElapsed = 0
                totalMistakes = 0
                gameResultSaved = false
                gameStarted = false // Reset game started flag for new game
                updateTimerText()
                updateProgress()
            }
            .setNegativeButton("Main Menu") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
    }

    private fun showQuestCompletionDialog(puzzleId: String, realmId: String, timeText: String, mistakes: Int) {
        val questCodex = QuestCodex(this)
        val realm = questCodex.getRealmById(realmId)
        val puzzleChain = questCodex.getPuzzleChain(realmId)
        
        val currentPuzzle = puzzleChain?.puzzles?.find { it.id == puzzleId }
        val nextPuzzle = currentPuzzle?.let { puzzle ->
            puzzleChain.puzzles.find { it.puzzleNumber == puzzle.puzzleNumber + 1 }
        }
        
        val message = if (nextPuzzle?.isUnlocked == true) {
            "🎉 Puzzle ${currentPuzzle?.puzzleNumber} Completed!\n\n" +
            "⭐ Stars earned: ${currentPuzzle?.stars}/3\n" +
            "⏱️ Time: $timeText\n" +
            "❌ Mistakes: $mistakes\n\n" +
            "🔓 Next Puzzle: ${nextPuzzle.puzzleNumber}\n" +
            "📏 Board: ${nextPuzzle.boardSize}×${nextPuzzle.boardSize}\n" +
            "🎯 Difficulty: ${nextPuzzle.difficulty.name}\n\n" +
            "Continue to next puzzle?"
        } else {
            "🎉 Puzzle ${currentPuzzle?.puzzleNumber} Completed!\n\n" +
            "⭐ Stars earned: ${currentPuzzle?.stars}/3\n" +
            "⏱️ Time: $timeText\n" +
            "❌ Mistakes: $mistakes\n\n" +
            "🏆 Congratulations! You've completed this realm!\n" +
            "Return to Realm Selection to see your progress."
        }
        
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_quest_completed, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        // Match ornate dialog style with transparent window background
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Populate UI
        view.findViewById<TextView>(R.id.dialogTitle)?.text = "Puzzle Complete! 🎯"
        view.findViewById<TextView>(R.id.dialogMessage)?.text = message

        // Actions
        view.findViewById<Button>(R.id.dialogContinue)?.setOnClickListener {
            dialog.dismiss()
            // Open the specific realm window for the current tier (e.g., Echoes for Tier I)
            val intent = Intent(this, RealmQuestActivity::class.java).apply {
                putExtra("realm_id", realmId)
            }
            startActivity(intent)
            finish()
        }

        view.findViewById<Button>(R.id.dialogRealmMap)?.setOnClickListener {
            dialog.dismiss()
            // Open Sudoku Quest (realm selection) window
            val intent = Intent(this, RealmSelectionActivity::class.java)
            startActivity(intent)
            finish()
        }

        dialog.show()
    }

    // Disable phone back button - use UI back button instead
    override fun onBackPressed() {
        // Do nothing - phone back button is disabled
    }
    
}