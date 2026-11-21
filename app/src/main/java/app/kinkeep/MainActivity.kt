package app.kinkeep

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.kinkeep.notify.NotificationHelper
import app.kinkeep.security.BiometricAuth
import app.kinkeep.ui.KinKeepRoot
import app.kinkeep.ui.detail.DetailViewModel
import app.kinkeep.ui.home.HomeViewModel
import app.kinkeep.ui.lock.LockScreen
import app.kinkeep.ui.onboarding.OnboardingScreen
import app.kinkeep.ui.settings.SettingsViewModel
import app.kinkeep.ui.theme.KinKeepTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() { // FragmentActivity is required by BiometricPrompt
    private val home: HomeViewModel by viewModels()
    private val detail: DetailViewModel by viewModels()
    private val settings: SettingsViewModel by viewModels()
    private val deepLink = MutableStateFlow<Long?>(null)
    private val locked = MutableStateFlow(false)
    private var leftAt = Long.MIN_VALUE / 4 // so the very first start always locks
    private var authenticating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh launch, so rotating the phone does not reopen the same person.
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch { settings.prefs.collect { applySecureFlag(it.appLockEnabled) } }
        setContent {
            val prefs by settings.prefs.collectAsState()
            val isLocked by locked.collectAsState()
            KinKeepTheme {
                when {
                    prefs.appLockEnabled && isLocked -> LockScreen(onUnlock = ::authenticate)
                    !prefs.onboardingDone -> OnboardingScreen { importNow ->
                        settings.update { it.copy(onboardingDone = true) }
                        if (importNow) home.syncNow()
                    }
                    else -> KinKeepRoot(home, detail, settings, deepLink)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (settings.prefs.value.appLockEnabled && SystemClock.elapsedRealtime() - leftAt > RELOCK_AFTER_MS) {
            locked.value = true
            authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun authenticate() {
        if (authenticating) return
        authenticating = true
        BiometricAuth.prompt(
            this, getString(R.string.lock_prompt_title), getString(R.string.lock_prompt_subtitle),
            onSuccess = { authenticating = false; locked.value = false },
            onFailure = { authenticating = false },
        )
    }

    /** With app lock on, hide KinKeep from screenshots and the recent-apps preview. */
    private fun applySecureFlag(secure: Boolean) {
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(NotificationHelper.EXTRA_CONTACT_ID, -1L) ?: -1L
        if (id > 0) deepLink.value = id
    }

    private companion object { const val RELOCK_AFTER_MS = 30_000L }
}
