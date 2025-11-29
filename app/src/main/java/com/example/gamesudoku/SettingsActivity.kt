package com.artashes.sudoku

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var audioManager: AudioManager
    private lateinit var masterToggle: Switch
    private lateinit var hapticsToggle: Switch
    private lateinit var questCodex: QuestCodex
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable fullscreen/immersive mode
        enableFullscreen()
        
        setContentView(R.layout.activity_settings)
        
        audioManager = AudioManager.getInstance(this)
        questCodex = QuestCodex(this)
        initializeViews()
        setupListeners()
        setupResetButton()
        setupRateButton()
        loadCurrentSettings()
        
        // Hide reset card if opened from quest game
        val fromQuestGame = intent.getBooleanExtra("from_quest_game", false)
        if (fromQuestGame) {
            val resetCard = findViewById<MaterialCardView>(R.id.resetCard)
            resetCard.visibility = View.GONE
        }
    }
    
    private fun initializeViews() {
        masterToggle = findViewById(R.id.masterToggle)
        hapticsToggle = findViewById(R.id.hapticsToggle)
        
        // Setup back button
        findViewById<ImageButton>(R.id.settingsBackButton).setOnClickListener {
            finish()
        }
    }
    
    private fun setupListeners() {
        // Master toggle
        masterToggle.setOnCheckedChangeListener { _, isChecked ->
            audioManager.updateMasterVolume(isChecked)
            if (isChecked) {
                audioManager.updateEffectsVolume(0.75f)
                audioManager.updateMusicVolume(0.4f)
            }
            updateUIState()
        }
        
        // Haptics toggle
        hapticsToggle.setOnCheckedChangeListener { _, isChecked ->
            audioManager.updateHapticsEnabled(isChecked)
        }
    }
    
    private fun setupResetButton() {
        val resetCard = findViewById<MaterialCardView>(R.id.resetCard)
        resetCard.setOnClickListener {
            showResetConfirmationDialog()
        }
    }
    
    private fun setupRateButton() {
        val rateCard = findViewById<MaterialCardView>(R.id.rateCard)
        rateCard.setOnClickListener {
            openPlayStoreRating()
        }
    }
    
    private fun openPlayStoreRating() {
        // Use Google Play In-App Review API for beautiful native rating dialog
        val reviewManager = InAppReviewManager(this)
        reviewManager.requestReview {
            // Review flow completed
            Log.d("SettingsActivity", "In-app review completed")
        }
    }
    
    private fun showResetConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reset_quest_history, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val cancelButton = dialogView.findViewById<Button>(R.id.resetDialogCancel)
        val confirmButton = dialogView.findViewById<Button>(R.id.resetDialogConfirm)
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        confirmButton.setOnClickListener {
            questCodex.resetAllProgress()
            
            val attemptStore = AttemptStateStore(this)
            val realms = questCodex.getRealms()
            realms.forEach { realm ->
                questCodex.getPuzzleChain(realm.id)?.puzzles?.forEach { puzzle ->
                    attemptStore.clear(puzzle.id)
                }
            }
            
            Toast.makeText(this, "Quest history has been reset", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun loadCurrentSettings() {
        val settings = audioManager.getSettings()
        
        masterToggle.isChecked = settings.masterEnabled
        hapticsToggle.isChecked = settings.hapticsEnabled
        
        updateUIState()
    }
    
    private fun updateUIState() {
        // Haptics remain available even if sounds are muted
        hapticsToggle.isEnabled = true
        hapticsToggle.alpha = 1.0f
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
    
    override fun onDestroy() {
        super.onDestroy()
        // Settings are automatically saved when changed
    }
}


