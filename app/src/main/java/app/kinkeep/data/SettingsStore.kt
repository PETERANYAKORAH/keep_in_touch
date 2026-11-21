package app.kinkeep.data

import android.content.Context
import app.kinkeep.domain.Streaks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AppPrefs(
    val enabled: Boolean = true,
    val defaultHour: Int = 18,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 8,
    val dailyCap: Int = 3,
    val pausedUntil: Long = 0L,
    val lastRunAt: Long = 0L,
    val appLockEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    val weeklySummaryEnabled: Boolean = true,
    val weeklyGoal: Int = Streaks.DEFAULT_GOAL,
    val lastBackupAt: Long = 0L,
)

/** Small on-device settings store (SharedPreferences). Readable from workers without a UI. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs = ctx.getSharedPreferences("kinkeep_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppPrefs> = _state
    val current: AppPrefs get() = _state.value

    @Synchronized
    fun update(change: (AppPrefs) -> AppPrefs) {
        val next = change(_state.value)
        prefs.edit()
            .putBoolean("enabled", next.enabled)
            .putInt("defaultHour", next.defaultHour)
            .putInt("quietStart", next.quietStartHour)
            .putInt("quietEnd", next.quietEndHour)
            .putInt("dailyCap", next.dailyCap)
            .putLong("pausedUntil", next.pausedUntil)
            .putLong("lastRunAt", next.lastRunAt)
            .putBoolean("appLockEnabled", next.appLockEnabled)
            .putBoolean("onboardingDone", next.onboardingDone)
            .putBoolean("weeklySummaryEnabled", next.weeklySummaryEnabled)
            .putInt("weeklyGoal", next.weeklyGoal)
            .putLong("lastBackupAt", next.lastBackupAt)
            .apply()
        _state.value = next
    }

    fun getString(key: String): String? = prefs.getString(key, null)
    fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }

    /** Clears every setting and stored reminder-wording history. Used by "Delete all data". */
    @Synchronized
    fun reset() {
        prefs.edit().clear().apply()
        _state.value = AppPrefs()
    }

    private fun load(): AppPrefs {
        val d = AppPrefs()
        return AppPrefs(
            enabled = prefs.getBoolean("enabled", d.enabled),
            defaultHour = prefs.getInt("defaultHour", d.defaultHour),
            quietStartHour = prefs.getInt("quietStart", d.quietStartHour),
            quietEndHour = prefs.getInt("quietEnd", d.quietEndHour),
            dailyCap = prefs.getInt("dailyCap", d.dailyCap),
            pausedUntil = prefs.getLong("pausedUntil", d.pausedUntil),
            lastRunAt = prefs.getLong("lastRunAt", d.lastRunAt),
            appLockEnabled = prefs.getBoolean("appLockEnabled", d.appLockEnabled),
            onboardingDone = prefs.getBoolean("onboardingDone", d.onboardingDone),
            weeklySummaryEnabled = prefs.getBoolean("weeklySummaryEnabled", d.weeklySummaryEnabled),
            weeklyGoal = prefs.getInt("weeklyGoal", d.weeklyGoal),
            lastBackupAt = prefs.getLong("lastBackupAt", d.lastBackupAt),
        )
    }
}
