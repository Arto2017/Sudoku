package com.artashes.sudoku

import android.content.Context

/**
 * Sound Integration Helper
 * 
 * This class provides easy integration of sound effects into the Sudoku game.
 * It maps game events to appropriate sound categories and handles playback.
 */
class SoundIntegration(private val context: Context) {
    
    private val audioManager = AudioManager.getInstance(context)
    
    init {
        // Load sound effects (you'll need to add these sound files to res/raw/)
        loadSoundEffects()
    }
    
    private fun loadSoundEffects() {
        // Load sound effects - you'll need to add these files to res/raw/
        // For now, we'll use placeholder resource IDs
        
        // audioManager.loadSound(AudioManager.SoundCategory.UI_CLICK, R.raw.sfx_ui_click)
        // audioManager.loadSound(AudioManager.SoundCategory.CONFIRM_PLACE, R.raw.sfx_place_confirm)
        // audioManager.loadSound(AudioManager.SoundCategory.PENCIL_TOGGLE, R.raw.sfx_pencil_toggle)
        // audioManager.loadSound(AudioManager.SoundCategory.ERROR_INVALID, R.raw.sfx_error_buzz)
        // audioManager.loadSound(AudioManager.SoundCategory.ACHIEVEMENT_SUCCESS, R.raw.sfx_success_chime)
        // audioManager.loadSound(AudioManager.SoundCategory.HINT_REVEAL, R.raw.sfx_hint_reveal)
        // audioManager.loadSound(AudioManager.SoundCategory.UNDO_REDO, R.raw.sfx_undo_redo)
        // audioManager.loadSound(AudioManager.SoundCategory.HIGHLIGHT_SELECT, R.raw.sfx_highlight_select)
        // audioManager.loadSound(AudioManager.SoundCategory.SYSTEM_NOTIFICATION, R.raw.sfx_system_notification)
    }
    
    // Game Event Sound Triggers
    
    fun onNumberButtonClick() {
        audioManager.playSound(AudioManager.SoundCategory.UI_CLICK)
    }
    
    fun onNumberPlaced() {
        audioManager.playSound(AudioManager.SoundCategory.CONFIRM_PLACE)
    }
    
    fun onPencilMarkToggled() {
        audioManager.playSound(AudioManager.SoundCategory.PENCIL_TOGGLE)
    }
    
    fun onInvalidMove() {
        audioManager.playSound(AudioManager.SoundCategory.ERROR_INVALID)
    }
    
    fun onPuzzleCompleted() {
        audioManager.playSound(AudioManager.SoundCategory.ACHIEVEMENT_SUCCESS)
        // Duck music briefly for success sound
        audioManager.duckMusic(1000L)
    }
    
    fun onHintUsed() {
        audioManager.playSound(AudioManager.SoundCategory.HINT_REVEAL)
    }
    
    fun onUndoRedo() {
        audioManager.playSound(AudioManager.SoundCategory.UNDO_REDO)
    }
    
    fun onCellSelected() {
        audioManager.playSound(AudioManager.SoundCategory.HIGHLIGHT_SELECT)
    }
    
    fun onDailyChallengeReady() {
        audioManager.playSound(AudioManager.SoundCategory.SYSTEM_NOTIFICATION)
    }
    
    fun onQuestUnlocked() {
        audioManager.playSound(AudioManager.SoundCategory.SYSTEM_NOTIFICATION)
    }
    
    // UI Event Sound Triggers
    
    fun onButtonClick() {
        audioManager.playSound(AudioManager.SoundCategory.UI_CLICK)
    }
    
    fun onMenuOpen() {
        audioManager.playSound(AudioManager.SoundCategory.UI_CLICK)
    }
    
    fun onSettingsOpen() {
        audioManager.playSound(AudioManager.SoundCategory.UI_CLICK)
    }
    
    // Background Music
    
    fun startBackgroundMusic() {
        // audioManager.playMusic(R.raw.music_ambient_loop, true)
    }
    
    fun stopBackgroundMusic() {
        audioManager.stopMusic()
    }
    
    fun pauseBackgroundMusic() {
        audioManager.pauseMusic()
    }
    
    fun resumeBackgroundMusic() {
        audioManager.resumeMusic()
    }
    
    // Settings Integration
    
    fun getAudioManager(): AudioManager = audioManager
    
    fun cleanup() {
        audioManager.cleanup()
    }
}


