package com.chrisalvis.rotato.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.chrisalvis.rotato.data.DailyPick
import com.chrisalvis.rotato.worker.DailyPickWorker
import kotlinx.coroutines.launch

private fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12:00 AM"
    hour < 12 -> "$hour:00 AM"
    hour == 12 -> "12:00 PM"
    else -> "${hour - 12}:00 PM"
}

/** Settings › Discover & Sources › Daily pick: the notification toggle, its time and "Show one now". */
@Composable
internal fun DailyPickSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(DailyPick.isEnabled(context)) }
    var hour by remember { mutableIntStateOf(DailyPick.hour(context)) }

    fun turnOn() {
        DailyPick.setEnabled(context, true)
        enabled = true
        scope.launch {
            DailyPickWorker.sync(context, reschedule = true)
            // Show the first one straight away so there's something to see today.
            if (DailyPick.current(context) == null) DailyPickWorker.runNow(context)
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn()
        else Toast.makeText(context, "Allow notifications for Rotato to get the daily pick", Toast.LENGTH_LONG).show()
    }

    SettingsSection(title = "Daily pick") {
        SettingsToggleRow(
            title = "Daily pick notification",
            subtitle = "One fresh \"For you\" find from your sources each day, with Set and Save. Always SFW.",
            checked = enabled,
            onCheckedChange = { on ->
                if (on) {
                    val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else turnOn()
                } else {
                    DailyPick.setEnabled(context, false)
                    enabled = false
                    scope.launch { DailyPickWorker.sync(context) }
                }
            },
        )
        if (enabled) {
            SettingsDropdown(
                label = "Arrives around",
                items = (0..23).toList(),
                selectedLabel = hourLabel(hour),
                itemLabel = ::hourLabel,
                onSelect = { h ->
                    hour = h
                    DailyPick.setHour(context, h)
                    scope.launch { DailyPickWorker.sync(context, reschedule = true) }
                },
            )
            OutlinedButton(
                onClick = {
                    scope.launch { DailyPickWorker.runNow(context) }
                    Toast.makeText(context, "Finding a pick…", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Show one now") }
        }
        Text(
            "There's also a Daily pick home-screen widget. It works even with the notification off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
