package com.artashes.sudoku

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.*
import android.view.View
import android.graphics.Color
import com.google.android.material.card.MaterialCardView

class WorldSelectionActivity : AppCompatActivity() {

    private lateinit var questProgress: QuestProgress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable fullscreen/immersive mode
        enableFullscreen()
        
        setContentView(R.layout.activity_world_selection)

        questProgress = QuestProgress(this)
        
        setupWorldButtons()
    }

    private fun setupWorldButtons() {
        // Mystic Forest Button
        val mysticForestCard = findViewById<MaterialCardView>(R.id.mysticForestCard)
        mysticForestCard.setOnClickListener {
            val intent = Intent(this, QuestMapActivity::class.java).apply {
                putExtra("world", QuestWorld.MYSTIC_FOREST.name)
            }
            startActivity(intent)
        }

        // Ancient Temple Button
        val ancientTempleCard = findViewById<MaterialCardView>(R.id.ancientTempleCard)
        val templeLockIcon = findViewById<ImageView>(R.id.templeLockIcon)
        val templeLockText = findViewById<TextView>(R.id.templeLockText)
        
        val isTempleUnlocked = questProgress.isWorldUnlocked(QuestWorld.ANCIENT_TEMPLE)
        
        if (isTempleUnlocked) {
            // Temple is unlocked
            templeLockIcon.visibility = View.GONE
            templeLockText.visibility = View.GONE
            ancientTempleCard.setOnClickListener {
                val intent = Intent(this, QuestMapActivity::class.java).apply {
                    putExtra("world", QuestWorld.ANCIENT_TEMPLE.name)
                }
                startActivity(intent)
            }
        } else {
            // Temple is locked
            templeLockIcon.visibility = View.VISIBLE
            templeLockText.visibility = View.VISIBLE
            ancientTempleCard.setOnClickListener {
                Toast.makeText(this, "Complete Mystic Forest to unlock Ancient Temple!", Toast.LENGTH_LONG).show()
            }
        }

        // Update progress displays
        updateWorldProgress()
    }

    private fun updateWorldProgress() {
        // Mystic Forest Progress
        val mysticProgress = questProgress.getProgressPercentageForWorld(QuestWorld.MYSTIC_FOREST)
        val mysticStars = questProgress.getTotalStarsForWorld(QuestWorld.MYSTIC_FOREST)
        val mysticCompleted = questProgress.getCompletedLevelsForWorld(QuestWorld.MYSTIC_FOREST)
        
        findViewById<TextView>(R.id.mysticProgressText).text = "$mysticProgress%"
        findViewById<TextView>(R.id.mysticStarsText).text = "⭐ $mysticStars"
        findViewById<TextView>(R.id.mysticCompletedText).text = "$mysticCompleted/12"

        // Ancient Temple Progress
        val templeProgress = questProgress.getProgressPercentageForWorld(QuestWorld.ANCIENT_TEMPLE)
        val templeStars = questProgress.getTotalStarsForWorld(QuestWorld.ANCIENT_TEMPLE)
        val templeCompleted = questProgress.getCompletedLevelsForWorld(QuestWorld.ANCIENT_TEMPLE)
        
        findViewById<TextView>(R.id.templeProgressText).text = "$templeProgress%"
        findViewById<TextView>(R.id.templeStarsText).text = "⭐ $templeStars"
        findViewById<TextView>(R.id.templeCompletedText).text = "$templeCompleted/18"
    }

    override fun onResume() {
        super.onResume()
        
        // Re-enable fullscreen mode when resuming
        enableFullscreen()
        
        // Refresh progress when returning from quest
        setupWorldButtons()
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












