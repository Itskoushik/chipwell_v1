package com.example.chipvision.engine

/**
 * Tracks the active inspection arrangement and retains only the newest
 * arrangement observed while post-processing or a result hold is active.
 */
class InspectionDispatchState {
    private var activeSceneHash: Int? = null
    private var pendingSceneHash: Int? = null

    @Synchronized
    fun begin(sceneHash: Int): Boolean {
        if (activeSceneHash != null) return false
        activeSceneHash = sceneHash
        pendingSceneHash = null
        return true
    }

    @Synchronized
    fun observe(sceneHash: Int) {
        if (activeSceneHash != null && sceneHash != activeSceneHash) {
            pendingSceneHash = sceneHash
        }
    }

    @Synchronized
    fun activeSceneHash(): Int? = activeSceneHash

    @Synchronized
    fun pendingSceneHash(): Int? = pendingSceneHash

    @Synchronized
    fun complete(): Int? {
        val pending = pendingSceneHash
        activeSceneHash = null
        pendingSceneHash = null
        return pending
    }

    @Synchronized
    fun clear() {
        activeSceneHash = null
        pendingSceneHash = null
    }
}
