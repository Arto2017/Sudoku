package com.example.gamesudoku

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import android.view.View
import android.graphics.Color
import com.google.android.material.card.MaterialCardView

class RealmSelectionActivity : AppCompatActivity() {

    private lateinit var questCodex: QuestCodex

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realm_selection)

        questCodex = QuestCodex(this)
        
        // Ensure all puzzles have correct difficulty settings
        questCodex.resetPuzzleDifficulties()

        setupRealmButtons()
        updateCodexProgress()
        setupResetButton()

        // Back button in header
        findViewById<View>(R.id.realmSelectionBackButton)?.setOnClickListener {
            finish()
        }
    }
    
    private fun setupResetButton() {
        val resetButton = findViewById<Button>(R.id.resetQuestHistoryButton)
        resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }
    }
    
    private fun showResetConfirmationDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Reset Quest History")
        builder.setMessage("Are you sure you want to reset all quest progress? This will:\n\n" +
                "• Clear all puzzle completions\n" +
                "• Reset all realm progress\n" +
                "• Lock all realms except Tier I\n" +
                "• Clear all codex fragments\n" +
                "• Clear all puzzle attempt states\n\n" +
                "This action cannot be undone.")
        
        builder.setPositiveButton("Reset") { _, _ ->
            questCodex.resetAllProgress()
            
            // Also clear attempt states for all quest puzzles
            val attemptStore = AttemptStateStore(this)
            val realms = questCodex.getRealms()
            realms.forEach { realm ->
                val puzzleChain = questCodex.getPuzzleChain(realm.id)
                puzzleChain?.puzzles?.forEach { puzzle ->
                    attemptStore.clear(puzzle.id)
                }
            }
            
            Toast.makeText(this, "Quest history has been reset", Toast.LENGTH_SHORT).show()
            setupRealmButtons()
            updateCodexProgress()
        }
        
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun setupRealmButtons() {
        val realms = questCodex.getRealms()
        
        // Tier IV - Realm of Shadows (9x9 Expert)
        setupRealmButton(R.id.tierIVCard, realms.find { it.id == "shadows" }, "Tier IV", "9×9 • Expert")
        
        // Tier III - Realm of Flame (9x9 Intermediate)
        setupRealmButton(R.id.tierIIICard, realms.find { it.id == "flame" }, "Tier III", "9×9 • Intermediate")
        
        // Tier II - Realm of Trials (6x6 Intermediate)
        setupRealmButton(R.id.tierIICard, realms.find { it.id == "trials" }, "Tier II", "6×6 • Intermediate")
        
        // Tier I - Realm of Echoes (6x6 Beginner)
        setupRealmButton(R.id.tierICard, realms.find { it.id == "echoes" }, "Tier I", "6×6 • Beginner")
    }

    private fun setupRealmButton(cardId: Int, realm: QuestRealm?, tierName: String, difficultyText: String) {
        val card = findViewById<MaterialCardView>(cardId)
        val lockIcon = findViewById<ImageView>(getLockIconId(cardId))
        val lockText = findViewById<TextView>(getLockTextId(cardId))
        val progressText = findViewById<TextView>(getProgressTextId(cardId))

        realm?.let { questRealm ->
            if (questRealm.isUnlocked) {
                // Realm is unlocked
                lockIcon?.visibility = View.GONE
                lockText?.visibility = View.GONE
                
                // Check if realm is perfect (all puzzles 3 stars)
                val isPerfect = questCodex.isRealmPerfect(questRealm.id)
                
                // Update progress
                val progress = (questRealm.puzzlesCompleted * 100) / questRealm.totalPuzzles
                val clamped = progress.coerceIn(0, 100)
                if (isPerfect) {
                    progressText?.text = "⭐ Perfect!"
                    progressText?.setTextColor(Color.parseColor("#FFD700"))
                } else {
                    progressText?.text = "$clamped%"
                }
                
                card.setOnClickListener {
                    val intent = Intent(this, RealmQuestActivity::class.java).apply {
                        putExtra("realm_id", questRealm.id)
                    }
                    startActivity(intent)
                }
                
                // DEV: Long-press to mark realm as incomplete and lock others (no-op for unlocked)
                card.setOnLongClickListener {
                    if (DEV_MODE) {
                        Toast.makeText(this, "[DEV] ${questRealm.name} already unlocked", Toast.LENGTH_SHORT).show()
                        true
                    } else false
                }
            } else {
                // Realm is locked
                lockIcon?.visibility = View.VISIBLE
                lockText?.visibility = View.VISIBLE
                lockText?.text = "Complete previous tier to unlock"
                progressText?.text = "0%"
                
                card.setOnClickListener {
                    Toast.makeText(this, "Complete the previous tier to unlock $tierName!", Toast.LENGTH_LONG).show()
                }
                
                // DEV: Long-press to unlock this realm and first puzzles for testing
                card.setOnLongClickListener {
                    if (DEV_MODE) {
                        questCodex.devUnlockRealm(questRealm.id)
                        Toast.makeText(this, "[DEV] Unlocked ${questRealm.name}", Toast.LENGTH_SHORT).show()
                        setupRealmButtons()
                        true
                    } else false
                }
            }
        }
    }

    companion object {
        // Toggle to false to revert test behavior
        private const val DEV_MODE = true
    }

    private fun getLockIconId(cardId: Int): Int {
        return when (cardId) {
            R.id.tierIVCard -> R.id.tierIVLockIcon
            R.id.tierIIICard -> R.id.tierIIILockIcon
            R.id.tierIICard -> R.id.tierIILockIcon
            R.id.tierICard -> R.id.tierILockIcon
            else -> R.id.tierIVLockIcon
        }
    }

    private fun getLockTextId(cardId: Int): Int {
        return when (cardId) {
            R.id.tierIVCard -> R.id.tierIVLockText
            R.id.tierIIICard -> R.id.tierIIILockText
            R.id.tierIICard -> R.id.tierIILockText
            R.id.tierICard -> R.id.tierILockText
            else -> R.id.tierIVLockText
        }
    }

    private fun getProgressTextId(cardId: Int): Int {
        return when (cardId) {
            R.id.tierIVCard -> R.id.tierIVProgressText
            R.id.tierIIICard -> R.id.tierIIIProgressText
            R.id.tierIICard -> R.id.tierIIProgressText
            R.id.tierICard -> R.id.tierIProgressText
            else -> R.id.tierIVProgressText
        }
    }

    private fun updateCodexProgress() {
        val realms = questCodex.getRealms()
        val totalRealms = realms.size
        val completedRealms = realms.count { it.isCompleted }
        val totalPuzzles = realms.sumOf { it.totalPuzzles }
        val completedPuzzles = realms.sumOf { it.puzzlesCompleted }
        
        // Update star collection display
        val totalStars = questCodex.getTotalStars()
        val maxStars = questCodex.getMaxStars()
        findViewById<TextView>(R.id.totalStarsText).text = "⭐ $totalStars/$maxStars"
        findViewById<ProgressBar>(R.id.starProgressBar).progress = totalStars
        findViewById<ProgressBar>(R.id.starProgressBar).max = maxStars
        
        // Update motivational message
        val motivationalMessage = questCodex.getMotivationalMessage()
        findViewById<TextView>(R.id.motivationalMessageText).text = motivationalMessage
    }

    override fun onResume() {
        super.onResume()
        // Refresh realm status when returning from quest
        setupRealmButtons()
        updateCodexProgress()
    }
}







