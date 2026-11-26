package com.jepongdevxyz.devxyzfiles

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * First-launch Setup Wizard + Settings screen. SAF-only, four steps, all
 * phone-only: no PC, no cable, no Developer Options.
 * Every step shows a LIVE status — never a static claim.
 *
 * Step 4 runs a real probe and includes Mobile Legends as the explicit
 * test case, plus the honest /data/data limit row.
 *
 * v1.2.0: restyled to the black Honor theme only (dialog surface #201F22,
 * blue step checks, muted body text). SAME SAF steps, SAME grant logic,
 * SAME Files-app deep link, SAME MLBB test rows — no behavior change.
 */
@Composable
fun SetupWizardDialog(
    repository: FileRepository,
    restricted: RestrictedStatus?,
    mlbb: MlbbStatus?,
    onRunTest: () -> Unit,
    onOpenPath: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var testRunning by remember { mutableStateOf(false) }
    var safMessage by remember { mutableStateOf<String?>(null) }

    val allFiles = Environment.isExternalStorageManager()

    fun launchSafGrant(path: String) {
        val launcher = SafPickerLauncher.current
        if (launcher != null) {
            launcher(path)
        } else {
            safMessage = "Buksan muna ang file picker mula sa main screen."
        }
    }

    fun openFilesAppSettings() {
        try {
            context.startActivity(SafHelper.filesAppSettingsIntent(context))
        } catch (e: Exception) {
            safMessage = "Hindi mabuksan ang Files app settings."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HonorColors.Card,
        titleContentColor = HonorColors.OnSurface,
        textContentColor = HonorColors.Muted,
        title = { Text("Setup — DevxyzFiles") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Isang beses lang ito. Walang PC, walang cable, walang " +
                        "Developer Options — isang phone lang ang kailangan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HonorColors.Muted
                )

                // ---- Step 1: All Files Access ----
                WizardStep(
                    number = 1,
                    title = "All Files Access",
                    done = allFiles,
                    body = "Kailangan para makita ang buong Internal Storage. " +
                        "Hindi pa rin nito bubuksan ang Android/data ng ibang apps — " +
                        "doon papasok ang SAF grants sa Step 2 at 3.",
                    actionLabel = if (allFiles) "Naka-on na" else "Buksan ang Settings",
                    onAction = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            )
                        } catch (e: Exception) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        }
                    }
                )

                // ---- Step 2: Grant Android/data ----
                WizardStep(
                    number = 2,
                    title = "I-grant ang Android/data",
                    done = restricted?.data?.accessible == true,
                    body = "Pipiliin mo mismo sa system picker kung aling folder " +
                        "ang pwedeng buksan ng DevxyzFiles. Isang grant lang, " +
                        "tapos naka-save na siya — hindi na uulitin.",
                    actionLabel = "I-grant ang Android/data",
                    onAction = { launchSafGrant(FileRepository.RESTRICTED_DATA) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { launchSafGrant(FileRepository.MLBB_DATA) }) {
                        Text("Grant MLBB data folder only")
                    }
                }
                Text(
                    "Paalala: sa ilang phone (tulad ng Honor), hindi sinusunod " +
                        "ng system picker yung direktang pagbukas sa MLBB folder " +
                        "at walang Uninstall updates sa Files app — pag ganoon, " +
                        "yung phone mismo yung humaharang, hindi yung app. " +
                        "Subukan mo pa rin yung MLBB button sa itaas; pag root " +
                        "pa rin yung bumukas, blocked talaga siya ng phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HonorColors.Muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openFilesAppSettings() }) {
                        Text("Files app settings")
                    }
                }

                // ---- Step 3: Grant Android/obb ----
                WizardStep(
                    number = 3,
                    title = "I-grant ang Android/obb",
                    done = restricted?.obb?.accessible == true,
                    body = "Ganoon din sa obb — dito nakatira ang malalaking " +
                        "MLBB OBB files. Hiwalay na grant ito sa Step 2.",
                    actionLabel = "I-grant ang Android/obb",
                    onAction = { launchSafGrant(FileRepository.RESTRICTED_OBB) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { launchSafGrant(FileRepository.MLBB_OBB) }) {
                        Text("Grant MLBB obb folder only")
                    }
                }
                if (safMessage != null) {
                    Text(safMessage!!, color = Color(0xFFEF4444))
                }

                // ---- Step 4: Live test ----
                WizardStep(
                    number = 4,
                    title = "Test: mabuksan ba talaga?",
                    done = (restricted?.data?.accessible == true) &&
                        (restricted?.obb?.accessible == true),
                    body = "Pindutin ang Run test. Totoong listahan ito, hindi hula.",
                    actionLabel = if (testRunning) "Tine-test…" else "Run test",
                    onAction = {
                        testRunning = true
                        onRunTest()
                        testRunning = false
                    }
                )
                if (restricted != null) {
                    TestRow("Android/data", restricted.data)
                    TestRow("Android/obb", restricted.obb)
                }
                if (mlbb != null) {
                    Text(
                        "Mobile Legends — first-class test",
                        style = MaterialTheme.typography.titleSmall,
                        color = HonorColors.OnSurface
                    )
                    TestRow("MLBB data", mlbb.data)
                    TestRow("MLBB obb", mlbb.obb)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = HonorColors.Muted,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "  /data/data/com.mobile.legends — HINDI kaya kahit via " +
                                "SAF. Kailangan ng root doon. Android/data at " +
                                "Android/obb lang ang kaya.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HonorColors.Muted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onOpenPath(FileRepository.MLBB_DATA) }) {
                            Text("Buksan MLBB data")
                        }
                        OutlinedButton(onClick = { onOpenPath(FileRepository.MLBB_OBB) }) {
                            Text("Buksan MLBB obb")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tapos na", color = HonorColors.AccentBlue) }
        }
    )
}

/** Holder for the SAF picker launcher registered in MainActivity. */
object SafPickerLauncher {
    var current: ((String) -> Unit)? = null
}

@Composable
private fun WizardStep(
    number: Int,
    title: String,
    done: Boolean,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (done) HonorColors.AccentBlue else HonorColors.Muted,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "  Step $number — $title",
                style = MaterialTheme.typography.titleSmall,
                color = HonorColors.OnSurface
            )
        }
        Text(body, style = MaterialTheme.typography.bodySmall, color = HonorColors.Muted)
        OutlinedButton(onClick = onAction) { Text(actionLabel, color = HonorColors.AccentBlue) }
    }
}

@Composable
private fun TestRow(label: String, probe: ProbeResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (probe.accessible) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (probe.accessible) HonorColors.AccentBlue else Color(0xFFEF4444),
            modifier = Modifier.size(18.dp)
        )
        Text(
            "  $label: " + if (probe.accessible) {
                "OK — ${probe.count} items (${probe.via})"
            } else {
                "Blocked (${probe.via})" + (probe.error?.let { " — $it" } ?: "")
            },
            style = MaterialTheme.typography.bodySmall,
            color = HonorColors.OnSurface
        )
    }
}
