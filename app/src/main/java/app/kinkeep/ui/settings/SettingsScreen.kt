package app.kinkeep.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.kinkeep.R
import app.kinkeep.data.AppPrefs
import app.kinkeep.domain.Oem
import app.kinkeep.domain.OemGuide
import app.kinkeep.domain.ReminderHealth
import app.kinkeep.domain.ScoringEngine
import app.kinkeep.notify.NotificationHelper
import app.kinkeep.security.BiometricAuth
import app.kinkeep.ui.common.OemSettings
import app.kinkeep.ui.common.SystemSettings
import app.kinkeep.ui.common.findFragmentActivity
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

private const val MIN_PASSWORD = 8
private const val HOUR_MS = 3_600_000L

@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    message: String?,
    onChange: ((AppPrefs) -> AppPrefs) -> Unit,
    onTest: () -> Unit,
    onExport: (Uri, CharArray) -> Unit,
    onRestore: (Uri, CharArray) -> Unit,
    onWipe: () -> Unit,
    errorLogText: String,
    onClearErrorLog: () -> Unit,
) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val canNotify = remember(refresh, prefs) { NotificationHelper.canNotify(ctx) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val now = System.currentTimeMillis()
    val oem = remember { OemGuide.detect(Build.MANUFACTURER, Build.BRAND) }
    val brandName = remember { Build.MANUFACTURER.replaceFirstChar { it.uppercase() } }

    val activity = remember(ctx) { ctx.findFragmentActivity() }
    val lockAvailable = remember { BiometricAuth.isAvailable(ctx) }
    val lockTitle = stringResource(R.string.lock_prompt_title)
    val lockSubtitle = stringResource(R.string.lock_prompt_subtitle)

    var showExportDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingPassword
        pendingPassword = null
        if (uri != null && password != null) onExport(uri, password) else password?.fill('\u0000')
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingRestoreUri = uri; showRestoreDialog = true }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }

        // ----- Reminders -----
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_reminders), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = prefs.enabled, onCheckedChange = { v -> onChange { it.copy(enabled = v) } })
            }
        }
        if (!canNotify) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_notifications_off), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                    Button(onClick = {
                        val needsRuntimePermission = Build.VERSION.SDK_INT >= 33 &&
                            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsRuntimePermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else SystemSettings.openNotificationSettings(ctx)
                    }) { Text(stringResource(R.string.settings_allow_notifications)) }
                }
            }
        }
        if (ReminderHealth.isStale(prefs.enabled, prefs.pausedUntil, prefs.lastRunAt, now)) {
            item {
                Text(
                    stringResource(R.string.settings_stale, ((now - prefs.lastRunAt) / HOUR_MS).toInt()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        item { Stepper(stringResource(R.string.settings_default_time), hourText(prefs.defaultHour), { onChange { it.copy(defaultHour = wrap(it.defaultHour - 1)) } }, { onChange { it.copy(defaultHour = wrap(it.defaultHour + 1)) } }) }
        item { Stepper(stringResource(R.string.settings_quiet_start), hourText(prefs.quietStartHour), { onChange { it.copy(quietStartHour = wrap(it.quietStartHour - 1)) } }, { onChange { it.copy(quietStartHour = wrap(it.quietStartHour + 1)) } }) }
        item { Stepper(stringResource(R.string.settings_quiet_end), hourText(prefs.quietEndHour), { onChange { it.copy(quietEndHour = wrap(it.quietEndHour - 1)) } }, { onChange { it.copy(quietEndHour = wrap(it.quietEndHour + 1)) } }) }
        item { Text(stringResource(R.string.settings_quiet_hint), style = MaterialTheme.typography.bodySmall) }
        item {
            Stepper(
                stringResource(R.string.settings_daily_cap), prefs.dailyCap.toString(),
                { onChange { it.copy(dailyCap = (it.dailyCap - 1).coerceAtLeast(1)) } },
                { onChange { it.copy(dailyCap = (it.dailyCap + 1).coerceAtMost(10)) } },
            )
        }
        item {
            if (prefs.pausedUntil > now) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_paused_until, dateFormat.format(Date(prefs.pausedUntil))), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onChange { it.copy(pausedUntil = 0L) } }) { Text(stringResource(R.string.settings_resume)) }
                }
            } else {
                OutlinedButton(
                    onClick = { onChange { it.copy(pausedUntil = System.currentTimeMillis() + 7 * ScoringEngine.DAY_MS) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_pause_week)) }
            }
        }
        item {
            Text(
                if (prefs.lastRunAt == 0L) stringResource(R.string.settings_never_checked)
                else stringResource(R.string.settings_last_check, dateFormat.format(Date(prefs.lastRunAt))),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { OutlinedButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_test)) } }

        // ----- Weekly goal -----
        item { HorizontalDivider() }
        item {
            Stepper(
                stringResource(R.string.settings_goal), prefs.weeklyGoal.toString(),
                { onChange { it.copy(weeklyGoal = (it.weeklyGoal - 1).coerceAtLeast(1)) } },
                { onChange { it.copy(weeklyGoal = (it.weeklyGoal + 1).coerceAtMost(10)) } },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_summary), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = prefs.weeklySummaryEnabled, onCheckedChange = { v -> onChange { it.copy(weeklySummaryEnabled = v) } })
            }
        }

        // ----- Privacy -----
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_lock), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = prefs.appLockEnabled,
                        enabled = lockAvailable || prefs.appLockEnabled,
                        onCheckedChange = { want ->
                            activity?.let { a ->
                                BiometricAuth.prompt(a, lockTitle, lockSubtitle, onSuccess = { onChange { it.copy(appLockEnabled = want) } }, onFailure = {})
                            }
                        },
                    )
                }
                Text(
                    stringResource(if (lockAvailable || prefs.appLockEnabled) R.string.settings_lock_hint else R.string.settings_lock_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ----- Battery -----
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_battery_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_battery_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { SystemSettings.openBattery(ctx) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_battery_button)) }
                    OutlinedButton(onClick = { SystemSettings.openApp(ctx) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_app_button)) }
                }
                if (oem != Oem.OTHER) {
                    Text(stringResource(R.string.oem_title, brandName), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(oemSteps(oem)), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { OemSettings.open(ctx, oem) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.oem_button)) }
                } else {
                    Text(stringResource(R.string.oem_other), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ----- Backup -----
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_backup_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_backup_hint), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (prefs.lastBackupAt == 0L) stringResource(R.string.settings_never_backed_up)
                    else stringResource(R.string.settings_last_backup, dateFormat.format(Date(prefs.lastBackupAt))),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showExportDialog = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_backup_create)) }
                    OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_backup_restore)) }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }

        // ----- Diagnostics -----
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_diag_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_diag_body), style = MaterialTheme.typography.bodyMedium)
                if (errorLogText.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { shareLog(ctx, errorLogText) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_diag_share)) }
                        OutlinedButton(onClick = onClearErrorLog, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_diag_clear)) }
                    }
                } else {
                    Text(stringResource(R.string.settings_diag_empty), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ----- Danger zone -----
        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_danger), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                OutlinedButton(onClick = { showWipeDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_wipe)) }
            }
        }
        item { Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
    }

    if (showExportDialog) {
        PasswordDialog(
            title = stringResource(R.string.backup_export_title), body = stringResource(R.string.backup_export_body), confirm = true,
            onDismiss = { showExportDialog = false },
            onConfirm = { password ->
                showExportDialog = false
                pendingPassword = password
                createLauncher.launch("kinkeep-backup-${LocalDate.now()}.kkb")
            },
        )
    }
    if (showRestoreDialog) {
        PasswordDialog(
            title = stringResource(R.string.backup_restore_title), body = stringResource(R.string.backup_restore_body), confirm = false,
            onDismiss = { showRestoreDialog = false; pendingRestoreUri = null },
            onConfirm = { password ->
                showRestoreDialog = false
                pendingRestoreUri?.let { onRestore(it, password) } ?: password.fill('\u0000')
                pendingRestoreUri = null
            },
        )
    }
    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text(stringResource(R.string.wipe_title)) },
            text = { Text(stringResource(R.string.wipe_body)) },
            confirmButton = { TextButton(onClick = { showWipeDialog = false; onWipe() }) { Text(stringResource(R.string.wipe_confirm)) } },
            dismissButton = { TextButton(onClick = { showWipeDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun oemSteps(oem: Oem): Int = when (oem) {
    Oem.SAMSUNG -> R.string.oem_samsung
    Oem.XIAOMI -> R.string.oem_xiaomi
    Oem.OPPO -> R.string.oem_oppo
    Oem.VIVO -> R.string.oem_vivo
    Oem.HUAWEI -> R.string.oem_huawei
    Oem.TRANSSION -> R.string.oem_transsion
    Oem.OTHER -> R.string.oem_other
}

/** The person chooses where the log goes. Nothing is sent automatically. */
private fun shareLog(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.settings_diag_title))
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try { ctx.startActivity(Intent.createChooser(send, null)) } catch (_: ActivityNotFoundException) { /* no app to share with */ }
}

private fun wrap(hour: Int) = ((hour % 24) + 24) % 24
private fun hourText(hour: Int) = "%02d:00".format(hour)

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        val less = stringResource(R.string.cd_decrease, label)
        val more = stringResource(R.string.cd_increase, label)
        TextButton(onClick = onMinus, modifier = Modifier.semantics { contentDescription = less }) { Text("−") }
        Text(value, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onPlus, modifier = Modifier.semantics { contentDescription = more }) { Text("+") }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    confirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val valid = password.length >= MIN_PASSWORD && (!confirm || password == again)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    label = { Text(stringResource(R.string.backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (confirm) {
                    OutlinedTextField(
                        value = again, onValueChange = { again = it }, singleLine = true,
                        label = { Text(stringResource(R.string.backup_password_confirm)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (again.isNotEmpty() && again != password) {
                        Text(stringResource(R.string.backup_mismatch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Text(stringResource(R.string.backup_password_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password.toCharArray()) }, enabled = valid) { Text(stringResource(R.string.continue_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
