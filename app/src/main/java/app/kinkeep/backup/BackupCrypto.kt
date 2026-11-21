package app.kinkeep.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected backup file:
 *   "KINKEEP1" | iterations (4 bytes) | salt (16) | iv (12) | AES-256-GCM ciphertext and tag
 * The key is derived with PBKDF2-HMAC-SHA256. There is no way to recover a backup without its password.
 */
object BackupCrypto {
    private val MAGIC = "KINKEEP1".toByteArray(Charsets.US_ASCII)
    const val DEFAULT_ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val HEADER_BYTES = MAGIC.size + 4 + SALT_BYTES + IV_BYTES

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).putInt(iterations).put(salt).put(iv).array()
        return header + cipher.doFinal(plain)
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size < HEADER_BYTES + TAG_BITS / 8 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupException("This is not a KinKeep backup file.")
        }
        val buffer = ByteBuffer.wrap(file, MAGIC.size, file.size - MAGIC.size)
        val iterations = buffer.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw BackupException("The backup file is damaged.")
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            throw BackupException("Wrong password, or the backup file is damaged.")
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
