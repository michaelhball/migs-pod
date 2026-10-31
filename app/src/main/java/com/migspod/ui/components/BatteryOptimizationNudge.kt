package com.migspod.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.content.getSystemService

private const val PREFS = "migspod_player"
private const val KEY_NUDGE_SHOWN = "battery_nudge_shown"

/**
 * One-time prompt asking the user to exempt MigsPod from battery optimization.
 *
 * Aggressive OEM power management (notably ColorOS/OPPO, MIUI, etc.) kills the
 * playback foreground service when the app is swiped from recents, stopping
 * background audio. There's no API to override that; the user has to allow it.
 * Shown once, and only when the app isn't already exempt.
 */
@Composable
fun BatteryOptimizationNudge() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NUDGE_SHOWN, false)) return@LaunchedEffect
        val exempt = context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
        if (!exempt) show = true
    }

    if (!show) return

    fun markShownAndHide() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NUDGE_SHOWN, true)
        }
        show = false
    }

    AlertDialog(
        onDismissRequest = { markShownAndHide() },
        title = { Text("Keep playback alive") },
        text = {
            Text(
                "Some phones stop audio when you swipe the app away from recents. " +
                    "To keep episodes playing in the background, allow MigsPod to run " +
                    "without battery restrictions — look for “Allow background " +
                    "activity” or set MigsPod to “Don’t optimize”."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                openBatterySettings(context)
                markShownAndHide()
            }) { Text("Open settings") }
        },
        dismissButton = {
            TextButton(onClick = { markShownAndHide() }) { Text("Not now") }
        },
    )
}

private fun openBatterySettings(context: Context) {
    // Prefer the battery-optimization list; fall back to this app's detail page
    // (where OEM "Allow background activity" toggles usually live).
    val candidates = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // Try the next fallback.
        }
    }
}
