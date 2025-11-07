package com.example.gamesudoku

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.graphics.Color
import com.google.android.material.card.MaterialCardView

class RealmSelectionActivity : AppCompatActivity() {

    private lateinit var questCodex: QuestCodex
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_realm_selection)

        questCodex = QuestCodex(this)
        
        // Ensure all puzzles have correct difficulty settings
        questCodex.resetPuzzleDifficulties()
        
        // Initialize sound manager
        soundManager = SoundManager.getInstance(this)

        // Initialize previous star count
        previousStarCount = questCodex.getTotalStars()

        setupRealmButtons()
        setupResetButton()

        // Back button in header
        findViewById<View>(R.id.realmSelectionBackButton)?.setOnClickListener {
            finish()
        }
        
        // Update progress after layout is ready
        findViewById<View>(R.id.starProgressFill)?.post {
            updateCodexProgress()
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

    private var previousStarCount = 0
    
    private fun updateCodexProgress() {
        val realms = questCodex.getRealms()
        val totalRealms = realms.size
        val completedRealms = realms.count { it.isCompleted }
        val totalPuzzles = realms.sumOf { it.totalPuzzles }
        val completedPuzzles = realms.sumOf { it.puzzlesCompleted }
        
        // Update star collection display
        val totalStars = questCodex.getTotalStars()
        val maxStars = questCodex.getMaxStars()
        
        // Update text
        val starsText = findViewById<TextView>(R.id.totalStarsText)
        starsText?.text = "$totalStars / $maxStars"
        
        // Animate progress bar fill
        animateStarProgress(totalStars, maxStars)
        
        // Animate number count-up if stars increased
        if (totalStars > previousStarCount) {
            animateStarCountUp(previousStarCount, totalStars, starsText)
            animateStarIcon()
        } else {
            // Still animate the number without count-up effect
            starsText?.text = "$totalStars / $maxStars"
        }
        
        previousStarCount = totalStars
        
        // Update motivational message
        val motivationalMessage = questCodex.getMotivationalMessage()
        findViewById<TextView>(R.id.motivationalMessageText)?.text = motivationalMessage
    }
    
    private fun animateStarProgress(currentStars: Int, maxStars: Int) {
        val progressFill = findViewById<View>(R.id.starProgressFill) ?: return
        val progressBarContainer = progressFill.parent as? View ?: return
        
        // Ensure container has been measured
        if (progressBarContainer.width == 0) {
            progressBarContainer.post {
                animateStarProgress(currentStars, maxStars)
            }
            return
        }
        
        val progressPercent = if (maxStars > 0) (currentStars.toFloat() / maxStars.toFloat()) * 100f else 0f
        val targetWidth = (progressBarContainer.width * progressPercent / 100f).toInt()
        
        // Get current width (use 0 if not set yet)
        val layoutParams = progressFill.layoutParams
        val currentWidth = if (layoutParams.width > 0) layoutParams.width else 0
        
        // Animate width change
        val animator = ValueAnimator.ofInt(currentWidth, targetWidth)
        animator.duration = 600
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            layoutParams.width = animatedValue.coerceAtLeast(0)
            progressFill.layoutParams = layoutParams
        }
        animator.start()
    }
    
    private fun animateStarCountUp(from: Int, to: Int, textView: TextView) {
        val animator = ValueAnimator.ofInt(from, to)
        animator.duration = 600
        animator.interpolator = OvershootInterpolator()
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Int
            val maxStars = questCodex.getMaxStars()
            textView.text = "$animatedValue / $maxStars"
            
            // Scale animation for visual feedback
            textView.scaleX = 1.1f
            textView.scaleY = 1.1f
            textView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(200)
                .start()
        }
        animator.start()
    }
    
    private fun animateStarIcon() {
        val starIcon = findViewById<ImageView>(R.id.starIcon) ?: return
        
        // Sparkle animation
        starIcon.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .rotation(360f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                starIcon.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    override fun onResume() {
        super.onResume()
        // Refresh realm status when returning from quest
        setupRealmButtons()
        
        // Update progress after layout is ready
        findViewById<View>(R.id.starProgressFill)?.post {
            updateCodexProgress()
        }
    }
}







