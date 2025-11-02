package com.example.gamesudoku

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import android.app.AlertDialog
import android.content.Context
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.view.MotionEvent
import android.widget.ProgressBar
import android.widget.TextView
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import java.text.SimpleDateFormat
import java.util.*

class MainMenuActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOARD_SIZE = "board_size"
    }
    
    private lateinit var questCodex: QuestCodex
    private lateinit var questProgressRing: ProgressBar
    private lateinit var questLevelProgressBar: ProgressBar
    private lateinit var dailyChallengeManager: DailyChallengeManager
    private lateinit var dailyTimerHandler: Handler
    private lateinit var dailyTimerRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        // Initialize quest system
        questCodex = QuestCodex(this)
        questProgressRing = findViewById(R.id.questProgressRing)
        questLevelProgressBar = findViewById(R.id.questLevelProgressBar)
        
        // Initialize daily challenge system
        dailyChallengeManager = DailyChallengeManager(this)
        dailyTimerHandler = Handler(Looper.getMainLooper())

        // Start entrance animations
        startEntranceAnimations()

        // Setup carousel
        setupCarousel()
        
        // Update quest progress
        updateQuestProgress()
        
        // Update daily challenge card
        updateDailyChallengeCard()
        
        // Start daily challenge timer
        startDailyChallengeTimer()

        // Quick Play button with animation - starts game directly with 9x9 medium
        val quickPlayButton = findViewById<View>(R.id.btnQuickPlay)
        quickPlayButton.setOnClickListener {
            // Add click animation
            it.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            // Start game directly with 9x9 medium difficulty
                            val intent = Intent(this, MainActivity::class.java).apply {
                                putExtra(QuickPlayActivity.EXTRA_BOARD_SIZE, 9)
                                putExtra(QuickPlayActivity.EXTRA_DIFFICULTY, SudokuGenerator.Difficulty.MEDIUM.name)
                            }
                            startActivity(intent)
                        }
                        .start()
                }
                .start()
        }
        
        // Add continuous pulsing animation to Quick Play button
        addPulsingAnimation(quickPlayButton)

        // Statistics Button Handler
        findViewById<View>(R.id.btnStatistics).setOnClickListener {
            val intent = Intent(this, StatisticsActivity::class.java)
            startActivity(intent)
        }

        // Carousel card click handlers
        findViewById<View>(R.id.dailyChallengeCard).setOnClickListener {
            val intent = Intent(this, DailyChallengeActivity::class.java)
            startActivity(intent)
        }
        
        findViewById<View>(R.id.questCard).setOnClickListener {
            showQuestProgressDialog()
        }
        
        // Daily Challenge Button Handler
        findViewById<View>(R.id.btnDailyChallenge).setOnClickListener {
            val intent = Intent(this, DailyChallengeActivity::class.java)
            startActivity(intent)
        }
        
        // Quest Card Button Handler
        findViewById<View>(R.id.btnChooseLevel).setOnClickListener {
            val intent = Intent(this, RealmSelectionActivity::class.java)
            startActivity(intent)
        }

    }

    private fun startEntranceAnimations() {
        val appHeader = findViewById<View>(R.id.appHeader)
        val carouselContainer = findViewById<View>(R.id.carouselContainer)
        val quickPlayContainer = findViewById<View>(R.id.quickPlayContainer)

        // Initially hide elements that will animate in
        appHeader.alpha = 0f
        appHeader.translationY = -50f
        carouselContainer.alpha = 0f
        carouselContainer.translationY = 20f
        quickPlayContainer.alpha = 0f
        quickPlayContainer.translationY = 20f

        // Animate app header first
        appHeader.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Animate carousel after delay
        Handler(Looper.getMainLooper()).postDelayed({
            carouselContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }, 200)

        // Animate Quick Play CTA after carousel
        Handler(Looper.getMainLooper()).postDelayed({
            quickPlayContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }, 400)
    }

    private fun setupCarousel() {
        // Cards are now fixed in place without scrolling
        // Add card press animations
        val cards = listOf(
            findViewById<View>(R.id.dailyChallengeCard),
            findViewById<View>(R.id.questCard)
        )
        
        cards.forEach { card ->
            card.setOnTouchListener { view, motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate()
                            .scaleX(1.05f)
                            .scaleY(1.05f)
                            .setDuration(100)
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start()
                    }
                }
                false
            }
        }
    }
    
    private fun addPulsingAnimation(view: View) {
        val pulseAnimator = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.05f, 1.0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        val pulseAnimatorY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.05f, 1.0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Start pulsing after entrance animation completes
        Handler(Looper.getMainLooper()).postDelayed({
            pulseAnimator.start()
            pulseAnimatorY.start()
        }, 1200) // Start after all entrance animations
    }
    
    
    private fun updateQuestProgress() {
        val realms = questCodex.getRealms()
        var totalGames = 0
        var completedGames = 0
        var completedLevels = 0
        
        realms.forEach { realm ->
            totalGames += realm.totalPuzzles
            completedGames += realm.puzzlesCompleted
            
            // Count completed levels (a level is complete when all 10 games are done)
            if (realm.puzzlesCompleted >= realm.totalPuzzles) {
                completedLevels++
            }
        }
        
        // Circular Ring: Show progress of all 40 games (0-100%)
        val gamesProgressPercentage = if (totalGames > 0) (completedGames * 100) / totalGames else 0
        
        // Horizontal Bar: Show progress of 4 levels (0%, 25%, 50%, 75%, 100%)
        val levelsProgressPercentage = (completedLevels * 25) // 25% per completed level
        
        // Animate both progress bars
        animateProgressRing(gamesProgressPercentage)
        animateLevelProgressBar(levelsProgressPercentage)
    }
    
    private fun animateProgressRing(targetProgress: Int) {
        val currentProgress = questProgressRing.progress
        val animator = ObjectAnimator.ofInt(questProgressRing, "progress", currentProgress, targetProgress)
        animator.duration = 1000
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }
    
    private fun animateLevelProgressBar(targetProgress: Int) {
        val currentProgress = questLevelProgressBar.progress
        val animator = ObjectAnimator.ofInt(questLevelProgressBar, "progress", currentProgress, targetProgress)
        animator.duration = 1200
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }
    
    private fun showQuestProgressDialog() {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_quest_progress, null)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Make dialog background transparent
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        
        // Get quest data
        val realms = questCodex.getRealms()
        var totalGames = 0
        var completedGames = 0
        var completedLevels = 0
        var unlockedRealms = 0
        
        realms.forEach { realm ->
            totalGames += realm.totalPuzzles
            completedGames += realm.puzzlesCompleted
            if (realm.isUnlocked) unlockedRealms++
            
            // Count completed levels (a level is complete when all 10 games are done)
            if (realm.puzzlesCompleted >= realm.totalPuzzles) {
                completedLevels++
            }
        }
        
        // Circular Ring: Show progress of all 40 games (0-100%)
        val gamesProgressPercentage = if (totalGames > 0) (completedGames * 100) / totalGames else 0
        
        // Horizontal Bar: Show progress of 4 levels (0%, 25%, 50%, 75%, 100%)
        val levelsProgressPercentage = (completedLevels * 25) // 25% per completed level
        
        // Update dialog content
        val progressRing = dialogView.findViewById<ProgressBar>(R.id.dialogProgressRing)
        val progressText = dialogView.findViewById<TextView>(R.id.dialogProgressText)
        val completedText = dialogView.findViewById<TextView>(R.id.dialogCompletedText)
        val currentRealmText = dialogView.findViewById<TextView>(R.id.dialogCurrentRealmText)
        val realmsText = dialogView.findViewById<TextView>(R.id.dialogRealmsText)
        val playButton = dialogView.findViewById<Button>(R.id.dialogPlayButton)
        val closeButton = dialogView.findViewById<Button>(R.id.dialogCloseButton)
        
        // Animate progress ring
        progressRing?.let { ring ->
            val animator = ObjectAnimator.ofInt(ring, "progress", 0, gamesProgressPercentage)
            animator.duration = 1500
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.start()
        }
        
        progressText?.text = "$gamesProgressPercentage%"
        completedText?.text = "Games: $completedGames/$totalGames completed"
        currentRealmText?.text = "Levels: $completedLevels/4 completed (${levelsProgressPercentage}%)"
        realmsText?.text = "Unlocked Realms: $unlockedRealms/${realms.size}"
        
        // Button handlers
        playButton?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, RealmSelectionActivity::class.java)
            startActivity(intent)
        }
        
        closeButton?.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh quest progress when returning to main menu
        updateQuestProgress()
        // Refresh daily challenge card
        updateDailyChallengeCard()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop daily challenge timer
        stopDailyChallengeTimer()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop daily challenge timer
        stopDailyChallengeTimer()
    }
    
    private fun updateDailyChallengeCard() {
        val today = dailyChallengeManager.getTodayDateString()
        val hasCompletedToday = dailyChallengeManager.hasCompletedToday()
        val streakDays = dailyChallengeManager.getStreakDays()
        val stats = dailyChallengeManager.getUserStats()
        
        // Update date and difficulty
        val dateText = findViewById<TextView>(R.id.dailyChallengeDate)
        val difficulty = DailyChallengeGenerator.determineDifficulty(Date())
        val dateFormatter = SimpleDateFormat("MMM dd", Locale.US)
        dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
        val formattedDate = dateFormatter.format(Date())
        dateText.text = "$formattedDate • ${difficulty.name}"
        
        // Update streak badge
        val streakBadge = findViewById<TextView>(R.id.dailyStreakBadge)
        streakBadge.text = streakDays.toString()
        streakBadge.visibility = if (streakDays > 0) View.VISIBLE else View.GONE
        
        // Update status
        val statusText = findViewById<TextView>(R.id.dailyStatusText)
        val statusBadge = findViewById<LinearLayout>(R.id.dailyStatusBadge)
        
        if (hasCompletedToday) {
            statusText.text = "You solved today"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
            statusBadge.setBackgroundResource(R.drawable.daily_status_badge_completed)
        } else {
            statusText.text = "Ready to play"
            statusText.setTextColor(Color.parseColor("#FF6B35"))
            statusBadge.setBackgroundResource(R.drawable.daily_status_badge_ready)
        }
        
        // Update button text
        val button = findViewById<Button>(R.id.btnDailyChallenge)
        button.text = if (hasCompletedToday) "View Results" else "Play"
    }
    
    private fun startDailyChallengeTimer() {
        dailyTimerRunnable = object : Runnable {
            override fun run() {
                val timerText = findViewById<TextView>(R.id.dailyTimerText)
                timerText.text = dailyChallengeManager.formatTimeRemaining()
                dailyTimerHandler.postDelayed(this, 1000) // Update every second
            }
        }
        dailyTimerHandler.post(dailyTimerRunnable)
    }
    
    private fun stopDailyChallengeTimer() {
        dailyTimerHandler.removeCallbacks(dailyTimerRunnable)
    }
}

