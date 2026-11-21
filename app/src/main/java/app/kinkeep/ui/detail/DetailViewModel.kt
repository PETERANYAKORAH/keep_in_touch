package app.kinkeep.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kinkeep.data.ContactEntity
import app.kinkeep.data.ContactRepository
import app.kinkeep.data.InteractionEntity
import app.kinkeep.domain.Circle
import app.kinkeep.domain.Direction
import app.kinkeep.domain.HealthResult
import app.kinkeep.domain.Interaction
import app.kinkeep.domain.ScoringEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUi(
    val contact: ContactEntity,
    val health: HealthResult?,
    val history: List<InteractionEntity>,
    val snoozedUntil: Long,
)

@HiltViewModel
class DetailViewModel @Inject constructor(private val repo: ContactRepository) : ViewModel() {
    private val selected = MutableStateFlow<Long?>(null)
    fun select(id: Long?) { selected.value = id }

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<DetailUi?> = selected.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else combine(repo.observeContact(id), repo.observeInteractions(id), repo.observeReminderState(id)) { contact, history, state ->
            contact?.let { DetailUi(it, healthOf(it, history), history, state?.snoozedUntil ?: 0L) }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun healthOf(c: ContactEntity, history: List<InteractionEntity>): HealthResult? {
        val cadence = c.cadenceOverrideDays ?: c.circle.defaultCadenceDays
        if (cadence <= 0) return null // not tracked
        val domain = history.map { Interaction(it.timestamp, it.type, it.direction, it.durationSec) }
        return ScoringEngine.score(cadence, domain, System.currentTimeMillis())
    }

    fun setCircle(id: Long, circle: Circle) { viewModelScope.launch { repo.setCircle(id, circle) } }
    fun setCadence(id: Long, days: Int?) { viewModelScope.launch { repo.setCadenceOverride(id, days) } }
    fun snooze(id: Long, untilMs: Long) { viewModelScope.launch { repo.snooze(id, untilMs) } }
    fun setMuted(id: Long, muted: Boolean) { viewModelScope.launch { repo.setMuted(id, muted) } }
    fun saveNotes(id: Long, notes: String) { viewModelScope.launch { repo.saveNotes(id, notes) } }
    fun logManual(id: Long, direction: Direction, minutes: Int, note: String) {
        viewModelScope.launch { repo.logManual(id, direction, minutes, note) }
    }
}
