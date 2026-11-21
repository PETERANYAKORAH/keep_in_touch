package app.kinkeep.ui.people

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kinkeep.R
import app.kinkeep.ui.common.ImportPermissions
import app.kinkeep.data.ContactEntity
import app.kinkeep.domain.ContactHealth
import app.kinkeep.ui.common.ContactRow

@Composable
fun PeopleScreen(
    tracked: List<ContactHealth>,
    untracked: List<ContactEntity>,
    syncing: Boolean,
    message: String?,
    onSync: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 tracked, 1 not tracked
    var query by rememberSaveable { mutableStateOf("") }

    val permissions = remember { ImportPermissions.list() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onSync() }

    fun onImportClick() {
        val missing = permissions.any { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing) showRationale = true else onSync()
    }

    val trackedShown = remember(tracked, query) { tracked.filter { it.name.contains(query, ignoreCase = true) } }
    val untrackedShown = remember(untracked, query) { untracked.filter { it.displayName.contains(query, ignoreCase = true) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Button(onClick = ::onImportClick, enabled = !syncing, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (syncing) R.string.importing else R.string.import_button))
        }
        message?.let { Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }

        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text(stringResource(R.string.people_tracked, tracked.size)) })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text(stringResource(R.string.people_untracked, untracked.size)) })
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (mode == 1) Text(stringResource(R.string.untracked_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(4.dp))

        if ((mode == 0 && trackedShown.isEmpty()) || (mode == 1 && untrackedShown.isEmpty())) {
            Text(stringResource(R.string.people_empty), modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
        } else if (mode == 0) {
            LazyColumn {
                items(trackedShown, key = { it.id }) { item ->
                    ContactRow(item, Modifier.clickable { onOpen(item.id) })
                    HorizontalDivider()
                }
            }
        } else {
            LazyColumn {
                items(untrackedShown, key = { it.id }) { c ->
                    Text(c.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(vertical = 14.dp))
                    HorizontalDivider()
                }
            }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.rationale_title)) },
            text = { Text(stringResource(R.string.rationale_body)) },
            confirmButton = {
                TextButton(onClick = { showRationale = false; launcher.launch(permissions) }) { Text(stringResource(R.string.continue_action)) }
            },
            dismissButton = { TextButton(onClick = { showRationale = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
