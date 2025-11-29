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
    private lateinit var attemptStore: AttemptStateStore
    
    // Tooltip management
    private var currentTooltip: PopupWindow? = null
    
    // Banner ad
    private var bannerAdView: AdView? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set window background to match game background (prevent wrong color in status bar area)
        window.setBackgroundDrawableResource(R.drawable.parchment_background)
        
        // Enable fullscreen/immersive mode
        enableFullscreen()
        
        setContentView(R.layout.activity_daily_challenge)
        
        // Initialize AdMob and load banner ad first (like MainActivity)
        adManager = AdManager(this)
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
        
        // Load other ads (banner ad already loaded in onCreate)
        adManager.loadInterstitialAd()
        adManager.loadRewardedAd()
        
        // Generate today's puzzle
        currentPuzzle = DailyChallengeGenerator.generateDailyPuzzle()
        
        // Setup Sudoku board
        sudokuBoardView.setBoardSize(9)
        
        // Try to restore saved state first
        val savedState = dailyChallengeStateManager.loadState()
        
        if (savedState != null && savedState.date == currentPuzzle.date) {
            // Restore saved game state
            restoreGameState(savedState)
        } else {
            // No saved state or date changed - start fresh
            loadPuzzleToBoard()
            
            // Load mistakes from store for daily challenge
            val dailyChallengeId = "daily_${currentPuzzle.date}"
            totalMistakes = attemptStore.getMistakes(dailyChallengeId)
            updateMistakesHud(totalMistakes)
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
            
            Log.d("DailyChallenge", "Restored game state: time=${gameTime}ms, moves=$movesCount, hints=$hintsUsed, active=$isGameActive")
        } catch (e: Exception) {
            Log.e("DailyChallenge", "Error restoring game state", e)
            // Fallback to fresh start
            loadPuzzleToBoard()
            val dailyChallengeId = "daily_${currentPuzzle.date}"
            totalMistakes = attemptStore.getMistakes(dailyChallengeId)
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
                    // No free hints remaining - show ad directly
                    showRewardedAdForHint(hintButton)
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
                
                // Increment mistake counter
                val dailyChallengeId = "daily_${currentPuzzle.date}"
                totalMistakes = attemptStore.incrementMistakes(dailyChallengeId)
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

                // Mistakes are unlimited (infinity) - no game over dialog
                // Player can continue playing regardless of mistake count
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
                // No free hints remaining - show ad first, then remove incorrect numbers and place hint
                showRewardedAdForHintWithAutoFix()
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
                }
            }
            
            // Load ad with callback
            adManager.loadRewardedAd(onAdLoadedCallback)
            
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
                    SoundManager.getInstance(this@DailyChallengeActivity).playClick()
                    sudokuBoardView.setNumber(i)
                    sudokuBoardView.highlightNumber(i) // Highlight all cells with this number
                    movesCount++
                    selectedNumber = i
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
        gameResultSaved = true
        
        val timeSeconds = (gameTime / 1000).toInt()
        
        // Save completion record (use hintsUsed from board to ensure accuracy)
        val record = DailyChallengeManager.DailyRecord(
            date = currentPuzzle.date,
            timeSeconds = timeSeconds,
            moves = movesCount,
            difficulty = currentPuzzle.difficulty,
            hintsUsed = sudokuBoardView.getHintsUsed()
        )
        
        dailyChallengeManager.saveDailyRecord(record)
        
        // Clear saved state since game is completed
        dailyChallengeStateManager.clearState()
        
        // Show completion dialog
        showCompletionDialog(record)
    }
    
    private fun showCompletionDialog(record: DailyChallengeManager.DailyRecord) {
        val stats = dailyChallengeManager.getUserStats()
        val timeFormatted = String.format("%02d:%02d", record.timeSeconds / 60, record.timeSeconds % 60)
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_daily_challenge_complete, null)
        
        // Set the stats data
        dialogView.findViewById<TextView>(R.id.completionTime).text = timeFormatted
        dialogView.findViewById<TextView>(R.id.completionMistakes).text = totalMistakes.toString()
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
            dialog.dismiss()
            shareResults()
            // Show interstitial ad after sharing
            if (adManager.isInterstitialAdLoaded()) {
                adManager.showInterstitialAd(this, null)
            }
            // Preload next interstitial ad
            adManager.loadInterstitialAd()
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
        
        startActivity(Intent.createChooser(shareIntent, "Share your achievement"))
    }
    
    private fun openPlayStoreRating() {
        val packageName = packageName
        Log.d("DailyChallenge", "Opening Play Store rating for package: $packageName")
        
        try {
            // Try to open the Play Store app directly
            val marketUri = Uri.parse("market://details?id=$packageName")
            val intent = Intent(Intent.ACTION_VIEW, marketUri)
            startActivity(intent)
            Log.d("DailyChallenge", "Opened Play Store app with URI: $marketUri")
        } catch (e: Exception) {
            // If Play Store app is not available, open in browser
            Log.d("DailyChallenge", "Play Store app not available, trying browser: ${e.message}")
            try {
                val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                val intent = Intent(Intent.ACTION_VIEW, webUri)
                startActivity(intent)
                Log.d("DailyChallenge", "Opened Play Store in browser with URI: $webUri")
            } catch (e2: Exception) {
                Log.e("DailyChallenge", "Failed to open Play Store: ${e2.message}")
                Toast.makeText(this, "Unable to open Play Store", Toast.LENGTH_SHORT).show()
            }
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
