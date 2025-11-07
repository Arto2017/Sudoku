package com.example.gamesudoku

import android.content.Context
import android.media.SoundPool
import android.os.Build

class SoundManager private constructor(context: Context) {
    
    private val soundPool: SoundPool
    private var clickSoundId: Int = 0
    private var errorSoundId: Int = 0
    private val audioManager: AudioManager = AudioManager.getInstance(context)
    
    init {
        soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SoundPool.Builder()
                .setMaxStreams(5)
                .build()
        } else {
            @Suppress("DEPRECATION")
            SoundPool(5, android.media.AudioManager.STREAM_MUSIC, 0)
        }
        
        // Load click sound
        clickSoundId = soundPool.load(context, R.raw.click, 1)
        // Load error sound
        errorSoundId = soundPool.load(context, R.raw.error, 1)
    }
    
    fun playClick() {
        val volume = audioManager.getEffectiveEffectsVolume()
        if (clickSoundId > 0 && volume > 0f) {
            soundPool.play(clickSoundId, volume, volume, 1, 0, 1.0f)
        }
    }
    
    fun playError() {
        val volume = audioManager.getEffectiveEffectsVolume()
        if (errorSoundId > 0 && volume > 0f) {
            soundPool.play(errorSoundId, volume, volume, 1, 0, 1.0f)
        }
    }
    
    fun release() {
        soundPool.release()
    }
    
    companion object {
        @Volatile
        private var INSTANCE: SoundManager? = null
        
        fun getInstance(context: Context): SoundManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SoundManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

