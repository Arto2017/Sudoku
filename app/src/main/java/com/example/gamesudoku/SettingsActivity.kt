package com.example.gamesudoku

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.SeekBar

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var audioManager: AudioManager
    private lateinit var masterToggle: Switch
    private lateinit var effectsSeekBar: SeekBar
    private lateinit var musicSeekBar: SeekBar
    private lateinit var hapticsToggle: Switch
    private lateinit var effectsTestButton: Button
    private lateinit var musicTestButton: Button
    private lateinit var resetButton: Button
    
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
        effectsSeekBar = findViewById(R.id.effectsSeekBar)
        musicSeekBar = findViewById(R.id.musicSeekBar)
        hapticsToggle = findViewById(R.id.hapticsToggle)
        effectsTestButton = findViewById(R.id.effectsTestButton)
        musicTestButton = findViewById(R.id.musicTestButton)
        resetButton = findViewById(R.id.resetButton)
        
        // Setup seek bars
        effectsSeekBar.max = 100
        musicSeekBar.max = 100
        
        // Setup back button
        findViewById<ImageButton>(R.id.settingsBackButton).setOnClickListener {
            finish()
        }
    }
    
    private fun setupListeners() {
        // Master toggle
        masterToggle.setOnCheckedChangeListener { _, isChecked ->
            audioManager.updateMasterVolume(isChecked)
            updateUIState()
        }
        
        // Effects volume
        effectsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100f
                    audioManager.updateEffectsVolume(volume)
                    updateVolumeLabels()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Music volume
        musicSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100f
                    audioManager.updateMusicVolume(volume)
                    updateVolumeLabels()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Haptics toggle
        hapticsToggle.setOnCheckedChangeListener { _, isChecked ->
            audioManager.updateHapticsEnabled(isChecked)
        }
        
        // Test buttons
        effectsTestButton.setOnClickListener {
            audioManager.playSound(AudioManager.SoundCategory.UI_CLICK)
        }
        
        musicTestButton.setOnClickListener {
            // Play a short music sample if available
            // For now, just play a success sound as music sample
            audioManager.playSound(AudioManager.SoundCategory.ACHIEVEMENT_SUCCESS)
        }
        
        // Reset button
        resetButton.setOnClickListener {
            resetToDefaults()
        }
    }
    
    private fun loadCurrentSettings() {
        val settings = audioManager.getSettings()
        
        masterToggle.isChecked = settings.masterEnabled
        effectsSeekBar.progress = (settings.effectsVolume * 100).toInt()
        musicSeekBar.progress = (settings.musicVolume * 100).toInt()
        hapticsToggle.isChecked = settings.hapticsEnabled
        
        updateUIState()
        updateVolumeLabels()
    }
    
    private fun updateUIState() {
        val masterEnabled = masterToggle.isChecked
        val isEnabled = masterEnabled
        
        effectsSeekBar.isEnabled = isEnabled
        musicSeekBar.isEnabled = isEnabled
        hapticsToggle.isEnabled = isEnabled
        effectsTestButton.isEnabled = isEnabled
        musicTestButton.isEnabled = isEnabled
        
        // Update visual state
        val alpha = if (isEnabled) 1.0f else 0.5f
        effectsSeekBar.alpha = alpha
        musicSeekBar.alpha = alpha
        hapticsToggle.alpha = alpha
    }
    
    private fun updateVolumeLabels() {
        val effectsPercent = effectsSeekBar.progress
        val musicPercent = musicSeekBar.progress
        
        findViewById<TextView>(R.id.effectsVolumeLabel).text = "Effects: $effectsPercent%"
        findViewById<TextView>(R.id.musicVolumeLabel).text = "Music: $musicPercent%"
    }
    
    private fun resetToDefaults() {
        masterToggle.isChecked = true
        effectsSeekBar.progress = 75
        musicSeekBar.progress = 40
        hapticsToggle.isChecked = true
        
        audioManager.updateMasterVolume(true)
        audioManager.updateEffectsVolume(0.75f)
        audioManager.updateMusicVolume(0.4f)
        audioManager.updateHapticsEnabled(true)
        
        updateUIState()
        updateVolumeLabels()
        
        GameNotification.showSuccess(this, "Settings reset to defaults", 2000)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Settings are automatically saved when changed
    }
}


