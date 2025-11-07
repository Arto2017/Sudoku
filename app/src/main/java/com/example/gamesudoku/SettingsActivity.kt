package com.example.gamesudoku

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var audioManager: AudioManager
    private lateinit var masterToggle: Switch
    private lateinit var hapticsToggle: Switch
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        audioManager = AudioManager.getInstance(this)
        initializeViews()
        setupListeners()
        loadCurrentSettings()
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
    
    override fun onDestroy() {
        super.onDestroy()
        // Settings are automatically saved when changed
    }
}


