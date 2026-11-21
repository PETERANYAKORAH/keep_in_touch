package app.kinkeep.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kinkeep.R
import app.kinkeep.data.InteractionEntity
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.InteractionType
import app.kinkeep.domain.ReminderTiming
import app.kinkeep.domain.ScoringEngine
import app.kinkeep.ui.theme.color
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date

@Composable
fun DetailScreen(
    ui: DetailUi,
    onBack: () -> Unit,
    onCircle: (Circle) -> Unit,
    onCadence: (Int?) -> Unit,
    onMuted: (Boolean) -> Unit,
    onSaveNotes: (String) -> Unit,
    onLog: (Direction, Int, String) -> Unit,
    onSnooze: (Long) -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
) {
    val c = ui.contact
    var showLog by remember { mutableStateOf(false) }
    var notes by remember(c.id) { mutableStateOf(c.notes) }
    val effective = c.cadenceOverrideDays ?: c.circle.defaultCadenceDays
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.detail_back)) } }

            item {
                Column {
                    Text(c.displayName, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    ui.health?.let { h ->
                        Text("${h.category.label} · ${stringResource(R.string.score_label, h.score)}", style = MaterialTheme.typography.titleMedium, color = h.category.color())
                        val days = h.daysSinceLastContact
                        Text(
                            if (days == null) stringResource(R.string.no_contact_yet) else pluralStringResource(R.plurals.last_spoke_days, days, days),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCall, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_call)) }
                        OutlinedButton(onClick = onMessage, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_message)) }
                    }
                    OutlinedButton(onClick = { showLog = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.detail_spoke)) }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.detail_circle), style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Circle.entries.forEach { circle ->
                            FilterChip(selected = c.circle == circle, onClick = { onCircle(circle) }, label = { Text(circle.label) })
                        }
                    }
                    if (effective > 0) {
                        val step = if (effective >= 14) 7 else 1
                        val fewerDays = stringResource(R.string.cd_cadence_fewer)
                        val moreDays = stringResource(R.string.cd_cadence_more)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pluralStringResource(R.plurals.cadence_days, effective, effective), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { onCadence((effective - step).coerceAtLeast(1)) }, modifier = Modifier.semantics { contentDescription = fewerDays }) { Text("−") }
                            TextButton(onClick = { onCadence((effective + step).coerceAtMost(365)) }, modifier = Modifier.semantics { contentDescription = moreDays }) { Text("+") }
                        }
                        if (c.cadenceOverrideDays != null) {
                            TextButton(onClick = { onCadence(null) }) { Text(stringResource(R.string.detail_use_default)) }
                        }
                    } else {
                        Text(stringResource(R.string.detail_pick_circle), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.detail_pause), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = c.muted, onCheckedChange = onMuted)
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.detail_notes)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 3,
                    )
                    TextButton(onClick = { onSaveNotes(notes) }, enabled = notes != c.notes) { Text(stringResource(R.string.detail_save)) }
                }
            }

            item {
                Column {
                    val nowMs = System.currentTimeMillis()
                    if (ui.snoozedUntil > nowMs) {
                        Text(stringResource(R.string.snoozed_until, dateFormat.format(Date(ui.snoozedUntil))), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onSnooze(0L) }) { Text(stringResource(R.string.snooze_clear)) }
                    }
                    Row {
                        TextButton(onClick = { onSnooze(ReminderTiming.startOfTomorrow(nowMs, ZoneId.systemDefault())) }) { Text(stringResource(R.string.snooze_tomorrow)) }
                        TextButton(onClick = { onSnooze(nowMs + 7 * ScoringEngine.DAY_MS) }) { Text(stringResource(R.string.snooze_week)) }
                    }
                }
            }

            item { Text(stringResource(R.string.detail_history), style = MaterialTheme.typography.titleMedium) }
            if (ui.history.isEmpty()) {
                item { Text(stringResource(R.string.detail_no_history), style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(ui.history.take(30), key = { it.id }) { entry ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(historyLabel(entry), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            dateFormat.format(Date(entry.timestamp)) + if (entry.durationSec > 0) " · ${entry.durationSec / 60} min ${entry.durationSec % 60} s" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    if (showLog) LogDialog(onDismiss = { showLog = false }, onSave = { dir, min, note -> showLog = false; onLog(dir, min, note) })
}

@Composable
private fun historyLabel(e: InteractionEntity): String = stringResource(
    when {
        e.type == InteractionType.MANUAL && e.direction == Direction.INCOMING -> R.string.hist_manual_in
        e.type == InteractionType.MANUAL -> R.string.hist_manual_out
        e.direction == Direction.INCOMING -> R.string.hist_incoming
        e.direction == Direction.OUTGOING -> R.string.hist_outgoing
        else -> R.string.hist_missed
    },
)

@Composable
private fun LogDialog(onDismiss: () -> Unit, onSave: (Direction, Int, String) -> Unit) {
    var iReached by remember { mutableStateOf(true) }
    var minutes by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = iReached, onClick = { iReached = true }, label = { Text(stringResource(R.string.log_i_reached)) })
                    FilterChip(selected = !iReached, onClick = { iReached = false }, label = { Text(stringResource(R.string.log_they_reached)) })
                }
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(3) }, singleLine = true,
                    label = { Text(stringResource(R.string.log_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.log_note)) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (iReached) Direction.OUTGOING else Direction.INCOMING, minutes.toIntOrNull() ?: 0, note) }) {
                Text(stringResource(R.string.detail_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
