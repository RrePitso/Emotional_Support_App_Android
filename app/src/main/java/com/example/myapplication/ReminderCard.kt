package com.example.myapplication

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun ReminderCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ReminderScheduler.isEnabled(context)) }
    var hour by remember { mutableStateOf(ReminderScheduler.hour(context)) }
    var minute by remember { mutableStateOf(ReminderScheduler.minute(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ReminderScheduler.enable(context, hour, minute)
            enabled = true
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Daily journal reminder", style = MaterialTheme.typography.titleSmall)
                TextButton(
                    contentPadding = PaddingValues(0.dp),
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            hour = h
                            minute = m
                            if (enabled) ReminderScheduler.enable(context, h, m)
                            else ReminderScheduler.saveTime(context, h, m)
                        }, hour, minute, true).show()
                    }
                ) {
                    Text("Time: %02d:%02d (tap to change)".format(hour, minute))
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on) {
                        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            ReminderScheduler.enable(context, hour, minute)
                            enabled = true
                        }
                    } else {
                        ReminderScheduler.disable(context)
                        enabled = false
                    }
                }
            )
        }
    }
}