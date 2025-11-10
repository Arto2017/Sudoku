package com.example.gamesudoku

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.*
import android.view.Gravity
import android.util.DisplayMetrics
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.LinearInterpolator
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity() {
    private lateinit var sudokuBoard: SudokuBoardView
    private lateinit var timerText: TextView
    private lateinit var titleText: TextView
    private lateinit var statsManager: StatsManager
    private lateinit var soundManager: SoundManager
    private lateinit var audioManager: AudioManager
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
    private var isQuickPlay = false // Flag to indicate if this is Quick Play mode
    

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
        // Progress bar removed, do nothing
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
        isQuickPlay = difficultyString != null && questPuzzleId == null
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
        soundManager = SoundManager.getInstance(this)
        audioManager = AudioManager.getInstance(this)
        

        sudokuBoard = findViewById(R.id.sudokuBoard)
        timerText = findViewById(R.id.timerText)
        titleText = findViewById(R.id.titleText)
        val titleBanner = findViewById<LinearLayout>(R.id.titleBanner)

        // Set board size and generate initial puzzle
        sudokuBoard.setBoardSize(boardSize)
        
        // Apply realm theme if this is a quest puzzle
        if (questPuzzleId != null && realmId != null) {
            val currentRealmId = realmId!! // Force non-null since we checked it
            sudokuBoard.setRealmTheme(currentRealmId)
            // Hide title banner for quest puzzles
            titleBanner?.visibility = View.GONE
        } else if (isQuickPlay) {
            // Hide title banner for Quick Play
            titleBanner?.visibility = View.GONE
        }
        
        // Generate puzzle with appropriate difficulty
        // For quest puzzles, use puzzle ID as seed to ensure same puzzle every time
        if (questPuzzleId != null) {
            // Use puzzle ID as seed for deterministic generation
            sudokuBoard.resetPuzzle(currentDifficulty, questPuzzleId)
        } else if (isQuickPlay) {
            sudokuBoard.resetPuzzle(currentDifficulty)
        } else {
            sudokuBoard.resetPuzzle()
        }
        
        // Set up conflict listener
        sudokuBoard.setOnConflictListener(object : SudokuBoardView.OnConflictListener {
            override fun onConflictDetected() {
                // Play error sound
                soundManager.playError()
                
                // Increment mistake counter
                questPuzzleId?.let { id ->
                    totalMistakes = attemptStore.incrementMistakes(id)
                } ?: run {
                    totalMistakes++
                }
                updateMistakesHud(totalMistakes)
                
                // Provide haptic feedback
                if (audioManager.areHapticsEnabled()) {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(150)
                    }
                }

                // Mistakes are unlimited (infinity) for all game modes
                // No game over dialog - player can continue playing regardless of mistake count
                // This applies to Play Now, Quest puzzles, and Daily Challenge
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
        val difficultyText = findViewById<TextView>(R.id.difficultyText)
        val difficultyContainer = findViewById<LinearLayout>(R.id.difficultyContainer)
        
        // Hide difficulty settings if this is a quest puzzle or Quick Play
        if (questPuzzleId != null) {
            difficultySpinner.visibility = View.GONE
            difficultyText.visibility = View.VISIBLE
            difficultyText.text = currentDifficulty.name
            // Don't update title
        } else if (isQuickPlay) {
            difficultySpinner.visibility = View.GONE
            difficultyText.visibility = View.VISIBLE
            difficultyText.text = currentDifficulty.name
        } else {
            difficultySpinner.visibility = View.VISIBLE
            difficultyText.visibility = View.GONE
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

        // Play entrance animation for a fresh launch
        playStartAnimation(savedInstanceState)

        // Back (icon) button at top-left
        findViewById<ImageButton>(R.id.gameBackButton)?.setOnClickListener {
            showBackToMenuDialog()
        }

        // Settings button - show for Quick Play only
        val settingsButton = findViewById<TextView>(R.id.settingsButton)
        if (isQuickPlay) {
            settingsButton?.visibility = View.VISIBLE
            settingsButton?.setOnClickListener {
                val intent = Intent(this, QuickPlayActivity::class.java)
                startActivity(intent)
            }
        }

        findViewById<ImageButton>(R.id.systemSettingsButton)?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Start timer
        startTimer()
        updateProgress()
        // Load mistakes from store (quest only)
        questPuzzleId?.let { id ->
            totalMistakes = attemptStore.getMistakes(id)
        }
        updateMistakesHud(totalMistakes)

        // Mistake help removed
    }

    private fun playStartAnimation(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            return
        }

        val mainLayout = findViewById<View>(R.id.mainLayout)
        val topCard = findViewById<View>(R.id.topCard)
        val titleBanner = findViewById<View>(R.id.titleBanner)
        val timerContainer = findViewById<View>(R.id.timerText)
        val difficultyContainer = findViewById<View>(R.id.difficultyContainer)
        val mistakesHud = findViewById<View>(R.id.mistakesHud)
        val boardContainer = findViewById<View>(R.id.boardContainer)
        val controlCard = findViewById<View>(R.id.controlCard)
        val numberButtons = findViewById<View>(R.id.numberButtonsContainer)

        mainLayout.alpha = 0f
        topCard?.apply {
            alpha = 0f
            translationY = -50f
        }
        titleBanner?.apply {
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
        }
        timerContainer?.alpha = 0f
        difficultyContainer?.alpha = 0f
        mistakesHud?.alpha = 0f
        boardContainer?.apply {
            alpha = 0f
            scaleX = 0.95f
            scaleY = 0.95f
        }
        controlCard?.apply {
            alpha = 0f
            translationY = 60f
        }
        numberButtons?.alpha = 0f

        mainLayout.post {
            mainLayout.animate()
                .alpha(1f)
                .setDuration(150)
                .start()

            topCard?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setDuration(300)
                ?.setInterpolator(AccelerateDecelerateInterpolator())
                ?.start()

            titleBanner?.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setStartDelay(150)
                ?.setDuration(350)
                ?.setInterpolator(OvershootInterpolator(1.05f))
                ?.start()

            timerContainer?.animate()
                ?.alpha(1f)
                ?.setStartDelay(150)
                ?.setDuration(250)
                ?.start()

            difficultyContainer?.animate()
                ?.alpha(1f)
                ?.setStartDelay(200)
                ?.setDuration(250)
                ?.start()

            mistakesHud?.animate()
                ?.alpha(1f)
                ?.setStartDelay(250)
                ?.setDuration(250)
                ?.start()

            boardContainer?.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setStartDelay(200)
                ?.setDuration(450)
                ?.setInterpolator(OvershootInterpolator(1.08f))
                ?.start()

            controlCard?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setStartDelay(300)
                ?.setDuration(350)
                ?.setInterpolator(AccelerateDecelerateInterpolator())
                ?.start()

            numberButtons?.animate()
                ?.alpha(1f)
                ?.setStartDelay(400)
                ?.setDuration(300)
                ?.start()
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
                    soundManager.playClick()
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
        val hintButton = findViewById<Button>(R.id.revealHintButton)
        hintButton.setOnClickListener {
            if (sudokuBoard.revealHint()) {
                startTimer() // Start timer when hint is used (player is playing)
                updateProgress()
                
                // Highlight the number that was placed by the hint (same as manual placement)
                val placedNumber = sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol())
                if (placedNumber > 0) {
                    sudokuBoard.highlightNumber(placedNumber)
                    selectedNumber = placedNumber
                    highlightActiveNumber(placedNumber)
                }
                
                // Show success feedback
                showTooltip(hintButton, "Hint! (${sudokuBoard.getHintsRemaining()} left)")
                
                // Check for completion after revealing hint (in case hint fills last cell)
                if (sudokuBoard.isBoardComplete()) {
                    checkVictory()
                }
            } else {
                // Show error message
                val errorMsg = sudokuBoard.getLastHintErrorMessage()
                val message = when {
                    errorMsg != null -> errorMsg
                    sudokuBoard.getHintsRemaining() <= 0 -> "No hints left"
                    sudokuBoard.getSelectedRow() == -1 || sudokuBoard.getSelectedCol() == -1 -> "Select cell first"
                    sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol()) != 0 -> "Cell not empty"
                    else -> "Cannot hint"
                }
                showTooltip(hintButton, message)
            }
        }

        // Pencil Mode button - manual pencil mark entry
        val pencilButton = findViewById<Button>(R.id.pencilMarksButton)
        pencilButton.setOnClickListener {
            startTimer() // Start timer when pencil marks are used (player is playing)
            sudokuBoard.togglePencilMode()
            if (sudokuBoard.isPencilModeActive()) {
                pencilButton.text = "📝✓"
                showTooltip(pencilButton, "Select number, tap cell")
            } else {
                pencilButton.text = "📝"
                showTooltip(pencilButton, "Pencil mode off")
            }
        }


        // Reset button - hide in quest mode and Quick Play
        val resetButton = findViewById<Button>(R.id.resetButton)
        if (questPuzzleId != null || isQuickPlay) {
            // Hide reset button in quest mode and Quick Play
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

        // Back to menu button - hide for quest puzzles and Quick Play
        val backToMenuButton = findViewById<Button>(R.id.backToMenuButton)
        if (questPuzzleId != null || isQuickPlay) {
            backToMenuButton.visibility = View.GONE
        } else {
            backToMenuButton.setOnClickListener {
                showBackToMenuDialog()
            }
        }
    }

    private var currentTooltip: PopupWindow? = null
    
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
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
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
            
            // For quest puzzles, go back to realm/level window instead of main menu
            if (questPuzzleId != null && realmId != null) {
                val intent = Intent(this, RealmQuestActivity::class.java).apply {
                    putExtra("realm_id", realmId)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            } else {
                // For Quick Play and other modes, go to main menu
                val intent = Intent(this, MainMenuActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            }
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
        text.text = "$count / ∞"
        when {
            count >= 4 -> {
                text.setTextColor(Color.parseColor("#C62828"))
            }
            count >= 1 -> {
                text.setTextColor(Color.parseColor("#FF8F00"))
            }
            else -> {
                text.setTextColor(Color.parseColor("#6B4C2A"))
            }
        }
        // Content description for accessibility
        text.contentDescription = "Mistakes: $count of infinity"
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
                
                // Clear saved board state when puzzle is completed
                questCodex.clearPuzzleBoardState(puzzleId)
                
                questCodex.recordPuzzleCompletion(realmId ?: "", puzzleId, secondsElapsed.toLong(), totalMistakes)
                
                // Show animated quest victory dialog
                showQuestVictoryDialog(timeText, totalMistakes)
            } ?: run {
                // Show regular victory dialog for non-quest games
                showVictoryDialog(timeText, totalMistakes)
            }
        }
    }

    private fun showVictoryDialog(timeText: String, mistakes: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_puzzle_complete, null)
        
        // Set the stats data
        dialogView.findViewById<TextView>(R.id.completionTime)?.text = timeText
        dialogView.findViewById<TextView>(R.id.completionMistakes)?.text = mistakes.toString()
        
        // Format difficulty name (capitalize first letter)
        val difficultyText = currentDifficulty.name.lowercase().replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase() else it.toString() 
        }
        dialogView.findViewById<TextView>(R.id.completionDifficulty)?.text = difficultyText
        
        // Calculate performance stars based on time and mistakes
        val performanceStars = calculatePerformanceStars(secondsElapsed, mistakes, boardSize)
        val starsText = dialogView.findViewById<TextView>(R.id.performanceStars)
        val starDisplay = "⭐".repeat(performanceStars) + "☆".repeat(5 - performanceStars)
        starsText?.text = starDisplay
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // Make background transparent around card for nicer presentation
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        // Setup button click listeners
        dialogView.findViewById<Button>(R.id.btnNewGame)?.setOnClickListener {
            dialog.dismiss()
            // Start new game with same settings
            sudokuBoard.resetPuzzle(currentDifficulty) // This now clears selection, highlighting, and animations
            secondsElapsed = 0
            totalMistakes = 0
            gameResultSaved = false
            gameStarted = false // Reset game started flag for new game
            updateTimerText()
            updateProgress()
        }
        
        dialogView.findViewById<Button>(R.id.btnMenu)?.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialogView.findViewById<Button>(R.id.btnShare)?.setOnClickListener {
            dialog.dismiss()
            // Share functionality can be added here if needed
            Toast.makeText(this, "Share feature coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        dialog.show()
    }
    
    private fun calculatePerformanceStars(timeSeconds: Int, mistakes: Int, boardSize: Int): Int {
        var stars = 5
        
        // Deduct stars for mistakes
        when (boardSize) {
            6 -> {
                // 6x6: more lenient
                if (mistakes > 5) stars -= 2
                else if (mistakes > 2) stars -= 1
            }
            9 -> {
                // 9x9: stricter
                if (mistakes > 3) stars -= 2
                else if (mistakes > 1) stars -= 1
            }
        }
        
        // Deduct stars for slow completion (optional - time-based)
        // This can be customized based on difficulty
        val expectedTime = when (currentDifficulty) {
            SudokuGenerator.Difficulty.EASY -> if (boardSize == 6) 300 else 600
            SudokuGenerator.Difficulty.MEDIUM -> if (boardSize == 6) 600 else 1200
            SudokuGenerator.Difficulty.HARD -> if (boardSize == 6) 900 else 1800
            SudokuGenerator.Difficulty.EXPERT -> if (boardSize == 6) 1200 else 2400
        }
        
        // Deduct stars if completion time is too slow
        if (timeSeconds > expectedTime * 2) {
            stars = (stars - 1).coerceAtLeast(1)
        } else if (timeSeconds > (expectedTime * 1.5).toInt()) {
            // Slightly slow, but no penalty (stars already adjusted for mistakes)
            stars = stars.coerceAtLeast(1)
        }
        
        return stars.coerceIn(1, 5)
    }

    private fun showQuestVictoryDialog(time: String, mistakes: Int) {
        // Record quest completion
        val questCodex = QuestCodex(this)
        val timeSeconds = secondsElapsed.toLong()
        
        questPuzzleId?.let { puzzleId ->
            realmId?.let { realmId ->
                questCodex.recordPuzzleCompletion(realmId, puzzleId, timeSeconds, totalMistakes)
                
                // Clear saved board state when puzzle is completed
                questCodex.clearPuzzleBoardState(puzzleId)
                android.util.Log.d("MainActivity", "Cleared quest puzzle state after completion: $puzzleId")
            }
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_quest_victory, null)
        
        // Get puzzle to know board size
        val puzzle = questCodex.getSavedPuzzle(questPuzzleId ?: "")
        val boardSize = puzzle?.boardSize ?: 9
        
        // Calculate stars earned
        val starsEarned = questCodex.calculateStars(timeSeconds, mistakes, boardSize)
        
        // Update stats
        dialogView.findViewById<TextView>(R.id.questTime)?.text = time
        dialogView.findViewById<TextView>(R.id.questMistakes)?.text = mistakes.toString()
        
        // Dynamic star display: ⭐ for earned, ☆ for unearned
        val starsText = dialogView.findViewById<TextView>(R.id.questStars)
        val starDisplay = "⭐".repeat(starsEarned) + "☆".repeat(3 - starsEarned)
        starsText?.text = starDisplay
        
        // Show total star collection progress
        val totalStars = questCodex.getTotalStars()
        val maxStars = questCodex.getMaxStars()
        val starProgress = (totalStars * 100) / maxStars
        
        // Update congratulations message with star collection info
        val messageText = dialogView.findViewById<TextView>(R.id.congratulationsMessage)
        val motivationalMessage = questCodex.getMotivationalMessage()
        messageText?.text = "You now have $totalStars/$maxStars stars!\n$motivationalMessage"
        
        // Check if next puzzle is unlocked
        val puzzleChain = questCodex.getPuzzleChain(realmId ?: "")
        val nextPuzzleUnlocked = puzzle?.let { p ->
            val nextPuzzle = puzzleChain?.puzzles?.find { it.puzzleNumber == p.puzzleNumber + 1 }
            nextPuzzle?.isUnlocked == true
        } ?: false
        
        if (nextPuzzleUnlocked) {
            dialogView.findViewById<TextView>(R.id.nextPuzzleMessage)?.visibility = android.view.View.VISIBLE
        }
        
        // Check if reached a milestone
        val (milestoneMessage, remaining) = questCodex.getNextMilestone()
        if (remaining == 0 && totalStars > 0) {
            // Just reached a milestone!
            messageText?.text = "🎉 MILESTONE REACHED! 🎉\n$milestoneMessage\n\nYou have $totalStars/$maxStars stars!"
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // Make background transparent
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        // Setup button click listener
        dialogView.findViewById<Button>(R.id.btnRealmMap)?.setOnClickListener {
            dialog.dismiss()
            // Return to realm quest
            if (realmId != null) {
                val intent = Intent(this, RealmQuestActivity::class.java).apply {
                    putExtra("realm_id", realmId)
                }
                startActivity(intent)
                finish()
            }
        }
        
        // Animate dialog entrance
        dialog.setOnShowListener {
            animateQuestVictoryDialog(dialogView)
        }
        
        dialog.show()
    }
    
    private fun animateQuestVictoryDialog(dialogView: android.view.View) {
        val overlay = dialogView.findViewById<android.view.View>(R.id.victoryOverlay)
        val card = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.victoryCard)
        val trophyContainer = dialogView.findViewById<FrameLayout>(R.id.trophyContainer)
        val trophyIcon = dialogView.findViewById<ImageView>(R.id.trophyIcon)
        val trophyGlow = dialogView.findViewById<android.view.View>(R.id.trophyGlow)
        val title = dialogView.findViewById<TextView>(R.id.congratulationsTitle)
        val message = dialogView.findViewById<TextView>(R.id.congratulationsMessage)
        val statsContainer = dialogView.findViewById<LinearLayout>(R.id.statsContainer)
        val button = dialogView.findViewById<Button>(R.id.btnRealmMap)
        val nextMessage = dialogView.findViewById<TextView>(R.id.nextPuzzleMessage)
        
        // Animate overlay fade in
        overlay?.animate()?.alpha(1f)?.setDuration(300)?.start()
        
        // Animate card entrance (scale + fade)
        card?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(400)
            ?.setInterpolator(android.view.animation.OvershootInterpolator())
            ?.start()
        
        // Animate trophy with bounce
        trophyIcon?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setStartDelay(200)
            ?.setDuration(600)
            ?.setInterpolator(android.view.animation.OvershootInterpolator())
            ?.withEndAction {
                // Pulsing glow effect
                trophyGlow?.animate()
                    ?.alpha(0.6f)
                    ?.scaleX(1.2f)
                    ?.scaleY(1.2f)
                    ?.setDuration(1000)
                    ?.setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                    ?.withEndAction {
                        trophyGlow?.animate()
                            ?.alpha(0.3f)
                            ?.scaleX(1f)
                            ?.scaleY(1f)
                            ?.setDuration(1000)
                            ?.withEndAction {
                                // Loop back
                                trophyGlow?.animate()
                                    ?.alpha(0.6f)
                                    ?.scaleX(1.2f)
                                    ?.scaleY(1.2f)
                                    ?.setDuration(1000)
                                    ?.start()
                            }
                            ?.start()
                    }
                    ?.start()
                
                // Trophy rotation animation
                trophyIcon?.animate()
                    ?.rotation(360f)
                    ?.setDuration(2000)
                    ?.setInterpolator(android.view.animation.LinearInterpolator())
                    ?.start()
                
                // Confetti stars animation
                animateConfettiStars(trophyContainer)
            }
        
        // Animate text elements (staggered)
        title?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(400)
            ?.setDuration(400)
            ?.start()
        
        message?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(500)
            ?.setDuration(400)
            ?.start()
        
        statsContainer?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(600)
            ?.setDuration(400)
            ?.start()
        
        if (nextMessage?.visibility == android.view.View.VISIBLE) {
            nextMessage?.animate()
                ?.alpha(1f)
                ?.setStartDelay(700)
                ?.setDuration(400)
                ?.start()
        }
        
        button?.animate()
            ?.alpha(1f)
            ?.setStartDelay(800)
            ?.setDuration(400)
            ?.start()
    }
    
    private fun animateConfettiStars(container: FrameLayout?) {
        container ?: return
        
        val star1 = container.findViewById<TextView>(R.id.confettiStar1)
        val star2 = container.findViewById<TextView>(R.id.confettiStar2)
        val star3 = container.findViewById<TextView>(R.id.confettiStar3)
        val star4 = container.findViewById<TextView>(R.id.confettiStar4)
        
        val stars = listOfNotNull(star1, star2, star3, star4)
        
        stars.forEachIndexed { index, star ->
            val delay = 800 + (index * 100).toLong()
            
            star.animate()
                ?.alpha(1f)
                ?.scaleX(1.5f)
                ?.scaleY(1.5f)
                ?.rotation(720f)
                ?.setStartDelay(delay)
                ?.setDuration(1000)
                ?.withEndAction {
                    star.animate()
                        ?.alpha(0f)
                        ?.scaleX(0.5f)
                        ?.scaleY(0.5f)
                        ?.setDuration(500)
                        ?.start()
                }
                ?.start()
        }
    }

    // Disable phone back button - use UI back button instead
    override fun onBackPressed() {
        // Show dialog when back button is pressed
        showBackToMenuDialog()
    }
    
}