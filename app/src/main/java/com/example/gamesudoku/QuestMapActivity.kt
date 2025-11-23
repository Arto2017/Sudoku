package com.artashes.sudoku

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.*
import android.view.View
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class QuestMapActivity : AppCompatActivity() {

    private lateinit var questProgress: QuestProgress
    private lateinit var currentWorld: QuestWorld
    private lateinit var progressText: TextView
    private lateinit var starsText: TextView
    private lateinit var currentLevelText: TextView
    private lateinit var continueButton: Button
    private lateinit var mysticForestQuestView: MysticForestQuestView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable fullscreen/immersive mode
        enableFullscreen()
        
        // Get the world from intent
        val worldName = intent.getStringExtra("world") ?: QuestWorld.MYSTIC_FOREST.name
        currentWorld = QuestWorld.valueOf(worldName)
        
        // Set layout based on world
        when (currentWorld) {
            QuestWorld.MYSTIC_FOREST -> setContentView(R.layout.activity_quest_map_forest)
            QuestWorld.ANCIENT_TEMPLE -> setContentView(R.layout.activity_quest_map_temple)
        }

        questProgress = QuestProgress(this)
        
        initializeViews()
        setupQuestData()
        setupContinueButton()
    }

    private fun initializeViews() {
        progressText = findViewById(R.id.progressText)
        starsText = findViewById(R.id.starsText)
        currentLevelText = findViewById(R.id.currentLevelText)
        continueButton = findViewById(R.id.continueButton)
        
        // Initialize Mystic Forest Quest View for forest world
        if (currentWorld == QuestWorld.MYSTIC_FOREST) {
            mysticForestQuestView = findViewById(R.id.mysticForestQuestView)
            setupMysticForestQuestView()
        }
    }

    private fun setupQuestData() {
        val levels = questProgress.getLevelsForWorld(currentWorld)
        val completedLevels = questProgress.getCompletedLevelsForWorld(currentWorld)
        val totalStars = questProgress.getTotalStarsForWorld(currentWorld)
        val progressPercentage = questProgress.getProgressPercentageForWorld(currentWorld)
        val currentLevel = questProgress.getCurrentLevelForWorld(currentWorld)

        // Update Mystic Forest Quest View
        if (currentWorld == QuestWorld.MYSTIC_FOREST) {
            mysticForestQuestView.setQuestData(levels)
        }

        // Update statistics
        starsText.text = "⭐ $totalStars Stars"
        progressText.text = "$progressPercentage%"

        // Update current level
        currentLevel?.let { level ->
            currentLevelText.text = "Level ${level.id}"
        } ?: run {
            currentLevelText.text = "All Levels Completed!"
        }

        // Animate progress percentage
        animateProgress(progressPercentage)
    }

    private fun setupMysticForestQuestView() {
        mysticForestQuestView.setOnNodeClickListener { levelId ->
            // Start the selected level
            val level = questProgress.getLevelsForWorld(currentWorld).find { it.id == levelId }
            level?.let {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("quest_level_id", it.id)
                    putExtra("board_size", it.boardSize)
                    putExtra("difficulty", it.difficulty.name)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun animateProgress(targetPercentage: Int) {
        val animator = ValueAnimator.ofInt(0, targetPercentage)
        animator.duration = 1500
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            progressText.text = "$value%"
        }
        animator.start()
    }

    private fun setupContinueButton() {
        val currentLevel = questProgress.getCurrentLevelForWorld(currentWorld)
        
        if (currentLevel != null) {
            continueButton.text = "Continue Quest"
            continueButton.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("quest_level_id", currentLevel.id)
                    putExtra("board_size", currentLevel.boardSize)
                    putExtra("difficulty", currentLevel.difficulty.name)
                }
                startActivity(intent)
            }
        } else {
            continueButton.text = "World Complete!"
            continueButton.isEnabled = false
            continueButton.setBackgroundColor(Color.parseColor("#9E9E9E"))
        }
    }


    override fun onResume() {
        super.onResume()
        
        // Re-enable fullscreen mode when resuming
        enableFullscreen()
        
        // Refresh quest data when returning from game
        setupQuestData()
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
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
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
    }
}
