package com.artashes.sudoku

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
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
import com.google.android.gms.ads.AdView
import androidx.appcompat.app.AppCompatActivity
import com.artashes.sudoku.NetworkUtils
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.*

class DailyChallengeActivity : AppCompatActivity() {
    
    private lateinit var sudokuBoardView: SudokuBoardView
    private lateinit var dailyChallengeManager: DailyChallengeManager
    private lateinit var dailyChallengeStateManager: DailyChallengeStateManager
    private lateinit var currentPuzzle: DailyChallengeGenerator.DailyPuzzle
    private lateinit var audioManager: AudioManager
    private lateinit var adManager: AdManager
    private lateinit var adRateLimiter: AdRateLimiter
    
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
    private var selectedNumber = 0
    private var gameResultSaved = false // Flag to prevent duplicate saves
    
    // Mistake tracking (like MainActivity)
    private var totalMistakes = 0
    private var currentMaxMistakes = 0 // Current max mistakes (increases after each ad watch)
    private lateinit var attemptStore: AttemptStateStore
    
    // Tooltip management
    private var currentTooltip: PopupWindow? = null
    
    // Banner ad
    private var bannerAdView: AdView? = null
    
    // Share return tracking
    private var waitingForShareReturn = false // Flag to track if we're waiting for user to return from share dialog
    private var lastCompletionRecord: DailyChallengeManager.DailyRecord? = null // Store last completion record to show dialog again
    private var isPracticeMode = false // Flag to track if playing in practice mode (already completed challenge)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set window background to match game background (prevent wrong color in status bar area)
        window.setBackgroundDrawableResource(R.drawable.parchment_background)
        
        // Enable fullscreen/immersive mode
        enableFullscreen()
        
        setContentView(R.layout.activity_daily_challenge)
        
        // Initialize AdMob and load banner ad first (like MainActivity)
        adManager = AdManager(this)
        adRateLimiter = AdRateLimiter(this)
        Log.d("DailyChallenge", "AdManager initialized - Test ads: ${com.artashes.sudoku.BuildConfig.USE_TEST_ADS}")
        bannerAdView = findViewById<AdView>(R.id.bannerAdView)
        if (bannerAdView != null) {
            Log.d("DailyChallenge", "Banner ad view found, loading ad...")
            bannerAdView!!.visibility = View.VISIBLE
            adManager.loadBannerAd(bannerAdView!!)
        } else {
            Log.e("DailyChallenge", "Banner ad view NOT found!")
        }
        
        initializeViews()
        initializeGame()
        setupClickListeners()
        
        // Start game timer if game is active (will resume if restoring saved state)
        if (isGameActive) {
            startGameTimer()
        } else {
            startGame()
        }
    }
    
    private fun initializeViews() {
        sudokuBoardView = findViewById(R.id.dailySudokuBoard)
        headerTitle = findViewById(R.id.dailyHeaderTitle)
        timerText = findViewById(R.id.dailyTimerText)
        difficultyText = findViewById(R.id.dailyDifficultyText)
        hintButton = findViewById(R.id.btnDailyHint)
        notesButton = findViewById(R.id.btnDailyNotes)
        
        // Initialize hint badge
        updateHintBadge()
        
        // Setup back button
        findViewById<ImageButton>(R.id.btnDailyBack).setOnClickListener {
            onBackPressed()
        }

        findViewById<ImageButton>(R.id.btnDailySettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        
        // Setup number buttons (matching MainActivity design)
        setupNumberButtons()
    }
    
    private fun initializeGame() {
        dailyChallengeManager = DailyChallengeManager(this)
        dailyChallengeStateManager = DailyChallengeStateManager(this)
        attemptStore = AttemptStateStore(this)
        audioManager = AudioManager.getInstance(this)
        
        // Check if today's challenge is already completed
        val hasCompletedToday = dailyChallengeManager.hasCompletedToday()
        val todayRecord = dailyChallengeManager.getTodayRecord()
        
        if (hasCompletedToday && todayRecord != null) {
            // Challenge already completed - show results dialog instead of starting game
            Log.d("DailyChallenge", "Today's challenge already completed, showing results")
            Log.d("DailyChallenge", "Loaded record: date=${todayRecord.date}, mistakes=${todayRecord.mistakes}, hints=${todayRecord.hintsUsed}, time=${todayRecord.timeSeconds}s")
            showCompletedChallengeResults(todayRecord)
            return
        }
        
        // Load other ads (banner ad already loaded in onCreate)
        adManager.loadInterstitialAd()
        Log.d("DailyChallenge", "Loading rewarded ad (should use test ads in debug builds)")
        adManager.loadRewardedAd()
        
        // Generate today's puzzle
        currentPuzzle = DailyChallengeGenerator.generateDailyPuzzle()
        
        // Setup Sudoku board
        sudokuBoardView.setBoardSize(9)
        
        // Reset puzzle tracking for rate limiter
        val dailyChallengeId = "daily_${currentPuzzle.date}"
        adRateLimiter.resetPuzzleTracking(dailyChallengeId)
        
        // Try to restore saved state first
        val savedState = dailyChallengeStateManager.loadState()
        
        if (savedState != null && savedState.date == currentPuzzle.date) {
            // Restore saved game state (same day, game in progress)
            restoreGameState(savedState)
        } else {
            // No saved state or date changed - start fresh
            // IMPORTANT: Clear any old attempt state if date changed
            if (savedState != null && savedState.date != currentPuzzle.date) {
                // Date changed - clear old attempt state for previous day
                Log.d("DailyChallenge", "New day detected (${savedState.date} -> ${currentPuzzle.date}), clearing old attempt state")
                attemptStore.clear("daily_${savedState.date}")
            }
            
            // Reset everything for fresh start
            totalMistakes = 0
            currentMaxMistakes = 0
            hintsUsed = 0
            
            // Load puzzle to board (this will initialize hints)
            loadPuzzleToBoard()
            
            // Initialize hints from settings (resets to max hints)
            sudokuBoardView.updateHintsFromSettings()
            
            // Initialize max mistakes for new day
            initializeMaxMistakes()
            
            // Reset mistakes in store for this date (ensure clean start)
            attemptStore.resetMistakes(dailyChallengeId)
            attemptStore.resetMaxMistakes(dailyChallengeId, getInitialMaxMistakes())
            
            // Update UI
            updateMistakesHud(0)
            updateHintBadge()
            
            Log.d("DailyChallenge", "Fresh game started for ${currentPuzzle.date} - hints: ${sudokuBoardView.getHintsRemaining()}/${sudokuBoardView.getMaxHintsPerGame()}, mistakes: 0/$currentMaxMistakes")
        }
        
        // Update UI
        updateHeader()
        updateDifficulty()
    }
    
    private fun restoreGameState(savedState: DailyChallengeStateManager.DailyChallengeState) {
        try {
            // Restore board state
            val boardArray = savedState.board.map { it.toIntArray() }.toTypedArray()
            val fixedArray = savedState.fixed.map { it.toBooleanArray() }.toTypedArray()
            
            if (boardArray.size != 9 || fixedArray.size != 9) {
                Log.w("DailyChallenge", "Invalid saved state, starting fresh")
                loadPuzzleToBoard()
                return
            }
            
            sudokuBoardView.setBoardState(boardArray, fixedArray)
            
            // Restore solution
            val solutionArray = savedState.solution.toIntArray()
            if (solutionArray.size == 81) {
                sudokuBoardView.setSolutionBoard(solutionArray)
            }
            
            // Restore hints state
            // Note: Don't restore savedMax - use current settings instead
            // This allows hints to update when settings change
            sudokuBoardView.setHintsState(
                savedRemaining = null, // Will be recalculated based on current settings
                savedUsed = savedState.hintsUsed,
                savedMax = null // Use current settings instead of saved value
            )
            
            // Update hints from current settings after restoring used count
            // This recalculates remaining hints based on current maxHintsPerGame setting
            // updateHintsFromSettings() now always recalculates hintsRemaining, fixing the bug
            // where hintsRemaining wasn't updated if maxHints didn't change
            sudokuBoardView.updateHintsFromSettings()
            updateHintBadge()
            
            // Restore game state
            gameTime = savedState.gameTime
            // Calculate new start time based on saved elapsed time
            gameStartTime = System.currentTimeMillis() - gameTime
            movesCount = savedState.movesCount
            hintsUsed = savedState.hintsUsed
            totalMistakes = savedState.mistakes
            isGameActive = savedState.isGameActive
            selectedNumber = savedState.selectedNumber
            
            // Update mistakes HUD
            updateMistakesHud(totalMistakes)
            
            // Update timer display immediately
            updateTimer()
            
            // Clear any selected cell first to prevent unwanted row highlighting
            sudokuBoardView.clearCellSelection()
            
            // Restore selected number highlighting (without selecting a cell)
            if (selectedNumber in 1..9) {
                sudokuBoardView.highlightNumber(selectedNumber)
                highlightActiveNumber(selectedNumber)
            }
            
            // Update mistakes in attempt store
            val dailyChallengeId = "daily_${currentPuzzle.date}"
            attemptStore.setMistakes(dailyChallengeId, totalMistakes)
            initializeMaxMistakes()
            
            Log.d("DailyChallenge", "Restored game state: time=${gameTime}ms, moves=$movesCount, hints=$hintsUsed, active=$isGameActive")
        } catch (e: Exception) {
            Log.e("DailyChallenge", "Error restoring game state", e)
            // Fallback to fresh start
            loadPuzzleToBoard()
            val dailyChallengeId = "daily_${currentPuzzle.date}"
            totalMistakes = attemptStore.getMistakes(dailyChallengeId)
            initializeMaxMistakes()
            updateMistakesHud(totalMistakes)
        }
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
        
        // Clear any selected cell to prevent row highlighting on start
        sudokuBoardView.clearCellSelection()
        sudokuBoardView.clearNumberHighlight()
        
        // Verify solution was set correctly
        Log.d("DailyChallenge", "Hint system initialized")
    }
    
    private fun setupClickListeners() {
        // Set up cell selection listener to update selected number and highlight when cell is clicked
        sudokuBoardView.setOnCellSelectedListener(object : SudokuBoardView.OnCellSelectedListener {
            override fun onCellSelected(row: Int, col: Int, isEditable: Boolean) {
                // When a cell is clicked, update selected number and highlight all matching numbers
                val cellValue = sudokuBoardView.getBoardValue(row, col)
                if (cellValue != 0) {
                    selectedNumber = cellValue
                    highlightActiveNumber(cellValue)
                    // highlightNumber is already called in onTouchEvent, but ensure it's called here too
                    sudokuBoardView.highlightNumber(cellValue)
                } else {
                    // Empty cell - clear number highlighting
                    selectedNumber = 0
                    highlightActiveNumber(0)
                }
            }
        })
        
        hintButton.setOnClickListener {
            // Check if there are incorrect numbers on the board
            if (sudokuBoardView.hasIncorrectUserEnteredNumbers()) {
                // Always show dialog when there are incorrect numbers
                showHintWithAutoFixDialog()
            } else {
                // No incorrect numbers - proceed with normal hint flow
                val hasFreeHints = sudokuBoardView.getHintsRemaining() > 0
                if (hasFreeHints) {
                    // Use hint normally
                    if (sudokuBoardView.revealHint()) {
                        SoundManager.getInstance(this@DailyChallengeActivity).playClick()
                        // Sync hintsUsed with board (board tracks it internally)
                        hintsUsed = sudokuBoardView.getHintsUsed()
                        
                        // Highlight the number that was placed by the hint (same as manual placement)
                        val placedNumber = sudokuBoardView.getBoardValue(sudokuBoardView.getSelectedRow(), sudokuBoardView.getSelectedCol())
                        if (placedNumber > 0) {
                            sudokuBoardView.highlightNumber(placedNumber)
                            highlightActiveNumber(placedNumber)
                        }
                        
                        // Update badge after using hint
                        updateHintBadge()
                        
                        // Check for completion after revealing hint (in case hint fills last cell)
                        if (sudokuBoardView.isBoardComplete()) {
                            completeGame()
                        }
                    } else {
                        // Show error message
                        val errorMsg = sudokuBoardView.getLastHintErrorMessage()
                        val message = when {
                            errorMsg != null -> errorMsg
                            sudokuBoardView.getSelectedRow() == -1 || sudokuBoardView.getSelectedCol() == -1 -> "Select cell first"
                            sudokuBoardView.getBoardValue(sudokuBoardView.getSelectedRow(), sudokuBoardView.getSelectedCol()) != 0 -> "Cell not empty"
                            else -> "Cannot hint"
                        }
                        showTooltip(hintButton, message)
                    }
                } else {
                    // No free hints remaining - check ad cooldown first
                    if (adManager.isRewardedAdInCooldown()) {
                        val formattedTime = adManager.getRewardedAdCooldownFormatted()
                        val message = "Ads are currently unavailable. Please wait $formattedTime or continue playing."
                        showTooltip(hintButton, message)
                        return@setOnClickListener
                    }
                    
                    // Check rate limiter and show ad
                    val puzzleId = "daily_${currentPuzzle.date}"
                    if (adRateLimiter.canShowAd(9, puzzleId)) {
                        showRewardedAdForHint(hintButton)
                    } else {
                        // Rate limited - show message
                        val message = adRateLimiter.getBlockedMessage(9, puzzleId)
                        showTooltip(hintButton, message)
                    }
                }
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
                // Play error sound
                SoundManager.getInstance(this@DailyChallengeActivity).playError()
                
                // Increment mistake counter (mistakes only increment, never decrement)
                // Even if player corrects a mistake later, the count stays the same
                val dailyChallengeId = "daily_${currentPuzzle.date}"
                val previousMistakes = totalMistakes
                totalMistakes = attemptStore.incrementMistakes(dailyChallengeId)
                // Ensure max mistakes is initialized
                if (currentMaxMistakes <= 0) {
                    initializeMaxMistakes()
                }
                // Get fresh max from store (dailyChallengeId already declared above)
                val defaultMax = getInitialMaxMistakes()
                val maxMistakes = attemptStore.getMaxMistakes(dailyChallengeId, defaultMax)
                currentMaxMistakes = if (maxMistakes > 0) maxMistakes else defaultMax
                Log.d("DailyChallenge", "Mistake detected! Previous: $previousMistakes, New total: $totalMistakes, Max: $currentMaxMistakes")
                
                // Update UI immediately - we're already on main thread from conflict detection
                val text = findViewById<TextView>(R.id.mistakesText)
                if (text != null) {
                    text.text = "$totalMistakes / $currentMaxMistakes"
                    Log.d("DailyChallenge", "Updated TextView directly to: ${text.text}")
                } else {
                    Log.e("DailyChallenge", "mistakesText TextView is null!")
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
            SoundManager.getInstance(this).playClick()
            
            // Check if there are free hints remaining
            val hasFreeHints = sudokuBoardView.getHintsRemaining() > 0
            if (hasFreeHints) {
                // Has free hints - use free hint with auto-fix (no ad)
                if (sudokuBoardView.revealHintWithAutoFix()) {
                    hintsUsed = sudokuBoardView.getHintsUsed()
                    
                    // Highlight the number that was placed by the hint
                    val placedNumber = sudokuBoardView.getBoardValue(sudokuBoardView.getSelectedRow(), sudokuBoardView.getSelectedCol())
                    if (placedNumber > 0) {
                        sudokuBoardView.highlightNumber(placedNumber)
                        highlightActiveNumber(placedNumber)
                    }
                    
                    updateHintBadge()
                    
                    // Check for completion
                    if (sudokuBoardView.isBoardComplete()) {
                        completeGame()
                    }
                }
            } else {
                // No free hints remaining - check ad cooldown first
                if (adManager.isRewardedAdInCooldown()) {
                    val formattedTime = adManager.getRewardedAdCooldownFormatted()
                    val message = "Ads are currently unavailable. Please wait $formattedTime or continue playing."
                    Toast.makeText(this@DailyChallengeActivity, message, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                
                // Check rate limiter and show ad
                val puzzleId = "daily_${currentPuzzle.date}"
                if (adRateLimiter.canShowAd(9, puzzleId)) {
                    showRewardedAdForHintWithAutoFix()
                } else {
                    // Rate limited - show message
                    val message = adRateLimiter.getBlockedMessage(9, puzzleId)
                    Toast.makeText(this@DailyChallengeActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
            SoundManager.getInstance(this).playClick()
        }
        
        dialog.show()
    }
    
    private fun showRewardedAdForHintWithAutoFix() {
        // Show rewarded ad, then remove incorrect numbers and place hint after ad is watched
        if (adManager.isRewardedAdLoaded()) {
            adManager.showRewardedAd(this, onAdClosed = {
                // Record that ad was shown
                val puzzleId = "daily_${currentPuzzle.date}"
                adRateLimiter.recordAdShown(9, puzzleId)
                
                // After ad is watched, grant 1 hint
                sudokuBoardView.grantHint()
                
                // Remove incorrect numbers and place hint
                if (sudokuBoardView.revealHintWithAutoFix()) {
                    hintsUsed = sudokuBoardView.getHintsUsed()
                    
                    // Highlight the number that was placed by the hint
                    val placedNumber = sudokuBoardView.getBoardValue(sudokuBoardView.getSelectedRow(), sudokuBoardView.getSelectedCol())
                    if (placedNumber > 0) {
                        sudokuBoardView.highlightNumber(placedNumber)
                        highlightActiveNumber(placedNumber)
                    }
                    
                    updateHintBadge()
                    
                    // Check for completion
                    if (sudokuBoardView.isBoardComplete()) {
                        completeGame()
                    }
                }
                // Preload next rewarded ad
                adManager.loadRewardedAd()
            }, onUserEarnedReward = {
                // Reward earned callback - hint is granted in onAdClosed
            })
        } else {
            // Ad not loaded - check cooldown first
            if (adManager.isRewardedAdInCooldown()) {
                val formattedTime = adManager.getRewardedAdCooldownFormatted()
                val message = "Ads are currently unavailable. Please wait $formattedTime or continue playing."
                Toast.makeText(this@DailyChallengeActivity, message, Toast.LENGTH_LONG).show()
                return
            }
            
            // Show loading message and load with callback
            showTooltip(hintButton, "Loading ad...")
            
            // Track if ad was shown to prevent multiple callbacks
            var adShown = false
            
            // Create the callback for when ad is loaded
            val onAdLoadedCallback: () -> Unit = {
                if (!adShown && adManager.isRewardedAdLoaded()) {
                    adShown = true
                    // Automatically show the ad when it's loaded
                    adManager.showRewardedAd(this, onAdClosed = {
                        // Record that ad was shown
                        val puzzleId = "daily_${currentPuzzle.date}"
                        adRateLimiter.recordAdShown(9, puzzleId)
                        
                        // After ad is watched, grant 1 hint
                        sudokuBoardView.grantHint()
                        
                        // Remove incorrect numbers and place hint
                        if (sudokuBoardView.revealHintWithAutoFix()) {
                            hintsUsed = sudokuBoardView.getHintsUsed()
                            
                            // Highlight the number that was placed by the hint
                            val placedNumber = sudokuBoardView.getBoardValue(sudokuBoardView.getSelectedRow(), sudokuBoardView.getSelectedCol())
                            if (placedNumber > 0) {
                                sudokuBoardView.highlightNumber(placedNumber)
                                highlightActiveNumber(placedNumber)
                            }
                            
                            updateHintBadge()
                            
                            // Check for completion
                            if (sudokuBoardView.isBoardComplete()) {
                                completeGame()
                            }
                        }
                        // Preload next rewarded ad (without failure callback to avoid cooldown on preload)
                        adManager.loadRewardedAd()
                    }, onUserEarnedReward = {
                        // Reward earned callback - hint is granted in onAdClosed
                    })
                }
            }
            
            // Create failure callback
            val onAdFailureCallback: (String) -> Unit = { errorMessage ->
                if (!adShown) {
                    adShown = true
                    // Show user-friendly error message
                    Toast.makeText(this@DailyChallengeActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
            
            // Load ad with callbacks
            adManager.loadRewardedAd(onAdLoadedCallback, onAdFailureCallback)
            
            // Set up timeout fallback (10 seconds)
            Handler(Looper.getMainLooper()).postDelayed({
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
                // Record that ad was shown
                val puzzleId = "daily_${currentPuzzle.date}"
                adRateLimiter.recordAdShown(9, puzzleId)
                
                // After ad is watched, grant 1 hint
                sudokuBoardView.grantHint()
                
                // Update badge after granting hint
                updateHintBadge()
                
                // Check if a cell is selected - if yes, use the hint immediately
                val hasSelectedCell = sudokuBoardView.getSelectedRow() != -1 && sudokuBoardView.getSelectedCol() != -1
                if (hasSelectedCell) {
                    // No incorrect numbers check here - already handled before showing ad
                    if (sudokuBoardView.revealHint()) {
                        SoundManager.getInstance(this@DailyChallengeActivity).playClick()
                        // Sync hintsUsed with board (board tracks it internally)
                        hintsUsed = sudokuBoardView.getHintsUsed()
                        
                        // Highlight the number that was placed by the hint (same as manual placement)
                        val placedNumber = sudokuBoardView.getBoardValue(sudokuBoardView.getSelectedRow(), sudokuBoardView.getSelectedCol())
                        if (placedNumber > 0) {
                            sudokuBoardView.highlightNumber(placedNumber)
                            highlightActiveNumber(placedNumber)
                        }
                        
                        // Update badge after using hint
                        updateHintBadge()
                        
                        // Check for completion after revealing hint (in case hint fills last cell)
                        if (sudokuBoardView.isBoardComplete()) {
                            completeGame()
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
            // Ad not loaded - check cooldown first
            if (adManager.isRewardedAdInCooldown()) {
                val formattedTime = adManager.getRewardedAdCooldownFormatted()
                val message = "Ads are currently unavailable. Please wait $formattedTime or continue playing."
                showTooltip(hintButton, message)
                return
            }
            
            // Show loading message and load with callback
            showTooltip(hintButton, "Loading ad...")
            
            // Track if ad was shown to prevent multiple callbacks
            var adShown = false
            
            // Create the callback for when ad is loaded
            val onAdLoadedCallback: () -> Unit = {
                if (!adShown && adManager.isRewardedAdLoaded()) {
                    adShown = true
                    // Automatically show the ad when it's loaded
                    adManager.showRewardedAd(this, onAdClosed = {
                        // Record that ad was shown
                        val puzzleId = "daily_${currentPuzzle.date}"
                        adRateLimiter.recordAdShown(9, puzzleId)
                        
                        // After ad is watched, grant 1 hint
                        sudokuBoardView.grantHint()
                        
                        // Update badge after granting hint
                        updateHintBadge()
                        
                        // Check if a cell is selected - if yes, use the hint immediately
                        val hasSelectedCell = sudokuBoardView.getSelectedRow() != -1 && sudokuBoardView.getSelectedCol() != -1
                        if (hasSelectedCell) {
                            // Check if there are incorrect numbers - if yes, show dialog
                            if (sudokuBoardView.hasIncorrectUserEnteredNumbers()) {
                                showHintWithAutoFixDialog()
                            } else if (sudokuBoardView.revealHint()) {
                                SoundManager.getInstance(this@DailyChallengeActivity).playClick()
                                // Sync hintsUsed with board (board tracks it internally)
                                hintsUsed = sudokuBoardView.getHintsUsed()
                                
                                // Highlight the number that was placed by the hint (same as manual placement)
                                val placedNumber = sudokuBoardView.getBoardValue(sudokuBoardView.getSelectedRow(), sudokuBoardView.getSelectedCol())
                                if (placedNumber > 0) {
                                    sudokuBoardView.highlightNumber(placedNumber)
                                    highlightActiveNumber(placedNumber)
                                }
                                
                                // Update badge after using hint
                                updateHintBadge()
                                
                                // Check for completion after revealing hint (in case hint fills last cell)
                                if (sudokuBoardView.isBoardComplete()) {
                                    completeGame()
                                }
                            }
                        } else {
                            // No cell selected or hint couldn't be used - badge already updated
                        }
                        // Preload next rewarded ad (without failure callback to avoid cooldown on preload)
                        adManager.loadRewardedAd()
                    }, onUserEarnedReward = {
                        // Reward earned callback - hint is granted in onAdClosed
                    })
                }
            }
            
            // Create failure callback
            val onAdFailureCallback: (String) -> Unit = { errorMessage ->
                if (!adShown) {
                    adShown = true
                    // Show user-friendly error message
                    showTooltip(hintButton, errorMessage)
                }
            }
            
            // Load ad with callbacks
            adManager.loadRewardedAd(onAdLoadedCallback, onAdFailureCallback)
            
            // Set up timeout fallback (10 seconds)
            Handler(Looper.getMainLooper()).postDelayed({
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
        val hintsRemaining = sudokuBoardView.getHintsRemaining()
        
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
    
    private fun showHintAddedAnimation(hintButton: Button) {
        // Use post to ensure button is laid out before getting position
        hintButton.post {
            // Create a TextView for the "+1" text
            val plusOneText = TextView(this).apply {
                text = "+1"
                setTextColor(Color.parseColor("#4CAF50")) // Green color for positive feedback
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
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
                .setInterpolator(android.view.animation.OvershootInterpolator())
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
    
    private fun getInitialMaxMistakes(): Int {
        // Daily Challenge is always 9x9
        return 3
    }
    
    private fun getMaxMistakes(): Int {
        // Always get fresh value from store to ensure it's up to date
        val defaultMax = getInitialMaxMistakes()
        val dailyChallengeId = "daily_${currentPuzzle.date}"
        val storedMax = attemptStore.getMaxMistakes(dailyChallengeId, defaultMax)
        // Update cached value
        currentMaxMistakes = if (storedMax > 0) storedMax else defaultMax
        return currentMaxMistakes
    }
    
    private fun initializeMaxMistakes() {
        val defaultMax = getInitialMaxMistakes()
        val dailyChallengeId = "daily_${currentPuzzle.date}"
        currentMaxMistakes = attemptStore.getMaxMistakes(dailyChallengeId, defaultMax)
        // Ensure it's at least the default
        if (currentMaxMistakes <= 0) {
            currentMaxMistakes = defaultMax
            attemptStore.setMaxMistakes(dailyChallengeId, defaultMax)
        }
    }
    
    private fun incrementMaxMistakes() {
        val defaultMax = getInitialMaxMistakes()
        val dailyChallengeId = "daily_${currentPuzzle.date}"
        currentMaxMistakes = attemptStore.incrementMaxMistakes(dailyChallengeId, defaultMax)
    }
    
    private fun updateMistakesHud(count: Int) {
        val text = findViewById<TextView>(R.id.mistakesText)
        if (text == null) {
            Log.e("DailyChallenge", "mistakesText TextView not found!")
            return
        }
        // Always get fresh max from store (don't use cached value)
        val defaultMax = getInitialMaxMistakes()
        val dailyChallengeId = "daily_${currentPuzzle.date}"
        val maxMistakes = attemptStore.getMaxMistakes(dailyChallengeId, defaultMax)
        // Update cached value
        currentMaxMistakes = if (maxMistakes > 0) maxMistakes else defaultMax
        val displayText = "$count / $currentMaxMistakes"
        Log.d("DailyChallenge", "updateMistakesHud: count=$count, max=$currentMaxMistakes (from store: $maxMistakes), display='$displayText'")
        text.text = displayText
        when {
            count >= currentMaxMistakes -> {
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
        text?.contentDescription = "Mistakes: $count of $currentMaxMistakes"
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
            // Reset attempt state and clear user entries (keep same puzzle)
            val dailyChallengeId = "daily_${currentPuzzle.date}"
            val defaultMax = getInitialMaxMistakes()
            attemptStore.clear(dailyChallengeId)
            attemptStore.resetMaxMistakes(dailyChallengeId, defaultMax)
            currentMaxMistakes = defaultMax
            totalMistakes = 0
            updateMistakesHud(0)
            // Reset to initial state (clears user entries, keeps puzzle clues - same game)
            sudokuBoardView.resetToInitialState()
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
    
    private fun showMistakesExhaustedDialog() {
        isGameActive = false
        stopGameTimer()
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_mistakes_exhausted, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        // Check internet connectivity
        val watchAdButton = view.findViewById<Button>(R.id.btnWatchAd)
        var connectivityCheckHandler: Handler? = null
        var connectivityCheckRunnable: Runnable? = null
        
        // Function to update button state based on internet connectivity
        fun updateWatchAdButtonState() {
            val hasInternet = NetworkUtils.isConnected(this)
            if (hasInternet) {
                watchAdButton.isEnabled = true
                watchAdButton.alpha = 1.0f
            } else {
                watchAdButton.isEnabled = false
                watchAdButton.alpha = 0.5f
            }
        }
        
        // Initial check
        updateWatchAdButtonState()
        
        // If no internet initially, show message
        if (!NetworkUtils.isConnected(this)) {
            Toast.makeText(this, "No internet connection. Connect to internet to watch ad.", Toast.LENGTH_LONG).show()
        }
        
        // Set up periodic connectivity check while dialog is open
        connectivityCheckHandler = Handler(Looper.getMainLooper())
        connectivityCheckRunnable = object : Runnable {
            override fun run() {
                if (dialog.isShowing) {
                    val wasDisabled = !watchAdButton.isEnabled
                    updateWatchAdButtonState()
                    
                    // If button was disabled and now enabled, show message
                    if (wasDisabled && watchAdButton.isEnabled) {
                        Toast.makeText(this@DailyChallengeActivity, "Internet connected! You can now watch ad.", Toast.LENGTH_SHORT).show()
                    }
                    
                    // Check again in 1 second
                    connectivityCheckHandler?.postDelayed(this, 1000)
                }
            }
        }
        
        // Start checking connectivity every second
        connectivityCheckHandler.postDelayed(connectivityCheckRunnable!!, 1000)
        
        // Stop checking when dialog is dismissed
        dialog.setOnDismissListener {
            connectivityCheckHandler?.removeCallbacks(connectivityCheckRunnable!!)
        }

        view.findViewById<Button>(R.id.btnRestart).setOnClickListener {
            dialog.dismiss()
            SoundManager.getInstance(this).playClick()
            // Reset attempt state and clear user entries (keep same puzzle)
            val dailyChallengeId = "daily_${currentPuzzle.date}"
            val defaultMax = getInitialMaxMistakes()
            attemptStore.clear(dailyChallengeId)
            attemptStore.resetMaxMistakes(dailyChallengeId, defaultMax)
            currentMaxMistakes = defaultMax
            totalMistakes = 0
            updateMistakesHud(0)
            // Reset to initial state (clears user entries, keeps puzzle clues - same game)
            sudokuBoardView.resetToInitialState()
            sudokuBoardView.clearNumberHighlight()
            gameStartTime = System.currentTimeMillis()
            isGameActive = true
            startGameTimer()
        }

        watchAdButton.setOnClickListener {
            // Check internet again before showing ad
            if (!NetworkUtils.isConnected(this)) {
                dialog.dismiss()
                // Show dialog with retry option
                showNoInternetDialog()
                return@setOnClickListener
            }
            
            // Check rate limiter before showing ad
            val puzzleId = "daily_${currentPuzzle.date}"
            if (!adRateLimiter.canShowAd(9, puzzleId)) {
                val message = adRateLimiter.getBlockedMessage(9, puzzleId)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            dialog.dismiss()
            SoundManager.getInstance(this).playClick()
            // Show rewarded ad to continue playing
            attemptShowRewardedAd()
        }

        dialog.show()
    }
    
    /**
     * Show dialog when internet is disconnected
     */
    private fun showNoInternetDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("No Internet Connection")
        builder.setMessage("You need an internet connection to watch ads and continue playing.\n\nPlease connect to the internet and try again, or reset the game to start fresh.")
        builder.setPositiveButton("Retry") { dialog, _ ->
            SoundManager.getInstance(this).playClick()
            dialog.dismiss()
            // Check internet again after a short delay
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                if (NetworkUtils.isConnected(this)) {
                    // Internet is now available - try to show ad
                    attemptShowRewardedAd()
                } else {
                    // Still no internet - show dialog again
                    showNoInternetDialog()
                }
            }, 500) // Small delay to allow network check
        }
        builder.setNeutralButton("Reset Game") { _, _ ->
            SoundManager.getInstance(this).playClick()
            // Reset game - lose all changes
            val dailyChallengeId = "daily_${currentPuzzle.date}"
            val defaultMax = getInitialMaxMistakes()
            attemptStore.clear(dailyChallengeId)
            attemptStore.resetMaxMistakes(dailyChallengeId, defaultMax)
            currentMaxMistakes = defaultMax
            totalMistakes = 0
            updateMistakesHud(0)
            sudokuBoardView.resetToInitialState()
            sudokuBoardView.clearNumberHighlight()
            gameStartTime = System.currentTimeMillis()
            isGameActive = true
            startGameTimer()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            SoundManager.getInstance(this).playClick()
            dialog.dismiss()
            // Re-show the mistakes exhausted dialog
            showMistakesExhaustedDialog()
        }
        builder.show()
    }
    
    private fun attemptShowRewardedAd() {
        if (adManager.isRewardedAdLoaded()) {
            // Increment max mistakes IMMEDIATELY when ad starts (not when it closes)
            // This ensures the increment is saved even if user closes game during ad
            val dailyChallengeId = "daily_${currentPuzzle.date}"
            val defaultMax = getInitialMaxMistakes()
            val newMax = attemptStore.incrementMaxMistakes(dailyChallengeId, defaultMax)
            currentMaxMistakes = newMax
            Log.d("DailyChallenge", "Max mistakes incremented to: $newMax (saved immediately)")
            
            // Update UI immediately to show new max
            val text = findViewById<TextView>(R.id.mistakesText)
            if (text != null) {
                text.text = "$totalMistakes / $newMax"
                Log.d("DailyChallenge", "Updated TextView to: ${text.text}")
            }
            updateMistakesHud(totalMistakes)
            
            adManager.showRewardedAd(this, onAdClosed = {
                // Record that ad was shown
                val puzzleId = "daily_${currentPuzzle.date}"
                adRateLimiter.recordAdShown(9, puzzleId)
                
                // Ad closed - just restart timer and reload next ad
                // Max mistakes already incremented above
                Log.d("DailyChallenge", "Ad closed, mistakes=$totalMistakes, max=$newMax")
                
                // Keep current mistakes (don't increment them)
                totalMistakes = attemptStore.getMistakes(dailyChallengeId)
                
                // Update UI again (in case it changed)
                val textAfter = findViewById<TextView>(R.id.mistakesText)
                if (textAfter != null) {
                    textAfter.text = "$totalMistakes / $newMax"
                }
                updateMistakesHud(totalMistakes)
                // Restart timer to continue playing
                isGameActive = true
                startGameTimer()
                // Preload next rewarded ad
                adManager.loadRewardedAd()
            }, onUserEarnedReward = {
                // Reward earned - max mistakes already incremented above
                Log.d("DailyChallenge", "User earned reward, max mistakes already incremented to: $newMax")
            })
        } else {
            // Ad not loaded - try loading it
            val handler = Handler(Looper.getMainLooper())
            var adShown = false
            
            val onAdLoadedCallback: () -> Unit = {
                if (!adShown && adManager.isRewardedAdLoaded()) {
                    adShown = true
                    attemptShowRewardedAd()
                }
            }
            
            adManager.loadRewardedAd(onAdLoadedCallback)
            
            // Timeout fallback
            handler.postDelayed({
                if (!adShown) {
                    if (adManager.isRewardedAdLoaded()) {
                        adShown = true
                        attemptShowRewardedAd()
                    } else {
                        // Still failed - check internet again
                        if (!NetworkUtils.isConnected(this)) {
                            showNoInternetDialog()
                        } else {
                            // Internet available but ad failed - restart game
                            val dailyChallengeId = "daily_${currentPuzzle.date}"
                            val defaultMax = getInitialMaxMistakes()
                            attemptStore.clear(dailyChallengeId)
                            attemptStore.resetMaxMistakes(dailyChallengeId, defaultMax)
                            currentMaxMistakes = defaultMax
                            totalMistakes = 0
                            updateMistakesHud(0)
                            sudokuBoardView.resetToInitialState()
                            sudokuBoardView.clearNumberHighlight()
                            gameStartTime = System.currentTimeMillis()
                            isGameActive = true
                            startGameTimer()
                        }
                    }
                }
            }, 10000) // 10 second timeout
        }
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
                    SoundManager.getInstance(this@DailyChallengeActivity).playClick()
                    selectedNumber = i
                    
                    // Always highlight all cells with this number (even if no cell is selected)
                    sudokuBoardView.highlightNumber(i)
                    highlightActiveNumber(i)
                    
                    // Only place the number if a cell is selected
                    val selectedRow = sudokuBoardView.getSelectedRow()
                    val selectedCol = sudokuBoardView.getSelectedCol()
                    if (selectedRow != -1 && selectedCol != -1) {
                        sudokuBoardView.setNumber(i)
                        movesCount++
                        
                        // Check for completion after placing number
                        if (sudokuBoardView.isBoardComplete()) {
                            completeGame()
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
        gameResultSaved = true
        
        val timeSeconds = (gameTime / 1000).toInt()
        
        // If in practice mode, don't save as new completion - just show results
        if (isPracticeMode) {
            Log.d("DailyChallenge", "Game completed in practice mode - not saving as new completion")
            // Show practice completion message
            val timeFormatted = String.format("%02d:%02d", timeSeconds / 60, timeSeconds % 60)
            android.widget.Toast.makeText(
                this,
                "Practice completed in $timeFormatted! Great job! 🎉",
                android.widget.Toast.LENGTH_LONG
            ).show()
            
            // Show the original completion dialog with results
            lastCompletionRecord?.let { record ->
                showCompletedChallengeResults(record)
            } ?: run {
                // If no record, just finish
                finish()
            }
            return
        }
        
        // Save completion record (use hintsUsed from board to ensure accuracy)
        val record = DailyChallengeManager.DailyRecord(
            date = currentPuzzle.date,
            timeSeconds = timeSeconds,
            moves = movesCount,
            difficulty = currentPuzzle.difficulty,
            hintsUsed = sudokuBoardView.getHintsUsed(),
            mistakes = totalMistakes // Save mistakes count
        )
        
        Log.d("DailyChallenge", "Saving completion record: date=${record.date}, mistakes=${record.mistakes}, hints=${record.hintsUsed}")
        dailyChallengeManager.saveDailyRecord(record)
        
        // Verify the record was saved correctly
        val verifiedRecord = dailyChallengeManager.getTodayRecord()
        Log.d("DailyChallenge", "Record saved and verified: mistakes=${verifiedRecord?.mistakes}, hints=${verifiedRecord?.hintsUsed}")
        
        // Clear saved state since game is completed
        dailyChallengeStateManager.clearState()
        
        // Use the verified record from storage to ensure consistency
        // This ensures we're showing the same data that will be loaded later
        val recordToShow = verifiedRecord ?: record
        Log.d("DailyChallenge", "Showing completion dialog with record: mistakes=${recordToShow.mistakes}, hints=${recordToShow.hintsUsed}")
        
        // Show completion dialog with the verified record
        showCompletionDialog(recordToShow)
    }
    
    /**
     * Show results dialog when user opens an already-completed daily challenge
     */
    private fun showCompletedChallengeResults(record: DailyChallengeManager.DailyRecord) {
        val stats = dailyChallengeManager.getUserStats()
        val timeFormatted = String.format("%02d:%02d", record.timeSeconds / 60, record.timeSeconds % 60)
        
        // Log the record data for debugging
        Log.d("DailyChallenge", "showCompletedChallengeResults - Record data: mistakes=${record.mistakes}, hints=${record.hintsUsed}, time=${record.timeSeconds}s")
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_daily_challenge_complete, null)
        
        // Set the stats data
        dialogView.findViewById<TextView>(R.id.completionTime)?.text = timeFormatted
        val mistakesText = record.mistakes.toString()
        Log.d("DailyChallenge", "Setting mistakes in dialog to: $mistakesText")
        dialogView.findViewById<TextView>(R.id.completionMistakes)?.text = mistakesText // Use mistakes from record
        dialogView.findViewById<TextView>(R.id.completionHints)?.text = record.hintsUsed.toString()
        dialogView.findViewById<TextView>(R.id.completionStreak)?.text = "${stats.streakDays} days"
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true) // Allow canceling since this is just viewing results
            .create()
        
        // Make background transparent around card for nicer presentation
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        var adShown = false
        
        // Setup button click listeners
        dialogView.findViewById<Button>(R.id.btnShare)?.setOnClickListener {
            adShown = true
            // Store the record so we can show dialog again after sharing
            lastCompletionRecord = record
            dialog.dismiss()
            shareResults()
            // Note: Ad will be shown after user returns from share (in onResume)
        }
        
        // Change "Done" button to "Play Again" for practice mode
        val doneButton = dialogView.findViewById<Button>(R.id.btnDone)
        doneButton?.text = "Play Again"
        doneButton?.setOnClickListener {
            adShown = true
            dialog.dismiss()
            // Allow them to play the same puzzle again for practice (won't save as new completion)
            startPracticeMode()
        }
        
        dialogView.findViewById<Button>(R.id.btnRate)?.setOnClickListener {
            Log.d("DailyChallenge", "Rate button clicked in daily challenge results dialog")
            openPlayStoreRating()
        }
        
        // Show interstitial ad when dialog is dismissed (fallback - only if no button was clicked)
        dialog.setOnDismissListener {
            if (!adShown && adManager.isInterstitialAdLoaded()) {
                // Show interstitial ad after dialog closes (only if ad is loaded and not already shown)
                adManager.showInterstitialAd(this, null)
            }
            // Preload next interstitial ad
            adManager.loadInterstitialAd()
            
            // If user just dismissed without clicking anything, go back to menu
            if (!adShown) {
                finish()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Start practice mode - allows playing the same puzzle again without saving completion
     */
    private fun startPracticeMode() {
        Log.d("DailyChallenge", "Starting practice mode for already-completed challenge")
        
        isPracticeMode = true
        
        // Load other ads
        adManager.loadInterstitialAd()
        adManager.loadRewardedAd()
        
        // Generate today's puzzle (same puzzle since it's the same date)
        currentPuzzle = DailyChallengeGenerator.generateDailyPuzzle()
        
        // Setup Sudoku board
        sudokuBoardView.setBoardSize(9)
        
        // Start fresh game (don't restore saved state for practice)
        loadPuzzleToBoard()
        
        // Reset game state for practice
        gameStartTime = System.currentTimeMillis()
        gameTime = 0
        isGameActive = true
        movesCount = 0
        hintsUsed = 0
        totalMistakes = 0
        currentMaxMistakes = 0
        gameResultSaved = false
        selectedNumber = 0
        
        // Start game timer
        startGameTimer()
        
        // Update UI
        updateHeader()
        updateDifficulty()
        updateHintBadge()
    }
    
    private fun showCompletionDialog(record: DailyChallengeManager.DailyRecord) {
        val stats = dailyChallengeManager.getUserStats()
        val timeFormatted = String.format("%02d:%02d", record.timeSeconds / 60, record.timeSeconds % 60)
        
        // Log the record data for debugging
        Log.d("DailyChallenge", "showCompletionDialog - Record data: mistakes=${record.mistakes}, hints=${record.hintsUsed}, time=${record.timeSeconds}s")
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_daily_challenge_complete, null)
        
        // Set the stats data
        dialogView.findViewById<TextView>(R.id.completionTime).text = timeFormatted
        // IMPORTANT: Use record.mistakes instead of totalMistakes
        // totalMistakes may be reset to 0 when viewing results later
        val mistakesText = record.mistakes.toString()
        Log.d("DailyChallenge", "Setting mistakes in completion dialog to: $mistakesText (from record)")
        dialogView.findViewById<TextView>(R.id.completionMistakes).text = mistakesText
        dialogView.findViewById<TextView>(R.id.completionHints).text = record.hintsUsed.toString()
        dialogView.findViewById<TextView>(R.id.completionStreak).text = "${stats.streakDays} days"
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // Make background transparent around card for nicer presentation
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        var adShown = false
        
        // Setup button click listeners
        dialogView.findViewById<Button>(R.id.btnShare).setOnClickListener {
            adShown = true
            // Store the record so we can show dialog again after sharing
            lastCompletionRecord = record
            dialog.dismiss()
            shareResults()
            // Note: Ad will be shown after user returns from share (in onResume)
        }
        
        dialogView.findViewById<Button>(R.id.btnDone).setOnClickListener {
            adShown = true
            dialog.dismiss()
            // Show interstitial ad before finishing
            adManager.showInterstitialAd(this) {
                finish()
            }
        }
        
        dialogView.findViewById<Button>(R.id.btnRate).setOnClickListener {
            Log.d("DailyChallenge", "Rate button clicked in daily challenge complete dialog")
            openPlayStoreRating()
        }
        
        // Show interstitial ad when dialog is dismissed (fallback - only if no button was clicked)
        dialog.setOnDismissListener {
            if (!adShown && adManager.isInterstitialAdLoaded()) {
                // Show interstitial ad after dialog closes (only if ad is loaded and not already shown)
                adManager.showInterstitialAd(this, null)
            }
            // Preload next interstitial ad
            adManager.loadInterstitialAd()
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
        
        // Launch share chooser
        val chooserIntent = Intent.createChooser(shareIntent, "Share your achievement")
        
        try {
            // Set flag to show completion dialog when user returns
            waitingForShareReturn = true
            
            // Launch share dialog
            startActivity(chooserIntent)
            
            Log.d("DailyChallenge", "Share dialog opened, will show completion dialog on return")
        } catch (e: Exception) {
            Log.e("DailyChallenge", "Error sharing results: ${e.message}", e)
            waitingForShareReturn = false
            // If share fails, show completion dialog immediately
            lastCompletionRecord?.let { showCompletionDialog(it) }
        }
    }
    
    private fun openPlayStoreRating() {
        Log.d("DailyChallenge", "openPlayStoreRating() called - opening Play Store rating")
        
        try {
            // For reliability, always open Play Store directly
            // In-App Review API is unreliable during development and may not show the dialog
            val reviewManager = InAppReviewManager(this)
            reviewManager.openPlayStoreDirectly()
            Log.d("DailyChallenge", "Play Store opening initiated")
        } catch (e: Exception) {
            Log.e("DailyChallenge", "Error in openPlayStoreRating: ${e.message}", e)
            android.widget.Toast.makeText(
                this,
                "Error opening Play Store. Please try again.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Save game state when paused (unless completed)
        saveGameState()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopGameTimer()
        // Save game state when destroyed (unless completed)
        saveGameState()
    }
    
    private fun saveGameState() {
        // Don't save if game is completed or already saved
        if (gameResultSaved || sudokuBoardView.isBoardComplete()) {
            dailyChallengeStateManager.clearState()
            return
        }
        
        // Check if currentPuzzle is initialized (fix crash when activity paused before initialization)
        if (!::currentPuzzle.isInitialized) {
            Log.d("DailyChallenge", "Cannot save state: currentPuzzle not initialized yet")
            return
        }
        
        // Check if there's any progress to save
        val boardState = sudokuBoardView.getBoardState()
        val fixedState = sudokuBoardView.getFixedState()
        val hasProgress = hasUserPlacedNumbers(boardState, fixedState) || gameTime > 0 || totalMistakes > 0
        
        if (!hasProgress) {
            // No progress to save, clear any existing state
            dailyChallengeStateManager.clearState()
            return
        }
        
        // Update game time before saving
        if (isGameActive) {
            gameTime = System.currentTimeMillis() - gameStartTime
        }
        
        // Get solution for saving
        val solution = sudokuBoardView.getSolutionForSaving()
        if (solution == null) {
            Log.w("DailyChallenge", "No solution available for saving")
            return
        }
        
        // Save state
        dailyChallengeStateManager.saveState(
            date = currentPuzzle.date,
            boardSize = 9,
            difficulty = currentPuzzle.difficulty,
            gameTime = gameTime,
            gameStartTime = gameStartTime,
            movesCount = movesCount,
            hintsUsed = sudokuBoardView.getHintsUsed(),
            hintsRemaining = sudokuBoardView.getHintsRemaining(),
            maxHints = sudokuBoardView.getMaxHintsPerGame(),
            mistakes = totalMistakes,
            board = boardState,
            fixed = fixedState,
            solution = solution,
            selectedNumber = selectedNumber,
            isGameActive = isGameActive,
            isCompleted = false
        )
        
        Log.d("DailyChallenge", "Saved game state: time=${gameTime}ms, moves=$movesCount")
    }
    
    private fun hasUserPlacedNumbers(board: Array<IntArray>, fixed: Array<BooleanArray>): Boolean {
        for (row in board.indices) {
            for (col in board[row].indices) {
                if (board[row][col] != 0 && !fixed[row][col]) {
                    return true
                }
            }
        }
        return false
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
        
        // Update message text to reflect that progress will be saved
        val messageText = dialogView.findViewById<TextView>(R.id.exitMessageText)
        messageText.text = "Are you sure you want to leave? Your progress will be saved automatically and you can continue later today."
        
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
            // Save state before exiting
            saveGameState()
            super.onBackPressed()
        }
        
        // Style the dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    override fun onResume() {
        super.onResume()
        // Re-enable fullscreen mode when resuming
        enableFullscreen()
        
        // Check if we're waiting for user to return from share dialog
        if (waitingForShareReturn && lastCompletionRecord != null) {
            waitingForShareReturn = false
            val record = lastCompletionRecord!!
            
            // Show completion dialog again so user can choose next action
            showCompletionDialog(record)
            
            // Show interstitial ad after dialog is shown (if available)
            Handler(Looper.getMainLooper()).postDelayed({
                if (adManager.isInterstitialAdLoaded()) {
                    adManager.showInterstitialAd(this, null)
                }
                // Preload next interstitial ad
                adManager.loadInterstitialAd()
            }, 500) // Small delay to let dialog appear first
            
            return // Don't do other resume actions
        }
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
}
