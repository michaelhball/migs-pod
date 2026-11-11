package com.migspod.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/**
 * Calls [onTick] with the player every [intervalMs] while the app is on screen.
 * Tied to the lifecycle because a plain LaunchedEffect loop keeps waking the main
 * thread for as long as audio plays in the background.
 */
@Composable
internal fun PollPlayer(player: Player?, intervalMs: Long, onTick: (Player) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnTick by rememberUpdatedState(onTick)
    LaunchedEffect(player, lifecycleOwner) {
        if (player == null) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                currentOnTick(player)
                delay(intervalMs)
            }
        }
    }
}
