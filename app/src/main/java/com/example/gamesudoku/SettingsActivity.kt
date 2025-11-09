package com.example.gamesudoku

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var audioManager: AudioManager
    private lateinit var masterToggle: Switch
    private lateinit var hapticsToggle: Switch
    private lateinit var hintsToggle: Switch
    private lateinit var hintsCard: MaterialCardView
    private lateinit var hintsSummaryText: TextView
    private lateinit var questCodex: QuestCodex
    private lateinit var gameSettings: GameSettings
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        audioManager = AudioManager.getInstance(this)
        gameSettings = GameSettings.getInstance(this)
        questCodex = QuestCodex(this)
        initializeViews()
        setupListeners()
        setupResetButton()
        loadCurrentSettings()
    }
    
    private fun initializeViews() {
        masterToggle = findViewById(R.id.masterToggle)
        hapticsToggle = findViewById(R.id.hapticsToggle)
        hintsToggle = findViewById(R.id.hintsToggle)
        hintsCard = findViewById(R.id.hintsCard)
        hintsSummaryText = findViewById(R.id.hintsSummaryText)
        
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
        
        // Extended hints toggle
        hintsToggle.setOnCheckedChangeListener { _, isChecked ->
            gameSettings.setExtendedHintsEnabled(isChecked)
            updateHintSummary()
            val message = if (isChecked) "Extended hints enabled (50 per puzzle)" else "Extended hints disabled (2 per puzzle)"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        
        hintsCard.setOnClickListener {
            hintsToggle.isChecked = !hintsToggle.isChecked
        }
    }
    
    private fun setupResetButton() {
        val resetCard = findViewById<MaterialCardView>(R.id.resetCard)
        resetCard.setOnClickListener {
            showResetConfirmationDialog()
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
        hintsToggle.isChecked = gameSettings.isExtendedHintsEnabled()
        updateHintSummary()
        
        updateUIState()
    }
    
    private fun updateUIState() {
        // Haptics remain available even if sounds are muted
        hapticsToggle.isEnabled = true
        hapticsToggle.alpha = 1.0f
    }
    
    private fun updateHintSummary() {
        val isExtended = hintsToggle.isChecked
        hintsSummaryText.text = if (isExtended) {
            "Currently 50 hints per puzzle"
        } else {
            "Currently 2 hints per puzzle"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Settings are automatically saved when changed
    }
}


