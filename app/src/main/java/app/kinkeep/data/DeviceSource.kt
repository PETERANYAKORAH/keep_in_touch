package app.kinkeep.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Where contacts and calls come from. The real one reads the phone; tests supply a fake. */
interface DeviceSource {
    fun readContacts(): List<ImportedContact>
    fun readBirthdays(): Map<Long, String>
    fun readCalls(afterLogId: Long): List<ImportedCall>
}

fun interface PermissionChecker {
    fun has(permission: String): Boolean
}

class AndroidPermissionChecker @Inject constructor(
    @ApplicationContext private val ctx: Context,
) : PermissionChecker {
    override fun has(permission: String): Boolean =
        ctx.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
