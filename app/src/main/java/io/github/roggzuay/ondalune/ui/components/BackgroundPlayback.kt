package io.github.roggzuay.ondalune.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Battery optimisation exemption. Without it, One UI and other OEM battery savers freeze or kill
 * the app after the screen locks, which stops playback even though the media service is running.
 */
object BackgroundPlayback {
    private const val PREFS = "ondalune_ui"
    private const val KEY_ASKED = "battery_prompt_shown"

    fun isUnrestricted(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    /** True once per install, the first time it is worth asking. */
    fun shouldPrompt(context: Context): Boolean =
        !isUnrestricted(context) && !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    /** Opens the system "allow unrestricted battery use" dialog, or the settings list as a fallback. */
    @SuppressLint("BatteryLife") // a music player is an accepted use of this request
    fun requestExemption(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(direct) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** App info page, where Samsung keeps "Battery: Unrestricted" and "Sleeping apps" exclusions. */
    fun openAppBatterySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Asks once to lift battery optimisation so music keeps playing on the lock screen. */
@Composable
fun BackgroundPlaybackPrompt(onDismiss: () -> Unit) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        // Granted in the system dialog: close ours too.
        if (BackgroundPlayback.isUnrestricted(context)) onDismiss()
        onPauseOrDispose { }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.BatteryChargingFull, contentDescription = null) },
        title = { Text("Keep playing when locked") },
        text = {
            Text(
                "Your phone's battery saver can pause Ondalune once the screen turns off. " +
                    "Allow unrestricted battery use so songs keep playing and the next one starts on its own. " +
                    "Ondalune only stays awake while music is playing."
            )
        },
        confirmButton = {
            TextButton(
                onClick = { BackgroundPlayback.requestExemption(context) },
                modifier = Modifier.testTag("battery_prompt_allow")
            ) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("battery_prompt_later")) { Text("Not now") }
        }
    )
}

/** Settings row: shows whether background playback is unrestricted and how to fix it. */
@Composable
fun BackgroundPlaybackSettingsRow() {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(BackgroundPlayback.isUnrestricted(context)) }
    LifecycleResumeEffect(Unit) {
        unrestricted = BackgroundPlayback.isUnrestricted(context)
        onPauseOrDispose { }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_background_playback")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (unrestricted) Icons.Filled.CheckCircle else Icons.Filled.BatteryChargingFull,
                contentDescription = null,
                tint = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Lock-screen playback", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (unrestricted) "Battery use is unrestricted. Music keeps playing when the phone is locked."
                    else "Battery optimisation may stop music when the phone is locked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!unrestricted) FilledTonalButton(onClick = { BackgroundPlayback.requestExemption(context) }) {
                Text("Allow background playback")
            }
            TextButton(onClick = { BackgroundPlayback.openAppBatterySettings(context) }) { Text("App battery settings") }
        }
    }
}
