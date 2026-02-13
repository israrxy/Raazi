package com.israrxy.raazi.service

import android.content.Context
import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sleep Timer functionality for automatic playback stop
 * Standard feature in 2026 music apps
 */
class SleepTimer private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: SleepTimer? = null
        
        fun getInstance(): SleepTimer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SleepTimer().also { INSTANCE = it }
            }
        }
        
        // Preset durations in minutes
        val PRESET_DURATIONS = listOf(5, 10, 15, 30, 45, 60, 90)
    }
    
    private var countDownTimer: CountDownTimer? = null
    
    private val _remainingTimeMs = MutableStateFlow<Long>(0)
    val remainingTimeMs: StateFlow<Long> = _remainingTimeMs.asStateFlow()
    
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private var onTimerFinished: (() -> Unit)? = null
    
    fun start(durationMinutes: Int, onFinished: () -> Unit) {
        cancel() // Cancel any existing timer
        
        val durationMs = durationMinutes * 60 * 1000L
        onTimerFinished = onFinished
        _isActive.value = true
        
        countDownTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _remainingTimeMs.value = millisUntilFinished
            }
            
            override fun onFinish() {
                _remainingTimeMs.value = 0
                _isActive.value = false
                onTimerFinished?.invoke()
                onTimerFinished = null
            }
        }.start()
    }
    
    fun cancel() {
        countDownTimer?.cancel()
        countDownTimer = null
        _remainingTimeMs.value = 0
        _isActive.value = false
        onTimerFinished = null
    }
    
    fun formatRemainingTime(): String {
        val totalSeconds = _remainingTimeMs.value / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
