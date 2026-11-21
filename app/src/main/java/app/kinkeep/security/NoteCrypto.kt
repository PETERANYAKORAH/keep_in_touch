package app.kinkeep.security

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM for notes. Stored form is "enc1:" + Base64(iv + ciphertext + tag).
 * Plain text without the prefix (older notes) is returned unchanged, so nothing is lost on upgrade.
 * The key comes from [keyProvider]: the Android Keystore in the app, a plain key in unit tests.
 */
class NoteCrypto(private val keyProvider: () -> SecretKey) {

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return plain
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(sealed)
    }

    /** Returns an empty string if the note cannot be decrypted (for example, the key was removed). */
    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val raw = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, IV_BYTES)))
            String(cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size)), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            ""
        } catch (e: IllegalArgumentException) {
            ""
        } catch (e: IndexOutOfBoundsException) {
            ""
        }
    }

    companion object {
        const val PREFIX = "enc1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
