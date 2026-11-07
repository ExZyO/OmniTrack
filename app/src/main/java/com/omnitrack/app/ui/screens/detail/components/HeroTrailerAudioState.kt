package com.omnitrack.app.ui.screens.detail.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global audio state holder for in-app hero trailers.
 * Defaults to muted for cinematic non-intrusive playback, with seamless toggle.
 */
object HeroTrailerAudioState {
    private val _muted = MutableStateFlow(true)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    fun toggleMuted() {
        _muted.value = !_muted.value
    }

    fun setMuted(isMuted: Boolean) {
        _muted.value = isMuted
    }
}
