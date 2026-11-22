package com.omnitrack.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fires [onClick] immediately on press, then continuously repeats while the finger stays held down
 * (accelerating after 10 ticks). Cancels cleanly on release or pointer cancellation.
 */
fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelayMs: Long = 320L,
    repeatDelayMs: Long = 105L,
    fastDelayMs: Long = 45L,
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()

    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentEnabled) return@awaitEachGesture
            down.consume()
            currentOnClick()
            val repeatJob = scope.launch {
                delay(initialDelayMs)
                var ticks = 0
                while (isActive && currentEnabled) {
                    currentOnClick()
                    ticks++
                    delay(if (ticks >= 10) fastDelayMs else repeatDelayMs)
                }
            }
            try {
                waitForUpOrCancellation()
            } finally {
                repeatJob.cancel()
            }
        }
    }
}
