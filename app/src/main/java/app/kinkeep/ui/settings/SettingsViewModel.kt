package app.kinkeep.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kinkeep.R
import app.kinkeep.backup.BackupManager
import app.kinkeep.data.AppPrefs
import app.kinkeep.diagnostics.ErrorLog
import app.kinkeep.data.SettingsStore
import app.kinkeep.notify.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val store: SettingsStore,
    private val backup: BackupManager,
    private val errorLog: ErrorLog,
) : ViewModel() {
    val prefs: StateFlow<AppPrefs> = store.state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _errorLogText = MutableStateFlow(errorLog.read())
    val errorLogText: StateFlow<String> = _errorLogText

    fun clearErrorLog() {
        errorLog.clear()
        _errorLogText.value = ""
    }

    fun update(change: (AppPrefs) -> AppPrefs) = store.update(change)
    fun sendTest() = NotificationHelper.showTest(ctx)

    /** Writes an encrypted backup to the file the person picked. The password is wiped afterwards. */
    fun export(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            _message.value = try {
                val bytes = backup.export(password)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: throw IOException("Could not open the file")
                }
                store.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
                ctx.getString(R.string.backup_done)
            } catch (e: Exception) {
                ctx.getString(R.string.backup_failed, e.message ?: "")
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun restore(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            _message.value = try {
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("Could not open the file")
                }
                val summary = backup.restore(bytes, password)
                ctx.getString(R.string.restore_done, summary.contacts, summary.interactions)
            } catch (e: Exception) {
                ctx.getString(R.string.restore_failed, e.message ?: "")
            } finally {
                password.fill('\u0000')
            }
        }
    }

    /** After this, onboarding shows again because every setting is reset. */
    fun wipeAll() {
        viewModelScope.launch {
            backup.wipeAll()
            _message.value = null
        }
    }
}
