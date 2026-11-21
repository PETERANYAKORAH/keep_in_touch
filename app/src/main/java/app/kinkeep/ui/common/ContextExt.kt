package app.kinkeep.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/** Compose's LocalContext may be wrapped; BiometricPrompt needs the FragmentActivity underneath. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
