package app.kinkeep.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kinkeep.BuildConfig
import app.kinkeep.R
import app.kinkeep.data.ContactDao
import app.kinkeep.data.ContactEntity
import app.kinkeep.data.ContactRepository
import app.kinkeep.data.HealthAssembler
import app.kinkeep.data.InteractionDao
import app.kinkeep.data.InteractionEntity
import app.kinkeep.data.SettingsStore
import app.kinkeep.data.SyncRepository
import app.kinkeep.domain.ContactHealth
import app.kinkeep.domain.DashboardBuilder
import app.kinkeep.domain.DashboardData
import app.kinkeep.domain.ScoringEngine
import app.kinkeep.domain.Streaks
import app.kinkeep.work.ReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiModel(val contacts: List<ContactHealth>, val dashboard: DashboardData)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    contactDao: ContactDao,
    interactionDao: InteractionDao,
    settingsStore: SettingsStore,
    private val repo: ContactRepository,
    private val sync: SyncRepository,
) : ViewModel() {

    /** Re-scores every 15 minutes so "days since" stays fresh while the app is open. */
    private val ticker = flow { while (true) { emit(Unit); delay(15 * 60_000L) } }

    private data class Inputs(val tracked: List<ContactEntity>, val interactions: List<InteractionEntity>, val goal: Int)

    /**
     * 365 days of history covers scoring (recency is zero beyond 270 days) and a 52-week streak window.
     * conflate() lets the heavy scoring step skip intermediate database updates (for example during an import)
     * and only ever work on the latest data.
     */
    val ui: StateFlow<UiModel> = combine(
        contactDao.observeTracked(),
        interactionDao.observeSince(System.currentTimeMillis() - 365 * ScoringEngine.DAY_MS),
        ticker,
        settingsStore.state,
    ) { tracked, interactions, _, prefs -> Inputs(tracked, interactions, prefs.weeklyGoal) }
        .conflate()
        .map { buildUi(it.tracked, it.interactions, it.goal) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            UiModel(emptyList(), DashboardBuilder.build(emptyList(), List(DashboardBuilder.WEEKS) { 0 })),
        )

    val untracked: StateFlow<List<ContactEntity>> =
        repo.observeUntracked().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildUi(tracked: List<ContactEntity>, interactions: List<InteractionEntity>, goal: Int): UiModel {
        val now = System.currentTimeMillis()
        val assembled = HealthAssembler.assemble(tracked, interactions, now)
        val trackedIds = tracked.map { it.id }.toSet()
        val trackedHistory = assembled.history.filterKeys { it in trackedIds }
        val weekly = DashboardBuilder.weeklyCounts(trackedHistory.values.flatten(), now)
        val perWeek = Streaks.distinctPeoplePerWeek(trackedHistory, now)
        val dashboard = DashboardBuilder.build(
            assembled.contacts, weekly,
            peopleThisWeek = perWeek.firstOrNull() ?: 0,
            streakWeeks = Streaks.weeklyStreak(perWeek, goal),
            weeklyGoal = goal,
        )
        return UiModel(assembled.contacts, dashboard)
    }

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    suspend fun primaryNumber(id: Long): String? = repo.primaryNumber(id)

    fun syncNow() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _message.value = runCatching { sync.sync() }.fold(
                onSuccess = {
                    if (it.missingPermissions && it.newContacts == 0 && it.processedCalls == 0) ctx.getString(R.string.sync_no_permission)
                    else if (BuildConfig.CALL_LOG_ENABLED) ctx.getString(R.string.sync_result, it.newContacts, it.processedCalls)
                    else ctx.getString(R.string.sync_result_contacts, it.newContacts)
                },
                onFailure = { ctx.getString(R.string.sync_failed, it.message ?: "unknown error") },
            )
            ReminderWorker.runNow(ctx)
            _syncing.value = false
        }
    }
}
