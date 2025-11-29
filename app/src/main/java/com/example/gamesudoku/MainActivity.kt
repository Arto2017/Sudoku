package com.artashes.sudoku

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
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.graphics.drawable.ColorDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.animation.ObjectAnimator
import android.util.TypedValue
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.LinearInterpolator
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.*
import android.os.Build
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat


class MainActivity : AppCompatActivity() {
    private lateinit var sudokuBoard: SudokuBoardView
    private lateinit var timerText: TextView
    private lateinit var titleText: TextView
    private lateinit var statsManager: StatsManager
    private lateinit var soundManager: SoundManager
    private lateinit var audioManager: AudioManager
    private lateinit var adManager: AdManager
    private var bannerAdView: AdView? = null
    private var secondsElapsed = 0
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var gameStarted = false // Track if player has started playing (separate from timer running state)
    private var boardSize = 9
    private var selectedNumber = 1
    private var currentDifficulty = SudokuGenerator.Difficulty.EASY
    private var totalMistakes = 0
    private var currentMaxMistakes = 0 // Current max mistakes (increases after each ad watch)
    private lateinit var attemptStore: AttemptStateStore
    private var questCodex: QuestCodex? = null
    private var playNowStateManager: PlayNowStateManager? = null
    private var restoredPlayNowState: PlayNowStateManager.PlayNowState? = null

    private var gameResultSaved = false // Flag to prevent duplicate saves
    private var questPuzzleId: String? = null // Current quest puzzle ID
    private var realmId: String? = null // Current realm ID
    private var isQuickPlay = false // Flag to indicate if this is Quick Play mode
    

    companion object {
        private const val KEY_SECONDS = "secondsElapsed"
        private const val KEY_BOARD = "boardState"
        private const val KEY_FIXED = "fixedState"
        const val EXTRA_CONTINUE_PLAY_NOW = "continue_play_now"
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

    private fun parseDifficulty(value: String?): SudokuGenerator.Difficulty {
        return when (value) {
            "EASY" -> SudokuGenerator.Difficulty.EASY
            "MEDIUM" -> SudokuGenerator.Difficulty.MEDIUM
            "HARD" -> SudokuGenerator.Difficulty.HARD
            "EXPERT" -> SudokuGenerator.Difficulty.EXPERT
            else -> SudokuGenerator.Difficulty.EASY
        }
    }

    private fun restoreQuestPuzzleStateIfNeeded() {
        val puzzleId = questPuzzleId ?: return
        val codex = questCodex ?: QuestCodex(this).also { questCodex = it }
        val savedState = codex.loadPuzzleBoardState(puzzleId) ?: return

        try {
            val boardArray = savedState.board.map { it.toIntArray() }.toTypedArray()
            val fixedArray = savedState.fixed.map { it.toBooleanArray() }.toTypedArray()

            if (boardArray.size != boardSize || fixedArray.size != boardSize) {
                codex.clearPuzzleBoardState(puzzleId)
                attemptStore.clear(puzzleId)
                return
            }

            sudokuBoard.setBoardState(boardArray, fixedArray)
            
            // Recalculate correctness after restoring board state
            // Solution board should already be set from resetPuzzle() call above
            sudokuBoard.recalculateCorrectness()
            
            secondsElapsed = savedState.secondsElapsed
            totalMistakes = savedState.mistakes
            attemptStore.setMistakes(puzzleId, totalMistakes)
            sudokuBoard.setHintsState(
                savedRemaining = savedState.hintsRemaining,
                savedUsed = savedState.hintsUsed,
                savedMax = savedState.maxHints
            )
            updateHintBadge()
            updateTimerText()

            val hasProgress = hasUserPlacedNumbers(boardArray, fixedArray)
            gameStarted = hasProgress || secondsElapsed > 0
        } catch (e: Exception) {
            codex.clearPuzzleBoardState(puzzleId)
            attemptStore.clear(puzzleId)
        }
    }

    private fun restorePlayNowStateIfNeeded(state: PlayNowStateManager.PlayNowState?) {
        val savedState = state ?: return

        try {
            val boardArray = savedState.board.map { it.toIntArray() }.toTypedArray()
            val fixedArray = savedState.fixed.map { it.toBooleanArray() }.toTypedArray()

            if (boardArray.size != boardSize || fixedArray.size != boardSize) {
                playNowStateManager?.clearState()
                return
            }

            sudokuBoard.setBoardState(boardArray, fixedArray)

            val solutionArray = savedState.solution?.toIntArray()
            if (solutionArray != null && solutionArray.size == boardSize * boardSize) {
                sudokuBoard.setSolutionBoard(solutionArray)
                // Recalculate correctness after solution is set
                sudokuBoard.recalculateCorrectness()
            }

            sudokuBoard.setHintsState(
                savedRemaining = savedState.hintsRemaining,
                savedUsed = savedState.hintsUsed,
                savedMax = savedState.maxHints
            )
            updateHintBadge()

            secondsElapsed = savedState.secondsElapsed
            totalMistakes = savedState.mistakes
            updateMistakesHud(totalMistakes)
            updateTimerText()

            selectedNumber = savedState.selectedNumber
            if (selectedNumber in 1..boardSize) {
                sudokuBoard.highlightNumber(selectedNumber)
                highlightActiveNumber(selectedNumber)
            } else {
                selectedNumber = 0
                highlightActiveNumber(0)
            }

            val hasProgress = hasUserPlacedNumbers(boardArray, fixedArray)
            gameStarted = savedState.gameStarted || hasProgress || secondsElapsed > 0
        } catch (e: Exception) {
            playNowStateManager?.clearState()
        }
    }

    private fun persistQuestPuzzleState() {
        val puzzleId = questPuzzleId ?: return
        val codex = questCodex ?: QuestCodex(this).also { questCodex = it }

        if (gameResultSaved || sudokuBoard.isBoardComplete()) {
            codex.clearPuzzleBoardState(puzzleId)
            return
        }

        val boardState = sudokuBoard.getBoardState()
        val fixedState = sudokuBoard.getFixedState()
        val hintsRemaining = sudokuBoard.getHintsRemaining()
        val hintsUsed = sudokuBoard.getHintsUsed()
        val maxHints = sudokuBoard.getMaxHintsPerGame()
        val hasProgress = hasUserPlacedNumbers(boardState, fixedState) || secondsElapsed > 0 || totalMistakes > 0

        if (!hasProgress) {
            codex.clearPuzzleBoardState(puzzleId)
            return
        }

        codex.savePuzzleBoardState(
            puzzleId,
            boardState,
            fixedState,
            secondsElapsed,
            totalMistakes,
            hintsRemaining,
            hintsUsed,
            maxHints
        )
    }

    private fun hasUserPlacedNumbers(
        boardState: Array<IntArray>,
        fixedState: Array<BooleanArray>
    ): Boolean {
        for (row in boardState.indices) {
            for (col in boardState[row].indices) {
                val isFixed = if (row < fixedState.size && col < fixedState[row].size) {
                    fixedState[row][col]
                } else {
                    false
                }
                if (!isFixed && boardState[row][col] != 0) {
                    return true
                }
            }
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set window background to match game background (prevent wrong color in status bar area)
        window.setBackgroundDrawableResource(R.drawable.parchment_background)
        
        // Enable fullscreen/immersive mode
        enableFullscreen()
        
        setContentView(R.layout.activity_main)

        playNowStateManager = if (questPuzzleId == null) PlayNowStateManager(this) else null

        val continuePlayNow = questPuzzleId == null && intent.getBooleanExtra(EXTRA_CONTINUE_PLAY_NOW, false)

        // Get board size from intent (may be overridden if continuing)
        boardSize = intent.getIntExtra("board_size", intent.getIntExtra(MainMenuActivity.EXTRA_BOARD_SIZE, 9))
        questPuzzleId = intent.getStringExtra("quest_puzzle_id")
        realmId = intent.getStringExtra("realm_id")
        questPuzzleId?.let {
            questCodex = QuestCodex(this)
        }
        
        // Set difficulty from intent (for Quick Play) or quest puzzle
        val difficultyString = intent.getStringExtra("difficulty") ?: intent.getStringExtra(QuickPlayActivity.EXTRA_DIFFICULTY)
        isQuickPlay = difficultyString != null && questPuzzleId == null
        if (difficultyString != null) {
            currentDifficulty = parseDifficulty(difficultyString)
        }

        if (continuePlayNow) {
            restoredPlayNowState = playNowStateManager?.loadState()
            restoredPlayNowState?.let { state ->
                boardSize = state.boardSize
                currentDifficulty = parseDifficulty(state.difficulty)
                isQuickPlay = state.isQuickPlay
            }
        } else if (questPuzzleId == null) {
            playNowStateManager?.clearState()
        }

        // Initialize stats manager
        statsManager = StatsManager(this)
        attemptStore = AttemptStateStore(this)
        soundManager = SoundManager.getInstance(this)
        audioManager = AudioManager.getInstance(this)
        
        // Initialize AdMob and load ads
        adManager = AdManager(this)
        adManager.loadInterstitialAd()
        adManager.loadRewardedAd()
        
        // Load banner ad - delay until view is attached to window
        bannerAdView = findViewById(R.id.bannerAdView)
        bannerAdView?.let { adView ->
            // Post to ensure view is fully attached and laid out
            adView.post {
                try {
                    adManager.loadBannerAd(adView)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error loading banner ad: ${e.message}", e)
                }
            }
        }
        
        // Preload rewarded ad if this is a 9x9 Hard/Expert Quick Play game
        if (isQuickPlay && boardSize == 9 && 
            (currentDifficulty == SudokuGenerator.Difficulty.HARD || 
             currentDifficulty == SudokuGenerator.Difficulty.EXPERT)) {
            android.util.Log.d("MainActivity", "Preloading rewarded ad for 9x9 ${currentDifficulty.name} Quick Play")
            // Ensure rewarded ad is loaded (already loading above, but this ensures it's prioritized)
            adManager.loadRewardedAd()
        }

        sudokuBoard = findViewById(R.id.sudokuBoard)
        timerText = findViewById(R.id.timerText)
        titleText = findViewById(R.id.titleText)
        val titleBanner = findViewById<LinearLayout>(R.id.titleBanner)
        
        // Initialize hint badge
        updateHintBadge()

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
        } else if (restoredPlayNowState != null) {
            // State restoration will handle the board setup
        } else if (isQuickPlay) {
            sudokuBoard.resetPuzzle(currentDifficulty)
        } else {
            sudokuBoard.resetPuzzle()
        }

        restoreQuestPuzzleStateIfNeeded()
        
        // Set up cell selection listener to update selected number and highlight when cell is clicked
        sudokuBoard.setOnCellSelectedListener(object : SudokuBoardView.OnCellSelectedListener {
            override fun onCellSelected(row: Int, col: Int, isEditable: Boolean) {
                // When a cell is clicked, update selected number and highlight all matching numbers
                val cellValue = sudokuBoard.getBoardValue(row, col)
                if (cellValue != 0) {
                    selectedNumber = cellValue
                    highlightActiveNumber(cellValue)
                    // highlightNumber is already called in onTouchEvent, but ensure it's called here too
                    sudokuBoard.highlightNumber(cellValue)
                } else {
                    // Empty cell - clear number highlighting
                    selectedNumber = 0
                    highlightActiveNumber(0)
                }
            }
        })
        
        // Set up conflict listener
        sudokuBoard.setOnConflictListener(object : SudokuBoardView.OnConflictListener {
            override fun onConflictDetected() {
                // Play error sound
                soundManager.playError()
                
                // Increment mistake counter
                val previousMistakes = totalMistakes
                questPuzzleId?.let { id ->
                    totalMistakes = attemptStore.incrementMistakes(id)
                } ?: run {
                    totalMistakes++
                }
                // Ensure max mistakes is initialized
                if (currentMaxMistakes <= 0) {
                    initializeMaxMistakes()
                }
                // Get fresh max from store
                val defaultMax = getInitialMaxMistakes()
                val maxMistakes = questPuzzleId?.let { id ->
                    val storedMax = attemptStore.getMaxMistakes(id, defaultMax)
                    currentMaxMistakes = if (storedMax > 0) storedMax else defaultMax
                    currentMaxMistakes
                } ?: currentMaxMistakes.let { if (it > 0) it else defaultMax }
                currentMaxMistakes = maxMistakes
                Log.d("MainActivity", "Mistake detected! Previous: $previousMistakes, New total: $totalMistakes, Max: $maxMistakes")
                
                // Update UI immediately - we're already on main thread from conflict detection
                val text = findViewById<TextView>(R.id.mistakesText)
                if (text != null) {
                    text.text = "$totalMistakes / $maxMistakes"
                    Log.d("MainActivity", "Updated TextView directly to: ${text.text}")
                } else {
                    Log.e("MainActivity", "mistakesText TextView is null!")
                }
                
                // Also call the update function for color changes
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

                // Check if mistakes reached the limit
                if (totalMistakes >= maxMistakes) {
                    showMistakesExhaustedDialog()
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

        if (questPuzzleId == null) {
            restorePlayNowStateIfNeeded(restoredPlayNowState)
            restoredPlayNowState = null
        }

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
            // Pass flag if this is a quest game
            if (questPuzzleId != null) {
                intent.putExtra("from_quest_game", true)
            }
            startActivity(intent)
        }

        // Start timer
        startTimer()
        updateProgress()
        // Initialize max mistakes
        initializeMaxMistakes()
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

    private fun persistPlayNowState() {
        if (questPuzzleId != null) return
        val manager = playNowStateManager ?: return

        if (gameResultSaved || sudokuBoard.isBoardComplete()) {
            manager.clearState()
            return
        }

        val boardState = sudokuBoard.getBoardState()
        val fixedState = sudokuBoard.getFixedState()
        val hasProgress = hasUserPlacedNumbers(boardState, fixedState) || secondsElapsed > 0 || totalMistakes > 0

        if (!hasProgress) {
            manager.clearState()
            return
        }

        manager.saveState(
            boardSize = boardSize,
            difficulty = currentDifficulty,
            isQuickPlay = isQuickPlay,
            secondsElapsed = secondsElapsed,
            mistakes = totalMistakes,
            hintsRemaining = sudokuBoard.getHintsRemaining(),
            hintsUsed = sudokuBoard.getHintsUsed(),
            maxHints = sudokuBoard.getMaxHintsPerGame(),
            board = boardState,
            fixed = fixedState,
            solution = sudokuBoard.getSolutionForSaving(),
            selectedNumber = selectedNumber,
            gameStarted = gameStarted
        )
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
                    
                    // Always highlight all cells with this number (even if no cell is selected)
                    sudokuBoard.highlightNumber(i)
                    highlightActiveNumber(i)
                    
                    // Only place the number if a cell is selected
                    val selectedRow = sudokuBoard.getSelectedRow()
                    val selectedCol = sudokuBoard.getSelectedCol()
                    if (selectedRow != -1 && selectedCol != -1) {
                        sudokuBoard.setNumber(i)
                        startTimer()
                        updateProgress()
                        
                        // Check for completion after placing number
                        if (sudokuBoard.isBoardComplete()) {
                            checkVictory()
                        }
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
            // Check if selected cell has a correct number - if yes, show message
            val selectedRow = sudokuBoard.getSelectedRow()
            val selectedCol = sudokuBoard.getSelectedCol()
            if (selectedRow != -1 && selectedCol != -1) {
                val cellValue = sudokuBoard.getBoardValue(selectedRow, selectedCol)
                // Check if cell is not empty and has a correct number (fixed, hint-revealed, or correct user-entered)
                if (cellValue != 0 && sudokuBoard.isCellCorrect(selectedRow, selectedCol)) {
                    // Cell has a correct number - show message
                    showTooltip(hintButton, "Please choose an empty box")
                    return@setOnClickListener
                }
            }
            
            // Check if there are incorrect numbers on the board
            if (sudokuBoard.hasIncorrectUserEnteredNumbers()) {
                // Always show dialog when there are incorrect numbers
                showHintWithAutoFixDialog()
            } else {
                // No incorrect numbers - proceed with normal hint flow
                val hasFreeHints = sudokuBoard.getHintsRemaining() > 0
                if (hasFreeHints) {
                    // Use hint normally
                    if (sudokuBoard.revealHint()) {
                        soundManager.playClick()
                        startTimer() // Start timer when hint is used (player is playing)
                        updateProgress()
                        
                        // Highlight the number that was placed by the hint (same as manual placement)
                        val placedNumber = sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol())
                        if (placedNumber > 0) {
                            sudokuBoard.highlightNumber(placedNumber)
                            selectedNumber = placedNumber
                            highlightActiveNumber(placedNumber)
                        }
                        
                        // Update badge after using hint
                        updateHintBadge()
                        
                        // Check for completion after revealing hint (in case hint fills last cell)
                        if (sudokuBoard.isBoardComplete()) {
                            checkVictory()
                        }
                    } else {
                        // Show error message
                        val errorMsg = sudokuBoard.getLastHintErrorMessage()
                        val message = when {
                            errorMsg != null -> errorMsg
                            sudokuBoard.getSelectedRow() == -1 || sudokuBoard.getSelectedCol() == -1 -> "Select cell first"
                            sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol()) != 0 -> "Cell not empty"
                            else -> "Cannot hint"
                        }
                        showTooltip(hintButton, message)
                    }
                } else {
                    // No free hints remaining - show ad directly
                    showRewardedAdForHint(hintButton)
                }
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

    private fun showHintAddedAnimation(hintButton: Button) {
        // Use post to ensure button is laid out before getting position
        hintButton.post {
            // Create a TextView for the "+1" text
            val plusOneText = TextView(this).apply {
                text = "+1"
                setTextColor(Color.parseColor("#4CAF50")) // Green color for positive feedback
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTypeface(Typeface.create("serif", Typeface.BOLD))
                gravity = Gravity.CENTER
            }
            
            // Get the root view to add overlay
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            
            // Get button position relative to root
            val buttonLocation = IntArray(2)
            hintButton.getLocationOnScreen(buttonLocation)
            val rootLocation = IntArray(2)
            rootView.getLocationOnScreen(rootLocation)
            
            // Create layout params to position the text on top of the button
            val layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                // Position at the center of the button
                leftMargin = buttonLocation[0] - rootLocation[0] + (hintButton.width / 2) - 20
                topMargin = buttonLocation[1] - rootLocation[1] + (hintButton.height / 2) - 15
            }
            
            rootView.addView(plusOneText, layoutParams)
            
            // Set initial state (invisible, slightly below)
            plusOneText.alpha = 0f
            plusOneText.translationY = 10f
            plusOneText.scaleX = 0.5f
            plusOneText.scaleY = 0.5f
            
            // Animate: fade in, scale up, move up slightly
            plusOneText.animate()
                .alpha(1f)
                .translationY(-20f)
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator())
                .withEndAction {
                    // Hold for a moment, then fade out
                    plusOneText.animate()
                        .alpha(0f)
                        .translationY(-40f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .setStartDelay(1700) // Show for 2 seconds total (300ms in + 1700ms hold)
                        .setDuration(300)
                        .withEndAction {
                            // Remove the view
                            rootView.removeView(plusOneText)
                        }
                        .start()
                }
                .start()
        }
    }

    private fun showHintWithAutoFixDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_hint_auto_fix, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(R.drawable.fantasy_dialog_background)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        val continueButton = dialogView.findViewById<Button>(R.id.btnContinue)
        val cancelButton = dialogView.findViewById<Button>(R.id.btnCancel)
        
        continueButton.setOnClickListener {
            dialog.dismiss()
            soundManager.playClick()
            
            // Check if there are free hints remaining
            val hasFreeHints = sudokuBoard.getHintsRemaining() > 0
            if (hasFreeHints) {
                // Has free hints - use free hint with auto-fix (no ad)
                if (sudokuBoard.revealHintWithAutoFix()) {
                    startTimer() // Start timer when hint is used (player is playing)
                    updateProgress()
                    
                    // Highlight the number that was placed by the hint
                    val placedNumber = sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol())
                    if (placedNumber > 0) {
                        sudokuBoard.highlightNumber(placedNumber)
                        selectedNumber = placedNumber
                        highlightActiveNumber(placedNumber)
                    }
                    
                    updateHintBadge()
                    
                    // Check for completion
                    if (sudokuBoard.isBoardComplete()) {
                        checkVictory()
                    }
                }
            } else {
                // No free hints remaining - show ad first, then remove incorrect numbers and place hint
                showRewardedAdForHintWithAutoFix()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
            soundManager.playClick()
        }
        
        dialog.show()
    }
    
    private fun showBackToMenuDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_back_to_menu, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        val messageView = view.findViewById<TextView>(R.id.dialogMessage)
        if (questPuzzleId != null) {
            messageView?.text = "Are you sure you want to leave? Your quest progress is safely saved and you can resume from the realm menu."
        } else {
            messageView?.text = "Leave the game? Your progress is saved automatically — use Continue on the main menu to resume later."
        }

        // Make background transparent around card for nicer presentation
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        view.findViewById<Button>(R.id.dialogCancel).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<Button>(R.id.dialogConfirm).setOnClickListener {
            dialog.dismiss()
            // For quest puzzles, go back to realm/level window instead of main menu
            persistPlayNowState()
            persistQuestPuzzleState()
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
    
    private fun showRewardedAdForHintWithAutoFix() {
        // Show rewarded ad, then remove incorrect numbers and place hint after ad is watched
        val hintButton = findViewById<Button>(R.id.revealHintButton)
        if (adManager.isRewardedAdLoaded()) {
            adManager.showRewardedAd(this, onAdClosed = {
                // After ad is watched, grant 1 hint
                sudokuBoard.grantHint()
                
                // Remove incorrect numbers and place hint
                if (sudokuBoard.revealHintWithAutoFix()) {
                    startTimer()
                    updateProgress()
                    
                    // Highlight the number that was placed by the hint
                    val placedNumber = sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol())
                    if (placedNumber > 0) {
                        sudokuBoard.highlightNumber(placedNumber)
                        selectedNumber = placedNumber
                        highlightActiveNumber(placedNumber)
                    }
                    
                    updateHintBadge()
                    
                    // Check for completion
                    if (sudokuBoard.isBoardComplete()) {
                        checkVictory()
                    }
                }
                // Preload next rewarded ad
                adManager.loadRewardedAd()
            }, onUserEarnedReward = {
                // Reward earned callback - hint is granted in onAdClosed
            })
        } else {
            // Ad not loaded - show loading message and load with callback
            showTooltip(hintButton, "Loading ad...")
            
            // Track if ad was shown to prevent multiple callbacks
            var adShown = false
            
            // Create the callback for when ad is loaded
            val onAdLoadedCallback: () -> Unit = {
                if (!adShown && adManager.isRewardedAdLoaded()) {
                    adShown = true
                    // Automatically show the ad when it's loaded
                    adManager.showRewardedAd(this, onAdClosed = {
                        // After ad is watched, grant 1 hint
                        sudokuBoard.grantHint()
                        
                        // Remove incorrect numbers and place hint
                        if (sudokuBoard.revealHintWithAutoFix()) {
                            startTimer()
                            updateProgress()
                            
                            // Highlight the number that was placed by the hint
                            val placedNumber = sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol())
                            if (placedNumber > 0) {
                                sudokuBoard.highlightNumber(placedNumber)
                                selectedNumber = placedNumber
                                highlightActiveNumber(placedNumber)
                            }
                            
                            updateHintBadge()
                            
                            // Check for completion
                            if (sudokuBoard.isBoardComplete()) {
                                checkVictory()
                            }
                        }
                        // Preload next rewarded ad
                        adManager.loadRewardedAd()
                    }, onUserEarnedReward = {
                        // Reward earned callback - hint is granted in onAdClosed
                    })
                }
            }
            
            // Load ad with callback
            adManager.loadRewardedAd(onAdLoadedCallback)
            
            // Set up timeout fallback (10 seconds)
            handler.postDelayed({
                if (!adShown) {
                    if (adManager.isRewardedAdLoaded()) {
                        // Ad loaded but callback didn't fire - show it now
                        adShown = true
                        onAdLoadedCallback()
                    } else {
                        // Ad failed to load after timeout
                        showTooltip(hintButton, "Ad failed to load. Please try again.")
                    }
                }
            }, 10000) // 10 second timeout
        }
    }
    
    private fun showRewardedAdForHintAfterCorrectNumber(hintButton: Button) {
        // Show rewarded ad after player placed a correct number and wants a hint
        if (adManager.isRewardedAdLoaded()) {
            adManager.showRewardedAd(this, onAdClosed = {
                // After ad is watched, grant 1 hint
                sudokuBoard.grantHint()
                
                // Update badge after granting hint
                updateHintBadge()
                
                // Check if a cell is selected - if yes, use the hint immediately
                val hasSelectedCell = sudokuBoard.getSelectedRow() != -1 && sudokuBoard.getSelectedCol() != -1
                if (hasSelectedCell) {
                    // No incorrect numbers check here - already handled before showing ad
                    if (sudokuBoard.revealHint()) {
                        soundManager.playClick()
                        startTimer() // Start timer when hint is used (player is playing)
                        updateProgress()
                        
                        // Highlight the number that was placed by the hint (same as manual placement)
                        val placedNumber = sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol())
                        if (placedNumber > 0) {
                            sudokuBoard.highlightNumber(placedNumber)
                            selectedNumber = placedNumber
                            highlightActiveNumber(placedNumber)
                        }
                        
                        // Update badge after using hint
                        updateHintBadge()
                        
                        // Check for completion after revealing hint (in case hint fills last cell)
                        if (sudokuBoard.isBoardComplete()) {
                            checkVictory()
                        }
                    }
                } else {
                    // No cell selected or hint couldn't be used - badge already updated
                    // Hint is granted and ready for next use
                }
            }, onUserEarnedReward = {
                // Reward earned callback - hint is granted in onAdClosed
            })
        } else {
            // Ad not loaded - try to load it
            showTooltip(hintButton, "Loading ad...")
            
            var adShown = false
            val onAdLoadedCallback = {
                if (!adShown && adManager.isRewardedAdLoaded()) {
                    adShown = true
                    adManager.showRewardedAd(this, onAdClosed = {
                        // After ad is watched, grant 1 hint
                        sudokuBoard.grantHint()
                        
                        // Update badge after granting hint
                        updateHintBadge()
                        
                        // Check if a cell is selected - if yes, use the hint immediately
                        val hasSelectedCell = sudokuBoard.getSelectedRow() != -1 && sudokuBoard.getSelectedCol() != -1
                        if (hasSelectedCell) {
                            // No incorrect numbers check here - already handled before showing ad
                            if (sudokuBoard.revealHint()) {
                                soundManager.playClick()
                                startTimer() // Start timer when hint is used (player is playing)
                                updateProgress()
                                
                                // Highlight the number that was placed by the hint (same as manual placement)
                                val placedNumber = sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol())
                                if (placedNumber > 0) {
                                    sudokuBoard.highlightNumber(placedNumber)
                                    selectedNumber = placedNumber
                                    highlightActiveNumber(placedNumber)
                                }
                                
                                // Update badge after using hint
                                updateHintBadge()
                                
                                // Check for completion after revealing hint (in case hint fills last cell)
                                if (sudokuBoard.isBoardComplete()) {
                                    checkVictory()
                                }
                            }
                        } else {
                            // No cell selected or hint couldn't be used - badge already updated
                            // Hint is granted and ready for next use
                        }
                    }, onUserEarnedReward = {
                        // Reward earned callback - hint is granted in onAdClosed
                    })
                }
            }
            
            // Load ad with callback
            adManager.loadRewardedAd(onAdLoadedCallback)
            
            // Set up timeout fallback (10 seconds)
            handler.postDelayed({
                if (!adShown) {
                    if (adManager.isRewardedAdLoaded()) {
                        // Ad loaded but callback didn't fire - show it now
                        adShown = true
                        onAdLoadedCallback()
                    } else {
                        // Ad failed to load after timeout
                        showTooltip(hintButton, "Ad failed to load. Please try again.")
                    }
                }
            }, 10000) // 10 second timeout
        }
    }
    
    private fun showRewardedAdForHint(hintButton: Button) {
        // Show rewarded ad directly (no dialog)
        if (adManager.isRewardedAdLoaded()) {
            adManager.showRewardedAd(this, onAdClosed = {
                // After ad is watched, grant 1 hint
                sudokuBoard.grantHint()
                
                // Update badge after granting hint
                updateHintBadge()
                
                // Check if a cell is selected - if yes, use the hint immediately
                val hasSelectedCell = sudokuBoard.getSelectedRow() != -1 && sudokuBoard.getSelectedCol() != -1
                if (hasSelectedCell) {
                    // No incorrect numbers check here - already handled before showing ad
                    if (sudokuBoard.revealHint()) {
                        soundManager.playClick()
                        startTimer() // Start timer when hint is used (player is playing)
                        updateProgress()
                        
                        // Highlight the number that was placed by the hint (same as manual placement)
                        val placedNumber = sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol())
                        if (placedNumber > 0) {
                            sudokuBoard.highlightNumber(placedNumber)
                            selectedNumber = placedNumber
                            highlightActiveNumber(placedNumber)
                        }
                        
                        // Update badge after using hint
                        updateHintBadge()
                        
                        // Check for completion after revealing hint (in case hint fills last cell)
                        if (sudokuBoard.isBoardComplete()) {
                            checkVictory()
                        }
                    }
                } else {
                    // No cell selected or hint couldn't be used - badge already updated
                }
                // Preload next rewarded ad
                adManager.loadRewardedAd()
            }, onUserEarnedReward = {
                // Reward earned callback - hint is granted in onAdClosed
            })
        } else {
            // Ad not loaded - show loading message and load with callback
            showTooltip(hintButton, "Loading ad...")
            
            // Track if ad was shown to prevent multiple callbacks
            var adShown = false
            
            // Create the callback for when ad is loaded
            val onAdLoadedCallback: () -> Unit = {
                if (!adShown && adManager.isRewardedAdLoaded()) {
                    adShown = true
                    // Automatically show the ad when it's loaded
                    adManager.showRewardedAd(this, onAdClosed = {
                        // After ad is watched, grant 1 hint
                        sudokuBoard.grantHint()
                        
                        // Update badge after granting hint
                        updateHintBadge()
                        
                        // Check if a cell is selected - if yes, use the hint immediately
                        val hasSelectedCell = sudokuBoard.getSelectedRow() != -1 && sudokuBoard.getSelectedCol() != -1
                        if (hasSelectedCell) {
                            // No incorrect numbers check here - already handled before showing ad
                            if (sudokuBoard.revealHint()) {
                                soundManager.playClick()
                                startTimer() // Start timer when hint is used (player is playing)
                                updateProgress()
                                
                                // Highlight the number that was placed by the hint (same as manual placement)
                                val placedNumber = sudokuBoard.getBoardValue(sudokuBoard.getSelectedRow(), sudokuBoard.getSelectedCol())
                                if (placedNumber > 0) {
                                    sudokuBoard.highlightNumber(placedNumber)
                                    selectedNumber = placedNumber
                                    highlightActiveNumber(placedNumber)
                                }
                                
                                // Update badge after using hint
                                updateHintBadge()
                                
                                // Check for completion after revealing hint (in case hint fills last cell)
                                if (sudokuBoard.isBoardComplete()) {
                                    checkVictory()
                                }
                            }
                        } else {
                            // No cell selected or hint couldn't be used - badge already updated
                        }
                        // Preload next rewarded ad
                        adManager.loadRewardedAd()
                    }, onUserEarnedReward = {
                        // Reward earned callback - hint is granted in onAdClosed
                    })
                }
            }
            
            // Load ad with callback
            adManager.loadRewardedAd(onAdLoadedCallback)
            
            // Set up timeout fallback (10 seconds)
            handler.postDelayed({
                if (!adShown) {
                    if (adManager.isRewardedAdLoaded()) {
                        // Ad loaded but callback didn't fire - show it now
                        adShown = true
                        onAdLoadedCallback()
                    } else {
                        // Ad failed to load after timeout
                        showTooltip(hintButton, "Ad failed to load. Please try again.")
                    }
                }
            }, 10000) // 10 second timeout
        }
    }
    
    private fun updateHintBadge() {
        val hintBadge = findViewById<TextView>(R.id.hintBadge)
        val hintsRemaining = sudokuBoard.getHintsRemaining()
        
        if (hintsRemaining > 0) {
            // Show number badge (no background)
            hintBadge?.apply {
                text = hintsRemaining.toString()
                background = null
                setTextColor(Color.parseColor("#4CAF50")) // Green color
                visibility = View.VISIBLE
            }
        } else {
            // Show ad icon (no background)
            hintBadge?.apply {
                text = "📺"  // TV icon to indicate ad/video
                background = null
                setTextColor(Color.parseColor("#FF6B35")) // Orange color for ad
                visibility = View.VISIBLE
            }
        }
    }

    private fun startTimer() {
        if (!running) {
            running = true
            gameStarted = true // Mark that player has started playing
            handler.post(tickRunnable)
        }
    }

    private fun getInitialMaxMistakes(): Int {
        return if (boardSize == 6) 2 else 3
    }
    
    private fun getMaxMistakes(): Int {
        // Always get fresh value from store to ensure it's up to date
        val defaultMax = getInitialMaxMistakes()
        val storedMax = questPuzzleId?.let { id ->
            attemptStore.getMaxMistakes(id, defaultMax)
        } ?: defaultMax
        // Update cached value
        currentMaxMistakes = if (storedMax > 0) storedMax else defaultMax
        return currentMaxMistakes
    }
    
    private fun initializeMaxMistakes() {
        val defaultMax = getInitialMaxMistakes()
        currentMaxMistakes = questPuzzleId?.let { id ->
            val max = attemptStore.getMaxMistakes(id, defaultMax)
            if (max <= 0) {
                attemptStore.setMaxMistakes(id, defaultMax)
                defaultMax
            } else {
                max
            }
        } ?: defaultMax
        // Ensure it's at least the default
        if (currentMaxMistakes <= 0) {
            currentMaxMistakes = defaultMax
        }
    }
    
    private fun incrementMaxMistakes() {
        val defaultMax = getInitialMaxMistakes()
        currentMaxMistakes = questPuzzleId?.let { id ->
            attemptStore.incrementMaxMistakes(id, defaultMax)
        } ?: run {
            currentMaxMistakes + 1
        }
    }
    
    private fun updateMistakesHud(count: Int) {
        val text = findViewById<TextView>(R.id.mistakesText)
        // Always get fresh max from store (don't use cached value)
        val defaultMax = getInitialMaxMistakes()
        val maxMistakes = questPuzzleId?.let { id ->
            val storedMax = attemptStore.getMaxMistakes(id, defaultMax)
            currentMaxMistakes = if (storedMax > 0) storedMax else defaultMax
            currentMaxMistakes
        } ?: currentMaxMistakes.let { if (it > 0) it else defaultMax }
        // Update cached value
        currentMaxMistakes = maxMistakes
        val displayText = "$count / $maxMistakes"
        Log.d("MainActivity", "updateMistakesHud: count=$count, max=$maxMistakes, display='$displayText'")
        text.text = displayText
        when {
            count >= maxMistakes -> {
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
        text.contentDescription = "Mistakes: $count of $maxMistakes"
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
            // Reset attempt state and clear user entries (keep same puzzle)
            val defaultMax = getInitialMaxMistakes()
            questPuzzleId?.let { id -> 
                attemptStore.clear(id)
                attemptStore.resetMaxMistakes(id, defaultMax)
            }
            currentMaxMistakes = defaultMax
            totalMistakes = 0
            updateMistakesHud(0)
            // Reset to initial state (clears user entries, keeps puzzle clues - same game)
            sudokuBoard.resetToInitialState()
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
    
    private fun showMistakesExhaustedDialog() {
        stopTimer()
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_mistakes_exhausted, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<Button>(R.id.btnRestart).setOnClickListener {
            dialog.dismiss()
            SoundManager.getInstance(this).playClick()
            // Reset attempt state and clear user entries (keep same puzzle)
            val defaultMax = getInitialMaxMistakes()
            questPuzzleId?.let { id -> 
                attemptStore.clear(id)
                attemptStore.resetMaxMistakes(id, defaultMax)
            }
            currentMaxMistakes = defaultMax
            totalMistakes = 0
            updateMistakesHud(0)
            // Reset to initial state (clears user entries, keeps puzzle clues - same game)
            // For quest puzzles, this keeps the same puzzle. For Play Now, it also keeps the same puzzle.
            sudokuBoard.resetToInitialState()
            sudokuBoard.clearNumberHighlight()
            secondsElapsed = 0
            totalMistakes = 0
            gameResultSaved = false
            updateTimerText()
            startTimer()
            updateProgress()
        }

        view.findViewById<Button>(R.id.btnWatchAd).setOnClickListener {
            dialog.dismiss()
            SoundManager.getInstance(this).playClick()
            // Show rewarded ad to continue playing
            if (adManager.isRewardedAdLoaded()) {
                adManager.showRewardedAd(this, onAdClosed = {
                    // After ad is watched, increment max mistakes only (keep current mistakes)
                    // Example: 3/3 → 3/4, then if user makes mistake: 4/4
                    val defaultMax = getInitialMaxMistakes()
                    var newMax = defaultMax
                    
                    // Increment max mistakes only (don't increment mistakes)
                    questPuzzleId?.let { id ->
                        newMax = attemptStore.incrementMaxMistakes(id, defaultMax)
                        currentMaxMistakes = newMax
                    } ?: run {
                        // For non-quest games, increment local variable
                        currentMaxMistakes++
                        newMax = currentMaxMistakes
                    }
                    Log.d("MainActivity", "Max mistakes incremented to: $newMax")
                    
                    // Keep current mistakes (don't increment them)
                    questPuzzleId?.let { id ->
                        totalMistakes = attemptStore.getMistakes(id)
                    }
                    // For non-quest games, totalMistakes is already correct
                    Log.d("MainActivity", "After ad: mistakes=$totalMistakes, max=$newMax")
                    
                    // Update UI with both values directly
                    val text = findViewById<TextView>(R.id.mistakesText)
                    if (text != null) {
                        text.text = "$totalMistakes / $newMax"
                        Log.d("MainActivity", "Updated TextView to: ${text.text}")
                    }
                    updateMistakesHud(totalMistakes)
                    // Restart timer to continue playing
                    startTimer()
                    // Preload next rewarded ad
                    adManager.loadRewardedAd()
                }, onUserEarnedReward = {
                    // Reward earned - mistakes incremented in onAdClosed
                })
            } else {
                // Ad not loaded - show loading message and load with callback
                val handler = Handler(Looper.getMainLooper())
                var adShown = false
                
                val onAdLoadedCallback: () -> Unit = {
                    if (!adShown && adManager.isRewardedAdLoaded()) {
                        adShown = true
                        adManager.showRewardedAd(this, onAdClosed = {
                            // After ad is watched, increment max mistakes only (keep current mistakes)
                            // Example: 3/3 → 3/4, then if user makes mistake: 4/4
                            val defaultMax = getInitialMaxMistakes()
                            var newMax = defaultMax
                            
                            // Increment max mistakes only (don't increment mistakes)
                            questPuzzleId?.let { id ->
                                newMax = attemptStore.incrementMaxMistakes(id, defaultMax)
                                currentMaxMistakes = newMax
                            } ?: run {
                                // For non-quest games, increment local variable
                                currentMaxMistakes++
                                newMax = currentMaxMistakes
                            }
                            Log.d("MainActivity", "Max mistakes incremented to: $newMax")
                            
                            // Keep current mistakes (don't increment them)
                            questPuzzleId?.let { id ->
                                totalMistakes = attemptStore.getMistakes(id)
                            }
                            // For non-quest games, totalMistakes is already correct
                            Log.d("MainActivity", "After ad: mistakes=$totalMistakes, max=$newMax")
                            
                            // Update UI with both values directly
                            val text = findViewById<TextView>(R.id.mistakesText)
                            if (text != null) {
                                text.text = "$totalMistakes / $newMax"
                                Log.d("MainActivity", "Updated TextView to: ${text.text}")
                            }
                            updateMistakesHud(totalMistakes)
                            // Restart timer to continue playing
                            startTimer()
                            adManager.loadRewardedAd()
                        }, onUserEarnedReward = {
                            // Reward earned
                        })
                    }
                }
                
                adManager.loadRewardedAd(onAdLoadedCallback)
                
                // Timeout fallback - if ad doesn't load, restart game
                handler.postDelayed({
                    if (!adShown) {
                        if (adManager.isRewardedAdLoaded()) {
                            adShown = true
                            onAdLoadedCallback()
                        } else {
                            // Ad failed to load - restart game instead
                            questPuzzleId?.let { id -> attemptStore.clear(id) }
                            totalMistakes = 0
                            updateMistakesHud(0)
                            sudokuBoard.resetPuzzle(currentDifficulty)
                            sudokuBoard.clearNumberHighlight()
                            secondsElapsed = 0
                            totalMistakes = 0
                            gameResultSaved = false
                            updateTimerText()
                            startTimer()
                            updateProgress()
                        }
                    }
                }, 10000) // 10 second timeout
            }
        }

        dialog.show()
    }

    private fun stopTimer() {
        running = false
        handler.removeCallbacks(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
        persistQuestPuzzleState()
        persistPlayNowState()
        
        // Pause banner ad
        bannerAdView?.pause()
    }

    override fun onResume() {
        super.onResume()
        
        // Re-enable fullscreen mode when resuming
        enableFullscreen()
        
        if (gameStarted) {
            startTimer() // Resume timer if game was started
        }
        
        // Update hint count if settings changed while in settings
        sudokuBoard.updateHintsFromSettings()
        updateHintBadge()
        
        // Resume banner ad
        bannerAdView?.resume()
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableFullscreen()
        }
    }
    
    /**
     * Enable fullscreen/immersive mode to hide status bar and navigation bar
     */
    private fun enableFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30+) - Use WindowInsetsController
            window.setDecorFitsSystemWindows(false)
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // Older Android versions
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
        
        // Ensure window background matches game background
        window.setBackgroundDrawableResource(R.drawable.parchment_background)
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
        persistQuestPuzzleState()
        persistPlayNowState()
        
        // Destroy banner ad
        bannerAdView?.destroy()
    }

    // Check for victory
    private fun checkVictory() {
        if (sudokuBoard.isBoardComplete() && !gameResultSaved) {
            stopTimer()
            gameResultSaved = true
            
            // Save game result
            val timeText = timerText.text.toString()
            val performanceStars = statsManager.calculatePerformanceStars(secondsElapsed, totalMistakes, boardSize, currentDifficulty)
            val gameResult = GameResult(
                boardSize = boardSize,
                difficulty = currentDifficulty,
                timeInSeconds = secondsElapsed,
                mistakes = totalMistakes,
                completed = true,
                performanceRating = performanceStars
            )
            statsManager.saveGameResult(gameResult)
            
            // Clear attempt state so failed flag/mistakes do not persist after success
            questPuzzleId?.let { id -> attemptStore.clear(id) }
            
            // Record quest progress if this is a quest puzzle
            questPuzzleId?.let { puzzleId ->
                val codex = questCodex ?: QuestCodex(this).also { questCodex = it }
                
                // Clear saved board state when puzzle is completed
                codex.clearPuzzleBoardState(puzzleId)
                
                codex.recordPuzzleCompletion(realmId ?: "", puzzleId, secondsElapsed.toLong(), totalMistakes)
                
                // Show animated quest victory dialog
                showQuestVictoryDialog(timeText, totalMistakes)
            } ?: run {
                playNowStateManager?.clearState()
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
        val performanceStars = statsManager.calculatePerformanceStars(secondsElapsed, mistakes, boardSize, currentDifficulty)
        val starsText = dialogView.findViewById<TextView>(R.id.performanceStars)
        val starDisplay = "⭐".repeat(performanceStars) + "☆".repeat(5 - performanceStars)
        starsText?.text = starDisplay
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // Make background transparent around card for nicer presentation
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        var shouldShowAdOnDismiss = true
        
        // Determine if we should show rewarded ad (for 9x9 Hard/Expert in Quick Play)
        val shouldShowRewardedAd = isQuickPlay && 
                                   boardSize == 9 && 
                                   (currentDifficulty == SudokuGenerator.Difficulty.HARD || 
                                    currentDifficulty == SudokuGenerator.Difficulty.EXPERT)
        
        // Helper function to show appropriate ad
        fun showAppropriateAd(onAdClosed: (() -> Unit)? = null) {
            if (shouldShowRewardedAd) {
                android.util.Log.d("MainActivity", "Showing rewarded ad for 9x9 ${currentDifficulty.name} Quick Play")
                // Track if ad was shown and if onAdClosed was called to prevent race conditions
                var adShown = false
                var onAdClosedCalled = false
                val handler = Handler(Looper.getMainLooper())
                
                // Wrapper to ensure onAdClosed is only called once
                val safeOnAdClosed: () -> Unit = {
                    if (!onAdClosedCalled) {
                        android.util.Log.d("MainActivity", "Calling onAdClosed callback")
                        onAdClosedCalled = true
                        onAdClosed?.invoke()
                    } else {
                        android.util.Log.w("MainActivity", "onAdClosed already called, skipping")
                    }
                }
                
                // Check if rewarded ad is loaded
                if (adManager.isRewardedAdLoaded()) {
                    android.util.Log.d("MainActivity", "Rewarded ad is loaded, showing it immediately")
                    adShown = true
                    adManager.showRewardedAd(this, onAdClosed = safeOnAdClosed)
                    // Preload next rewarded ad
                    adManager.loadRewardedAd()
                } else {
                    android.util.Log.w("MainActivity", "Rewarded ad is NOT loaded! Loading it now and waiting...")
                    // Try to load it with callback
                    adManager.loadRewardedAd {
                        // Ad loaded callback
                        android.util.Log.d("MainActivity", "Rewarded ad load callback fired - adShown: $adShown, onAdClosedCalled: $onAdClosedCalled")
                        if (!adShown && !onAdClosedCalled) {
                            if (adManager.isRewardedAdLoaded()) {
                                android.util.Log.d("MainActivity", "Rewarded ad loaded via callback, showing it now")
                                adShown = true
                                adManager.showRewardedAd(this, onAdClosed = safeOnAdClosed)
                                // Preload next rewarded ad
                                adManager.loadRewardedAd()
                            } else {
                                android.util.Log.w("MainActivity", "Rewarded ad callback fired but ad still not available, proceeding without ad")
                                safeOnAdClosed()
                            }
                        } else {
                            android.util.Log.w("MainActivity", "Rewarded ad callback fired but ad already handled (adShown=$adShown, onAdClosedCalled=$onAdClosedCalled)")
                        }
                    }
                    
                    // Set up a timeout fallback (max 10 seconds)
                    handler.postDelayed({
                        android.util.Log.d("MainActivity", "Timeout handler fired - adShown: $adShown, onAdClosedCalled: $onAdClosedCalled, adLoaded: ${adManager.isRewardedAdLoaded()}")
                        if (!adShown && !onAdClosedCalled && !adManager.isRewardedAdLoaded()) {
                            android.util.Log.w("MainActivity", "Rewarded ad failed to load after 5s timeout. Waiting 5 more seconds...")
                            // Try one more time with longer timeout (5 more seconds)
                            handler.postDelayed({
                                android.util.Log.d("MainActivity", "Extended timeout handler fired - adShown: $adShown, onAdClosedCalled: $onAdClosedCalled, adLoaded: ${adManager.isRewardedAdLoaded()}")
                                if (!adShown && !onAdClosedCalled) {
                                    if (adManager.isRewardedAdLoaded()) {
                                        android.util.Log.d("MainActivity", "Rewarded ad loaded after extended wait (10s total), showing it")
                                        adShown = true
                                        adManager.showRewardedAd(this, onAdClosed = safeOnAdClosed)
                                        adManager.loadRewardedAd()
                                    } else {
                                        android.util.Log.w("MainActivity", "Rewarded ad still not available after 10s wait, proceeding without ad")
                                        safeOnAdClosed()
                                    }
                                } else {
                                    android.util.Log.d("MainActivity", "Extended timeout: Ad already handled, skipping")
                                }
                            }, 5000) // Additional 5 second wait
                        } else {
                            android.util.Log.d("MainActivity", "Timeout handler: Ad already handled or loaded, skipping")
                        }
                    }, 5000) // 5 second timeout
                }
            } else {
                android.util.Log.d("MainActivity", "Showing interstitial ad for Quick Play")
                // Show interstitial ad
                if (onAdClosed != null) {
                    adManager.showInterstitialAd(this, onAdClosed = onAdClosed)
                } else {
                    if (adManager.isInterstitialAdLoaded()) {
                        adManager.showInterstitialAd(this, null)
                    }
                }
                // Preload next interstitial ad
                adManager.loadInterstitialAd()
            }
        }
        
        // Setup button click listeners
        dialogView.findViewById<Button>(R.id.btnNewGame)?.setOnClickListener {
            shouldShowAdOnDismiss = true
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
            shouldShowAdOnDismiss = false
            dialog.dismiss()
            // Show appropriate ad before going to menu
            showAppropriateAd {
                finish()
            }
        }
        
        dialogView.findViewById<Button>(R.id.btnShare)?.setOnClickListener {
            shouldShowAdOnDismiss = true
            dialog.dismiss()
            // Share functionality can be added here if needed
            Toast.makeText(this, "Share feature coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        dialogView.findViewById<Button>(R.id.btnRate)?.setOnClickListener {
            openPlayStoreRating()
        }
        
        // Show appropriate ad when dialog is dismissed (for New Game and Share buttons)
        dialog.setOnDismissListener {
            if (shouldShowAdOnDismiss) {
                // Show appropriate ad after dialog closes
                showAppropriateAd()
            } else {
                // Preload next ad even if not showing
                if (shouldShowRewardedAd) {
                    adManager.loadRewardedAd()
                } else {
                    adManager.loadInterstitialAd()
                }
            }
        }
        
        dialog.show()
    }

    /**
     * Show ad based on tier and puzzle number
     * Tier 1 (echoes): After puzzles 2, 4, 6, 8 show Interstitial, after puzzle 10 show Rewarded
     * Tier 2 (trials): Same as Tier 1
     * Tier 3 (flame): After every puzzle show Rewarded
     * Tier 4 (shadows): After every puzzle show Rewarded
     */
    private fun showQuestAd(realmId: String?, puzzleNumber: Int, onAdClosed: () -> Unit) {
        android.util.Log.d("MainActivity", "showQuestAd called - realmId: $realmId, puzzleNumber: $puzzleNumber")
        
        if (realmId == null || puzzleNumber == 0) {
            android.util.Log.d("MainActivity", "showQuestAd: Skipping ad - realmId is null or puzzleNumber is 0")
            onAdClosed()
            return
        }
        
        // Determine tier based on realm ID
        val tier = when (realmId) {
            "echoes" -> 1
            "trials" -> 2
            "flame" -> 3
            "shadows" -> 4
            else -> {
                android.util.Log.d("MainActivity", "showQuestAd: Unknown realmId: $realmId")
                onAdClosed()
                return
            }
        }
        
        android.util.Log.d("MainActivity", "showQuestAd: Tier determined as $tier for realmId: $realmId")
        
        // Determine which ad to show based on tier and puzzle number
        val shouldShowInterstitial = when (tier) {
            1, 2 -> {
                // Tier 1 & 2: Show interstitial after puzzles 2, 4, 6, 8
                puzzleNumber in listOf(2, 4, 6, 8)
            }
            3 -> {
                // Tier 3: Never show interstitial (always rewarded)
                false
            }
            4 -> {
                // Tier 4: Never show interstitial (always rewarded)
                false
            }
            else -> false
        }
        
        val shouldShowRewarded = when (tier) {
            1, 2 -> {
                // Tier 1 & 2: Show rewarded after puzzle 10
                puzzleNumber == 10
            }
            3 -> {
                // Tier 3: Show rewarded after every puzzle
                true
            }
            4 -> {
                // Tier 4: Show rewarded after every puzzle
                true
            }
            else -> false
        }
        
        android.util.Log.d("MainActivity", "showQuestAd: shouldShowInterstitial=$shouldShowInterstitial, shouldShowRewarded=$shouldShowRewarded")
        
        // Show the appropriate ad
        when {
            shouldShowRewarded -> {
                android.util.Log.d("MainActivity", "=== showQuestAd: Showing rewarded ad for Tier $tier, Puzzle $puzzleNumber ===")
                android.util.Log.d("MainActivity", "Rewarded ad loaded status: ${adManager.isRewardedAdLoaded()}")
                
                // Track if ad was shown and if onAdClosed was called to prevent race conditions
                var adShown = false
                var onAdClosedCalled = false
                val handler = Handler(Looper.getMainLooper())
                val isPuzzle10ForTier1Or2 = (tier == 1 || tier == 2) && puzzleNumber == 10
                val isTier3Or4 = tier == 3 || tier == 4
                
                android.util.Log.d("MainActivity", "isPuzzle10ForTier1Or2: $isPuzzle10ForTier1Or2, isTier3Or4: $isTier3Or4")
                
                // Wrapper to ensure onAdClosed is only called once
                val safeOnAdClosed: () -> Unit = {
                    if (!onAdClosedCalled) {
                        android.util.Log.d("MainActivity", "Calling onAdClosed callback")
                        onAdClosedCalled = true
                        onAdClosed()
                    } else {
                        android.util.Log.w("MainActivity", "onAdClosed already called, skipping")
                    }
                }
                
                // Check if rewarded ad is loaded
                if (adManager.isRewardedAdLoaded()) {
                    android.util.Log.d("MainActivity", "Rewarded ad is loaded, showing it immediately")
                    adShown = true
                    adManager.showRewardedAd(this, onAdClosed = safeOnAdClosed)
                    // Preload next rewarded ad
                    adManager.loadRewardedAd()
                } else {
                    android.util.Log.w("MainActivity", "Rewarded ad is NOT loaded! Loading it now and waiting...")
                    // Try to load it with callback
                    adManager.loadRewardedAd {
                        // Ad loaded callback
                        android.util.Log.d("MainActivity", "Rewarded ad load callback fired - adShown: $adShown, onAdClosedCalled: $onAdClosedCalled")
                        if (!adShown && !onAdClosedCalled) {
                            if (adManager.isRewardedAdLoaded()) {
                                android.util.Log.d("MainActivity", "Rewarded ad loaded via callback, showing it now")
                                adShown = true
                                adManager.showRewardedAd(this, onAdClosed = safeOnAdClosed)
                                // Preload next rewarded ad
                                adManager.loadRewardedAd()
                            } else {
                                android.util.Log.w("MainActivity", "Rewarded ad callback fired but ad still not available, proceeding without ad")
                                safeOnAdClosed()
                            }
                        } else {
                            android.util.Log.w("MainActivity", "Rewarded ad callback fired but ad already handled (adShown=$adShown, onAdClosedCalled=$onAdClosedCalled)")
                        }
                    }
                    
                    // Also set up a timeout fallback (max 5 seconds)
                    // For puzzle 10 (Tier 1 & 2) and Tier 3 & 4, we must show rewarded ad, not interstitial
                    handler.postDelayed({
                        android.util.Log.d("MainActivity", "Timeout handler fired - adShown: $adShown, onAdClosedCalled: $onAdClosedCalled, adLoaded: ${adManager.isRewardedAdLoaded()}")
                        if (!adShown && !onAdClosedCalled && !adManager.isRewardedAdLoaded()) {
                            if (isPuzzle10ForTier1Or2 || isTier3Or4) {
                                // For puzzle 10 (Tier 1 & 2) and Tier 3 & 4, we must show rewarded ad - wait longer or proceed without ad
                                android.util.Log.w("MainActivity", "Rewarded ad failed to load after 5s timeout. Waiting 5 more seconds...")
                                // Try one more time with longer timeout (5 more seconds)
                                handler.postDelayed({
                                    android.util.Log.d("MainActivity", "Extended timeout handler fired - adShown: $adShown, onAdClosedCalled: $onAdClosedCalled, adLoaded: ${adManager.isRewardedAdLoaded()}")
                                    if (!adShown && !onAdClosedCalled) {
                                        if (adManager.isRewardedAdLoaded()) {
                                            android.util.Log.d("MainActivity", "Rewarded ad loaded after extended wait (10s total), showing it")
                                            adShown = true
                                            adManager.showRewardedAd(this, onAdClosed = safeOnAdClosed)
                                            adManager.loadRewardedAd()
                                        } else {
                                            android.util.Log.w("MainActivity", "Rewarded ad still not available after 10s wait, proceeding without ad")
                                            safeOnAdClosed()
                                        }
                                    } else {
                                        android.util.Log.d("MainActivity", "Extended timeout: Ad already handled, skipping")
                                    }
                                }, 5000) // Additional 5 second wait
                            } else {
                                // For other cases, fallback to interstitial is acceptable
                                android.util.Log.w("MainActivity", "Rewarded ad failed to load after timeout, showing interstitial as fallback")
                                if (adManager.isInterstitialAdLoaded()) {
                                    android.util.Log.d("MainActivity", "Showing interstitial ad as fallback")
                                    adShown = true
                                    adManager.showInterstitialAd(this, onAdClosed = safeOnAdClosed)
                                    adManager.loadInterstitialAd()
                                } else {
                                    android.util.Log.w("MainActivity", "No ads available, proceeding without ad")
                                    safeOnAdClosed()
                                }
                            }
                        } else {
                            android.util.Log.d("MainActivity", "Timeout handler: Ad already handled or loaded, skipping")
                        }
                    }, 5000) // Increased to 5 second timeout
                }
            }
            shouldShowInterstitial -> {
                android.util.Log.d("MainActivity", "Showing interstitial ad for Tier $tier, Puzzle $puzzleNumber")
                adManager.showInterstitialAd(this, onAdClosed = onAdClosed)
                // Preload next interstitial ad
                adManager.loadInterstitialAd()
            }
            else -> {
                android.util.Log.d("MainActivity", "No ad for Tier $tier, Puzzle $puzzleNumber")
                onAdClosed()
            }
        }
    }
    
    private fun showQuestVictoryDialog(time: String, mistakes: Int) {
        // Record quest completion
        val questCodex = QuestCodex(this)
        val timeSeconds = secondsElapsed.toLong()
        
        questPuzzleId?.let { puzzleId ->
            realmId?.let { realmId ->
                // Get puzzle chain
                val puzzleChain = questCodex.getPuzzleChain(realmId) ?: return@let
                val realm = questCodex.getRealmById(realmId) ?: return@let
                
                // Check if this puzzle was already completed before
                val savedPuzzleBefore = questCodex.getSavedPuzzle(puzzleId)
                val wasPuzzleAlreadyCompleted = savedPuzzleBefore?.isCompleted == true
                
                // Count completed puzzles BEFORE recording this one (excluding current puzzle)
                val completedBefore = puzzleChain.puzzles.count { puzzle ->
                    if (puzzle.id == puzzleId) {
                        false // Don't count current puzzle
                    } else {
                        val saved = questCodex.getSavedPuzzle(puzzle.id)
                        saved?.isCompleted == true
                    }
                }
                
                android.util.Log.d("MainActivity", "BEFORE: completedBefore=$completedBefore, wasPuzzleAlreadyCompleted=$wasPuzzleAlreadyCompleted, puzzleId=$puzzleId")
                
                // Record the puzzle completion (this will mark it as completed)
                questCodex.recordPuzzleCompletion(realmId, puzzleId, timeSeconds, totalMistakes)
                
                // Count completed puzzles AFTER recording
                val completedAfter = puzzleChain.puzzles.count { puzzle ->
                    val saved = questCodex.getSavedPuzzle(puzzle.id)
                    saved?.isCompleted == true
                }
                
                val totalPuzzles = realm.totalPuzzles
                
                android.util.Log.d("MainActivity", "AFTER: completedAfter=$completedAfter, totalPuzzles=$totalPuzzles")
                
                // Check if this is the 10th puzzle being completed
                // Show special dialog when: completedBefore was 9 and completedAfter is now 10
                // This means the user just completed the final puzzle to finish the level
                val isCompletingFinalPuzzle = completedBefore == (totalPuzzles - 1) && completedAfter >= totalPuzzles
                
                android.util.Log.d("MainActivity", "Realm completion check:")
                android.util.Log.d("MainActivity", "  - wasPuzzleAlreadyCompleted: $wasPuzzleAlreadyCompleted")
                android.util.Log.d("MainActivity", "  - completedBefore: $completedBefore")
                android.util.Log.d("MainActivity", "  - completedAfter: $completedAfter")
                android.util.Log.d("MainActivity", "  - totalPuzzles: $totalPuzzles")
                android.util.Log.d("MainActivity", "  - isCompletingFinalPuzzle: $isCompletingFinalPuzzle")
                
                // Clear saved board state when puzzle is completed
                questCodex.clearPuzzleBoardState(puzzleId)
                
                // Get puzzle number for ad logic
                val currentPuzzle = puzzleChain.puzzles.find { it.id == puzzleId }
                val puzzleNumber = currentPuzzle?.puzzleNumber ?: 0
                
                android.util.Log.d("MainActivity", "Puzzle completion - puzzleId: $puzzleId, puzzleNumber: $puzzleNumber, isCompletingFinalPuzzle: $isCompletingFinalPuzzle")
                
                // Preload rewarded ad if next puzzle will need it (puzzle 9 for Tier 1 & 2, or any puzzle for Tier 3 & 4)
                val tier = when (realmId) {
                    "echoes" -> 1
                    "trials" -> 2
                    "flame" -> 3
                    "shadows" -> 4
                    else -> 0
                }
                if ((tier == 1 || tier == 2) && puzzleNumber == 9) {
                    // Preload rewarded ad for puzzle 10
                    android.util.Log.d("MainActivity", "Preloading rewarded ad for puzzle 10 (completing puzzle 9)")
                    adManager.loadRewardedAd()
                } else if ((tier == 1 || tier == 2) && puzzleNumber == 10) {
                    // Also ensure rewarded ad is loaded when puzzle 10 is completed (backup preload)
                    android.util.Log.d("MainActivity", "Puzzle 10 completed - ensuring rewarded ad is loaded")
                    if (!adManager.isRewardedAdLoaded()) {
                        android.util.Log.d("MainActivity", "Rewarded ad not loaded, loading it now")
                        adManager.loadRewardedAd()
                    } else {
                        android.util.Log.d("MainActivity", "Rewarded ad already loaded, ready to show")
                    }
                } else if (tier == 3 || tier == 4) {
                    // Always preload rewarded ad for Tier 3 & 4
                    android.util.Log.d("MainActivity", "Preloading rewarded ad for Tier $tier")
                    adManager.loadRewardedAd()
                }
                
                // If this is the final puzzle completing the level, show special level completion dialog
                if (isCompletingFinalPuzzle) {
                    android.util.Log.d("MainActivity", "*** SHOWING LEVEL COMPLETION DIALOG *** for realm: $realmId, puzzleNumber: $puzzleNumber")
                    showLevelCompletionDialog(realmId, realm.name, puzzleNumber)
                } else {
                    android.util.Log.d("MainActivity", "NOT showing level completion dialog - showing regular dialog instead")
                    // Show regular dialog
                    showRegularQuestVictoryDialog(time, mistakes, realmId, puzzleNumber)
                }
                return  // Exit function early
            }
        }
        
        // If we reach here and realmId is null, this shouldn't happen for quest puzzles
        // But if it does, show the regular dialog anyway
        val currentRealmId = realmId
        if (currentRealmId != null) {
            // Get puzzle number if available
            val puzzleNumber = questPuzzleId?.let { puzzleId ->
                val codex = QuestCodex(this)
                val chain = codex.getPuzzleChain(currentRealmId)
                chain?.puzzles?.find { it.id == puzzleId }?.puzzleNumber ?: 0
            } ?: 0
            showRegularQuestVictoryDialog(time, mistakes, currentRealmId, puzzleNumber)
        }
    }
    
    private fun showRegularQuestVictoryDialog(time: String, mistakes: Int, realmId: String?, puzzleNumber: Int) {
        android.util.Log.d("MainActivity", "Showing regular quest victory dialog")
        
        val questCodex = this.questCodex ?: QuestCodex(this)
        val timeSeconds = secondsElapsed.toLong()
        
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
        
        // Setup button click listener - show ad when button is clicked
        dialogView.findViewById<Button>(R.id.btnRealmMap)?.setOnClickListener {
            dialog.dismiss()
            // Show ad based on tier and puzzle number when button is clicked
            showQuestAd(realmId, puzzleNumber) {
                // Return to realm quest after ad
                if (realmId != null) {
                    val intent = Intent(this, RealmQuestActivity::class.java).apply {
                        putExtra("realm_id", realmId)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
        
        dialogView.findViewById<Button>(R.id.btnRate)?.setOnClickListener {
            openPlayStoreRating()
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
        val rateButton = dialogView.findViewById<Button>(R.id.btnRate)
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
        
        rateButton?.animate()
            ?.alpha(1f)
            ?.setStartDelay(900)
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

    private fun showLevelCompletionDialog(realmId: String, realmName: String, puzzleNumber: Int) {
        val questCodex = QuestCodex(this)
        val realm = questCodex.getRealmById(realmId)
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_level_completion, null)
        
        // Set realm name
        dialogView.findViewById<TextView>(R.id.levelName)?.text = realmName
        dialogView.findViewById<TextView>(R.id.levelNameTitle)?.text = "Level Complete!"
        
        // Get total stars for this realm
        val puzzleChain = questCodex.getPuzzleChain(realmId)
        val totalStars = puzzleChain?.puzzles?.sumOf { puzzle ->
            val saved = questCodex.getSavedPuzzle(puzzle.id)
            saved?.stars ?: 0
        } ?: 0
        
        dialogView.findViewById<TextView>(R.id.totalStarsText)?.text = "Total Stars: $totalStars"
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // Make background transparent
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        // Setup button click listener - show ad when button is clicked
        dialogView.findViewById<Button>(R.id.btnContinue)?.setOnClickListener {
            fallingStarsActive = false
            dialog.dismiss()
            // Show ad based on tier and puzzle number when button is clicked
            android.util.Log.d("MainActivity", "Level completion dialog button clicked - realmId: $realmId, puzzleNumber: $puzzleNumber")
            showQuestAd(realmId, puzzleNumber) {
                // Return to realm quest or main menu after ad
                if (realmId != null) {
                    val intent = Intent(this, RealmQuestActivity::class.java).apply {
                        putExtra("realm_id", realmId)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
        
        // Reset flag when dialog is dismissed
        dialog.setOnDismissListener {
            fallingStarsActive = false
        }
        
        // Animate dialog entrance and effects
        dialog.setOnShowListener {
            animateLevelCompletionDialog(dialogView)
        }
        
        dialog.show()
    }
    
    private fun animateLevelCompletionDialog(dialogView: android.view.View) {
        val overlay = dialogView.findViewById<android.view.View>(R.id.completionOverlay)
        val card = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.completionCard)
        val starsContainer = dialogView.findViewById<FrameLayout>(R.id.starsConnectionContainer)
        val title = dialogView.findViewById<TextView>(R.id.levelNameTitle)
        val levelName = dialogView.findViewById<TextView>(R.id.levelName)
        val message = dialogView.findViewById<TextView>(R.id.completionMessage)
        val starsText = dialogView.findViewById<TextView>(R.id.totalStarsText)
        val button = dialogView.findViewById<Button>(R.id.btnContinue)
        
        // Animate overlay fade in
        overlay?.animate()?.alpha(1f)?.setDuration(400)?.start()
        
        // Animate card entrance (scale + fade)
        card?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(500)
            ?.setInterpolator(android.view.animation.OvershootInterpolator())
            ?.start()
        
        // Animate title
        title?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(200)
            ?.setDuration(400)
            ?.setInterpolator(android.view.animation.OvershootInterpolator())
            ?.start()
        
        // Animate level name
        levelName?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(300)
            ?.setDuration(400)
            ?.start()
        
        // Animate connecting stars
        animateConnectingStars(starsContainer)
        
        // Start falling stars/balloons animation
        startFallingStarsAnimation(dialogView)
        
        // Animate message and stats
        message?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(600)
            ?.setDuration(400)
            ?.start()
        
        starsText?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(700)
            ?.setDuration(400)
            ?.start()
        
        // Animate button
        button?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setStartDelay(800)
            ?.setDuration(400)
            ?.setInterpolator(android.view.animation.OvershootInterpolator())
            ?.start()
    }
    
    private fun animateConnectingStars(container: FrameLayout?) {
        container ?: return
        
        // Use post to get actual dimensions
        container.post {
            val width = container.width
            val height = container.height
            if (width == 0 || height == 0) return@post
            
            // Draw connecting lines between stars (using a custom view or canvas)
            val lineView = object : android.view.View(this) {
                override fun onDraw(canvas: android.graphics.Canvas) {
                    super.onDraw(canvas)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#FFD700")
                        strokeWidth = 4f
                        alpha = 150
                    }
                    
                    val radius = minOf(width, height) * 0.3f
                    val centerX = width / 2f
                    val centerY = height / 2f
                    
                    // Draw lines connecting adjacent stars
                    for (i in 0 until 10) {
                        val angle1 = (i * 36.0) * kotlin.math.PI / 180.0
                        val angle2 = ((i + 1) % 10 * 36.0) * kotlin.math.PI / 180.0
                        
                        val x1 = (centerX + radius * kotlin.math.cos(angle1)).toFloat()
                        val y1 = (centerY + radius * kotlin.math.sin(angle1)).toFloat()
                        val x2 = (centerX + radius * kotlin.math.cos(angle2)).toFloat()
                        val y2 = (centerY + radius * kotlin.math.sin(angle2)).toFloat()
                        
                        canvas.drawLine(x1, y1, x2, y2, paint)
                    }
                }
            }.apply {
                layoutParams = FrameLayout.LayoutParams(width, height)
                alpha = 0f
            }
            
            container.addView(lineView, 0) // Add behind stars
            
            // Create 10 star views representing the 10 completed games
            for (i in 0 until 10) {
                val angle = (i * 36.0) * kotlin.math.PI / 180.0 // 360/10 = 36 degrees per star
                val radius = minOf(width, height) * 0.3f
                val centerX = width / 2f
                val centerY = height / 2f
                
                val star = TextView(this).apply {
                    text = "⭐"
                    textSize = 32f
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val x = (centerX + radius * kotlin.math.cos(angle) - 32).toInt()
                        val y = (centerY + radius * kotlin.math.sin(angle) - 32).toInt()
                        
                        leftMargin = x
                        topMargin = y
                    }
                    alpha = 0f
                    scaleX = 0f
                    scaleY = 0f
                }
                
                container.addView(star)
                
                // Animate each star appearing and connecting
                val delay = i * 100L
                star.animate()
                    ?.alpha(1f)
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setStartDelay(delay)
                    ?.setDuration(400)
                    ?.setInterpolator(android.view.animation.OvershootInterpolator())
                    ?.withEndAction {
                        // Add pulsing effect
                        star.animate()
                            ?.scaleX(1.2f)
                            ?.scaleY(1.2f)
                            ?.setDuration(300)
                            ?.withEndAction {
                                star.animate()
                                    ?.scaleX(1f)
                                    ?.scaleY(1f)
                                    ?.setDuration(300)
                                    ?.start()
                            }
                            ?.start()
                    }
                    ?.start()
            }
            
            // Animate line appearance
            lineView.animate()
                ?.alpha(1f)
                ?.setStartDelay(500)
                ?.setDuration(800)
                ?.start()
        }
    }
    
    private var fallingStarsActive = false
    
    private fun startFallingStarsAnimation(parentView: android.view.View) {
        val parent = parentView.findViewById<FrameLayout>(R.id.fallingStarsContainer) ?: return
        
        if (fallingStarsActive) return
        fallingStarsActive = true
        
        // Use post to get actual dimensions
        parent.post {
            val width = parent.width
            val height = parent.height
            if (width == 0 || height == 0) {
                fallingStarsActive = false
                return@post
            }
            
            // Create multiple falling stars/balloons
            for (i in 0 until 15) {
                val star = TextView(this).apply {
                    text = if (i % 3 == 0) "🎈" else "⭐" // Mix balloons and stars
                    textSize = 28f + (kotlin.random.Random.nextFloat() * 20)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        leftMargin = (kotlin.random.Random.nextFloat() * width).toInt()
                        topMargin = -100 // Start above screen
                    }
                    alpha = 0.8f
                }
                
                parent.addView(star)
                
                // Animate falling
                val duration = (2000 + kotlin.random.Random.nextFloat() * 2000).toLong()
                val delay = (i * 200).toLong()
                val endX = (kotlin.random.Random.nextFloat() * width)
                val endY = height + 100f
                
                star.animate()
                    ?.translationX(endX - star.left)
                    ?.translationY(endY)
                    ?.rotation((kotlin.random.Random.nextFloat() * 360))
                    ?.setStartDelay(delay)
                    ?.setDuration(duration)
                    ?.setInterpolator(LinearInterpolator())
                    ?.withEndAction {
                        parent.removeView(star)
                        // Create new falling star to continue animation
                        if (parent.childCount < 5) {
                            createSingleFallingStar(parent)
                        }
                    }
                    ?.start()
            }
        }
    }
    
    private fun createSingleFallingStar(parent: FrameLayout) {
        val width = parent.width
        val height = parent.height
        if (width == 0 || height == 0) return
        
        val star = TextView(this).apply {
            text = if (kotlin.random.Random.nextBoolean()) "🎈" else "⭐"
            textSize = 28f + (kotlin.random.Random.nextFloat() * 20)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (kotlin.random.Random.nextFloat() * width).toInt()
                topMargin = -100
            }
            alpha = 0.8f
        }
        
        parent.addView(star)
        
        val duration = (2000 + kotlin.random.Random.nextFloat() * 2000).toLong()
        val endX = (kotlin.random.Random.nextFloat() * width)
        val endY = height + 100f
        
        star.animate()
            ?.translationX(endX - star.left)
            ?.translationY(endY)
            ?.rotation((kotlin.random.Random.nextFloat() * 360))
            ?.setDuration(duration)
            ?.setInterpolator(LinearInterpolator())
            ?.withEndAction {
                parent.removeView(star)
                // Continue creating stars if dialog is still showing
                if (parent.parent != null) {
                    createSingleFallingStar(parent)
                }
            }
            ?.start()
    }
    
    private fun openPlayStoreRating() {
        val packageName = packageName
        android.util.Log.d("MainActivity", "Opening Play Store rating for package: $packageName")
        
        try {
            // Try to open the Play Store app directly
            val marketUri = Uri.parse("market://details?id=$packageName")
            val intent = Intent(Intent.ACTION_VIEW, marketUri)
            startActivity(intent)
            android.util.Log.d("MainActivity", "Opened Play Store app with URI: $marketUri")
        } catch (e: Exception) {
            // If Play Store app is not available, open in browser
            android.util.Log.d("MainActivity", "Play Store app not available, trying browser: ${e.message}")
            try {
                val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                val intent = Intent(Intent.ACTION_VIEW, webUri)
                startActivity(intent)
                android.util.Log.d("MainActivity", "Opened Play Store in browser with URI: $webUri")
            } catch (e2: Exception) {
                android.util.Log.e("MainActivity", "Failed to open Play Store: ${e2.message}")
                Toast.makeText(this, "Unable to open Play Store", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Disable phone back button - use UI back button instead
    override fun onBackPressed() {
        // Show dialog when back button is pressed
        showBackToMenuDialog()
    }
    
}