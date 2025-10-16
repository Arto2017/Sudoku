package com.example.gamesudoku

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import android.view.View
import android.graphics.Color
import com.google.android.material.card.MaterialCardView

class WorldSelectionActivity : AppCompatActivity() {

    private lateinit var questProgress: QuestProgress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // Refresh progress when returning from quest
        setupWorldButtons()
    }
}












