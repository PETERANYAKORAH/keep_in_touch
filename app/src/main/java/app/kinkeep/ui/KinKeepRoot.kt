package app.kinkeep.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.kinkeep.R
import app.kinkeep.ui.common.ContactActions
import app.kinkeep.ui.dashboard.DashboardScreen
import app.kinkeep.ui.detail.DetailScreen
import app.kinkeep.ui.detail.DetailViewModel
import app.kinkeep.ui.home.HomeViewModel
import app.kinkeep.ui.people.PeopleScreen
import app.kinkeep.ui.settings.SettingsScreen
import app.kinkeep.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun KinKeepRoot(
    home: HomeViewModel,
    detail: DetailViewModel,
    settings: SettingsViewModel,
    deepLink: MutableStateFlow<Long?>,
) {
    val ui by home.ui.collectAsState()
    val untracked by home.untracked.collectAsState()
    val syncing by home.syncing.collectAsState()
    val message by home.message.collectAsState()
    val prefs by settings.prefs.collectAsState()
    val settingsMessage by settings.message.collectAsState()
    val errorLogText by settings.errorLogText.collectAsState()
    val link by deepLink.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    BackHandler(enabled = detailId != null) { detailId = null }
    LaunchedEffect(detailId) { detail.select(detailId) }
    // A tapped notification or widget row opens that person.
    LaunchedEffect(link) { link?.let { detailId = it; deepLink.value = null } }

    fun placeCall(id: Long) { scope.launch { home.primaryNumber(id)?.let { ContactActions.dial(ctx, it) } } }
    fun sendMessage(id: Long) { scope.launch { home.primaryNumber(id)?.let { ContactActions.message(ctx, it) } } }

    val openId = detailId
    if (openId != null) {
        val d by detail.detail.collectAsState()
        d?.let { state ->
            DetailScreen(
                ui = state,
                onBack = { detailId = null },
                onCircle = { detail.setCircle(openId, it) },
                onCadence = { detail.setCadence(openId, it) },
                onMuted = { detail.setMuted(openId, it) },
                onSaveNotes = { detail.saveNotes(openId, it) },
                onLog = { dir, minutes, note -> detail.logManual(openId, dir, minutes, note) },
                onSnooze = { detail.snooze(openId, it) },
                onCall = { placeCall(openId) },
                onMessage = { sendMessage(openId) },
            )
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Filled.Favorite, contentDescription = null) }, label = { Text(stringResource(R.string.tab_dashboard)) })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Filled.Person, contentDescription = null) }, label = { Text(stringResource(R.string.tab_people)) })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, label = { Text(stringResource(R.string.tab_settings)) })
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> DashboardScreen(ui, onOpen = { detailId = it }, onCall = ::placeCall, onMessage = ::sendMessage)
                    1 -> PeopleScreen(
                        tracked = ui.contacts, untracked = untracked, syncing = syncing, message = message,
                        onSync = home::syncNow, onOpen = { detailId = it },
                    )
                    else -> SettingsScreen(
                        prefs = prefs, message = settingsMessage, onChange = settings::update, onTest = settings::sendTest,
                        onExport = settings::export, onRestore = settings::restore, onWipe = settings::wipeAll,
                        errorLogText = errorLogText, onClearErrorLog = settings::clearErrorLog,
                    )
                }
            }
        }
    }
}
