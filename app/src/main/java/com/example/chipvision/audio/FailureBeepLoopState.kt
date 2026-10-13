package com.example.chipvision.audio

class FailureBeepLoopState {
    private var active = false

    @Synchronized
    fun start(): Boolean {
        if (active) return false
        active = true
        return true
    }

    @Synchronized
    fun stop() {
        active = false
    }

    @Synchronized
    fun isActive(): Boolean = active
}
