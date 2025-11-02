package com.example.gamesudoku

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager as SystemAudioManager
import android.media.SoundPool
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*

class AudioManager private constructor(context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AudioManager? = null
        
        fun getInstance(context: Context): AudioManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Sound categories
    enum class SoundCategory {
        UI_CLICK,
        CONFIRM_PLACE,
        PENCIL_TOGGLE,
        ERROR_INVALID,
        ACHIEVEMENT_SUCCESS,
        HINT_REVEAL,
        UNDO_REDO,
        HIGHLIGHT_SELECT,
        SYSTEM_NOTIFICATION
    }
    
    // Sound settings
    data class SoundSettings(
        var masterEnabled: Boolean = true,
        var effectsVolume: Float = 0.75f, // 75% of master
        var musicVolume: Float = 0.4f,    // 40% of master
        var hapticsEnabled: Boolean = true
    )
    
    private val context: Context = context.applicationContext
    private var soundPool: SoundPool? = null
    private var musicPlayer: MediaPlayer? = null
    private var soundSettings: SoundSettings = SoundSettings()
    private val soundMap = mutableMapOf<SoundCategory, Int>()
    private val lastPlayTime = mutableMapOf<SoundCategory, Long>()
    private val RATE_LIMIT_MS = 100L // Prevent same sound > 10 times/sec
    
    // Volume levels
    private val MASTER_VOLUME = 1.0f
    private val EFFECTS_VOLUME_MULTIPLIER = 0.75f
    private val MUSIC_VOLUME_MULTIPLIER = 0.4f
    
    init {
        initializeSoundPool()
        loadSettings()
    }
    
    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(8) // Limit polyphony
            .setAudioAttributes(audioAttributes)
            .build()
    }
    
    private fun loadSettings() {
        val prefs = context.getSharedPreferences("sound_settings", Context.MODE_PRIVATE)
        soundSettings = SoundSettings(
            masterEnabled = prefs.getBoolean("master_enabled", true),
            effectsVolume = prefs.getFloat("effects_volume", 0.75f),
            musicVolume = prefs.getFloat("music_volume", 0.4f),
            hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
        )
    }
    
    fun saveSettings() {
        val prefs = context.getSharedPreferences("sound_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("master_enabled", soundSettings.masterEnabled)
            putFloat("effects_volume", soundSettings.effectsVolume)
            putFloat("music_volume", soundSettings.musicVolume)
            putBoolean("haptics_enabled", soundSettings.hapticsEnabled)
            apply()
        }
    }
    
    fun loadSound(category: SoundCategory, resourceId: Int) {
        try {
            val soundId = soundPool?.load(context, resourceId, 1) ?: return
            soundMap[category] = soundId
            Log.d("AudioManager", "Loaded sound: $category")
        } catch (e: Exception) {
            Log.e("AudioManager", "Failed to load sound: $category", e)
        }
    }
    
    fun playSound(category: SoundCategory, priority: Int = 0) {
        if (!soundSettings.masterEnabled) return
        
        // Rate limiting
        val currentTime = System.currentTimeMillis()
        val lastTime = lastPlayTime[category] ?: 0L
        if (currentTime - lastTime < RATE_LIMIT_MS) return
        
        lastPlayTime[category] = currentTime
        
        val soundId = soundMap[category] ?: return
        val volume = calculateVolume(category)
        
        soundPool?.play(soundId, volume, volume, priority, 0, 1.0f)
        
        // Trigger haptics for certain sounds
        if (soundSettings.hapticsEnabled) {
            triggerHaptics(category)
        }
    }
    
    private fun calculateVolume(category: SoundCategory): Float {
        if (!soundSettings.masterEnabled) return 0f
        
        val baseVolume = when (category) {
            SoundCategory.ACHIEVEMENT_SUCCESS -> 1.0f // Success sounds can be louder
            SoundCategory.ERROR_INVALID -> 0.9f
            else -> soundSettings.effectsVolume
        }
        
        return baseVolume * EFFECTS_VOLUME_MULTIPLIER * MASTER_VOLUME
    }
    
    private fun triggerHaptics(category: SoundCategory) {
        // TODO: Implement haptic feedback for mobile
        when (category) {
            SoundCategory.ERROR_INVALID -> {
                // Short vibration for error
            }
            SoundCategory.ACHIEVEMENT_SUCCESS -> {
                // Success pattern vibration
            }
            SoundCategory.CONFIRM_PLACE -> {
                // Light tap for place
            }
            else -> {
                // No haptics for other sounds
            }
        }
    }
    
    fun playMusic(resourceId: Int, loop: Boolean = true) {
        if (!soundSettings.masterEnabled || soundSettings.musicVolume <= 0f) return
        
        try {
            musicPlayer?.release()
            musicPlayer = MediaPlayer.create(context, resourceId).apply {
                isLooping = loop
                setVolume(soundSettings.musicVolume * MUSIC_VOLUME_MULTIPLIER, 
                         soundSettings.musicVolume * MUSIC_VOLUME_MULTIPLIER)
                start()
            }
        } catch (e: Exception) {
            Log.e("AudioManager", "Failed to play music", e)
        }
    }
    
    fun stopMusic() {
        musicPlayer?.stop()
        musicPlayer?.release()
        musicPlayer = null
    }
    
    fun pauseMusic() {
        musicPlayer?.pause()
    }
    
    fun resumeMusic() {
        if (soundSettings.masterEnabled && soundSettings.musicVolume > 0f) {
            musicPlayer?.start()
        }
    }
    
    fun duckMusic(durationMs: Long = 500L) {
        musicPlayer?.setVolume(0.1f, 0.1f) // Duck to 10%
        
        // Restore volume after duration
        CoroutineScope(Dispatchers.Main).launch {
            delay(durationMs)
            val volume = soundSettings.musicVolume * MUSIC_VOLUME_MULTIPLIER
            musicPlayer?.setVolume(volume, volume)
        }
    }
    
    fun updateMasterVolume(enabled: Boolean) {
        soundSettings.masterEnabled = enabled
        if (!enabled) {
            stopMusic()
        }
        saveSettings()
    }
    
    fun updateEffectsVolume(volume: Float) {
        soundSettings.effectsVolume = volume.coerceIn(0f, 1f)
        saveSettings()
    }
    
    fun updateMusicVolume(volume: Float) {
        soundSettings.musicVolume = volume.coerceIn(0f, 1f)
        if (musicPlayer != null) {
            val actualVolume = soundSettings.musicVolume * MUSIC_VOLUME_MULTIPLIER
            musicPlayer?.setVolume(actualVolume, actualVolume)
        }
        saveSettings()
    }
    
    fun updateHapticsEnabled(enabled: Boolean) {
        soundSettings.hapticsEnabled = enabled
        saveSettings()
    }
    
    fun getSettings(): SoundSettings = soundSettings.copy()
    
    fun cleanup() {
        soundPool?.release()
        musicPlayer?.release()
        soundPool = null
        musicPlayer = null
    }
}


