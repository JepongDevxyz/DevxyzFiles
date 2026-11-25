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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * First-launch Setup Wizard + Settings screen. Four steps, all phone-only:
 * no PC, no cable. Every step shows a LIVE status — never a static claim.
 *
 * Step 4 runs a real probe and includes Mobile Legends as the explicit
 * test case, plus the honest /data/data limit row.
 */
@Composable
fun SetupWizardDialog(
    repository: FileRepository,
    shizukuState: ShizukuState,
    restricted: RestrictedStatus?,
    mlbb: MlbbStatus?,
    onRunTest: () -> Unit,
    onOpenPath: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testRunning by remember { mutableStateOf(false) }
    var safMessage by remember { mutableStateOf<String?>(null) }

    val allFiles = Environment.isExternalStorageManager()
    val shizuku = shizukuState

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Setup — DevxyzFiles") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Isang beses lang ito. Walang PC, walang cable — " +
                        "isang phone lang ang kailangan.",
                    style = MaterialTheme.typography.bodyMedium
                )

                // ---- Step 1: All Files Access ----
                WizardStep(
                    number = 1,
                    title = "All Files Access",
                    done = allFiles,
                    body = "Kailangan para makita ang buong Internal Storage. " +
                        "Hindi pa rin nito bubuksan ang Android/data ng ibang apps — " +
                        "doon papasok ang Shizuku sa Step 3.",
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

                // ---- Step 2: Install Shizuku ----
                WizardStep(
                    number = 2,
                    title = "I-install ang Shizuku",
                    done = shizuku.installed,
                    body = "Libre at open-source. Si Shizuku ang magbibigay ng " +
                        "shell access sa DevxyzFiles para mabuksan ang " +
                        "Android/data at Android/obb.",
                    actionLabel = if (shizuku.installed) "Naka-install na" else "I-install sa Play Store",
                    onAction = {
                        val market = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=${ShizukuManager.SHIZUKU_PACKAGE}")
                        )
                        val web = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "https://play.google.com/store/apps/details?id=" +
                                    ShizukuManager.SHIZUKU_PACKAGE
                            )
                        )
                        try {
                            context.startActivity(market)
                        } catch (e: Exception) {
                            context.startActivity(web)
                        }
                    }
                )

                // ---- Step 3: Wireless Debugging pairing (phone-only) ----
                WizardStep(
                    number = 3,
                    title = "Shizuku via Wireless Debugging",
                    done = shizuku.binderAlive && shizuku.permissionGranted,
                    body = "PHONE-ONLY, WALANG PC:\n" +
                        "1. Settings > About phone > tap Build number 7x\n" +
                        "2. Developer options > i-ON ang Wireless debugging (kailangan naka-Wi-Fi)\n" +
                        "3. Sa Shizuku, tap Pairing\n" +
                        "4. Sa Settings, tap Pair device with pairing code — may 6-digit code\n" +
                        "5. Ilagay ang code sa Shizuku notification, tapos tap Start sa Shizuku\n\n" +
                        "Isang beses lang ang pairing. Pag nag-restart ang phone, " +
                        "buksan lang ulit ang Shizuku at tap Start.",
                    actionLabel = "Buksan ang Developer Options",
                    onAction = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            )
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ShizukuManager.refresh(context)
                                if (ShizukuManager.state.value.binderAlive) {
                                    ShizukuManager.bindService()
                                } else {
                                    // Try opening the Shizuku app so the user can tap Start.
                                    val launch = context.packageManager.getLaunchIntentForPackage(
                                        ShizukuManager.SHIZUKU_PACKAGE
                                    )
                                    if (launch != null) context.startActivity(launch)
                                }
                            }
                        }
                    ) {
                        Text("I-connect / I-start ang Shizuku")
                    }
                }
                if (shizuku.lastError != null) {
                    Text(shizuku.lastError, color = MaterialTheme.colorScheme.error)
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
                        style = MaterialTheme.typography.titleSmall
                    )
                    TestRow("MLBB data", mlbb.data)
                    TestRow("MLBB obb", mlbb.obb)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "  /data/data/com.mobile.legends — HINDI kaya kahit via " +
                                "Shizuku. Kailangan ng root doon. Android/data at " +
                                "Android/obb lang ang kaya.",
                            style = MaterialTheme.typography.bodySmall
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

                // ---- SAF fallback ----
                Text(
                    "Fallback kung ayaw mo ng Shizuku (SAF)",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Sa Android 16, tinatanggihan ng system picker ang " +
                        "Android/data at Android/obb kapag updated ang Files app. " +
                        "Workaround na walang PC: Settings > Apps > Files > " +
                        "Uninstall updates, saka ulitin ang grant dito. " +
                        "Ang grant na nakuha mo ay hindi mawawala kahit mag-update ulit ang Files.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val launcher = SafPickerLauncher.current
                        if (launcher != null) {
                            launcher(FileRepository.RESTRICTED_DATA)
                        } else {
                            safMessage = "Buksan muna ang file picker mula sa main screen."
                        }
                    }) {
                        Text("I-grant ang Android/data")
                    }
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(SafHelper.filesAppSettingsIntent(context))
                        } catch (e: Exception) {
                            safMessage = "Hindi mabuksan ang Files app settings."
                        }
                    }) {
                        Text("Files app settings")
                    }
                }
                if (safMessage != null) {
                    Text(safMessage!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tapos na") }
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
                modifier = Modifier.size(20.dp)
            )
            Text(
                "  Step $number — $title",
                style = MaterialTheme.typography.titleSmall
            )
        }
        Text(body, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun TestRow(label: String, probe: ProbeResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (probe.accessible) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Text(
            "  $label: " + if (probe.accessible) {
                "OK — ${probe.count} items (${probe.via})"
            } else {
                "Blocked (${probe.via})" + (probe.error?.let { " — $it" } ?: "")
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}
