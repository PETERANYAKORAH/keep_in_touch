package app.kinkeep.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class NoteCryptoTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val key = newKey()
    private val crypto = NoteCrypto { key }

    @Test fun roundTripIncludingUnicode() {
        val text = "Ask about her exam. Ọmọ rẹ dára? 🙂"
        val sealed = crypto.encrypt(text)
        assertTrue(sealed.startsWith(NoteCrypto.PREFIX))
        assertNotEquals(text, sealed)
        assertEquals(text, crypto.decrypt(sealed))
    }

    @Test fun emptyAndLegacyPlaintextPassThrough() {
        assertEquals("", crypto.encrypt(""))
        assertEquals("old plain note", crypto.decrypt("old plain note"))
    }

    @Test fun eachEncryptionIsDifferent() {
        assertNotEquals(crypto.encrypt("same"), crypto.encrypt("same"))
    }

    @Test fun wrongKeyOrTamperingGivesEmptyNotAGarbledNote() {
        val sealed = crypto.encrypt("secret")
        assertEquals("", NoteCrypto { newKey() }.decrypt(sealed))
        assertEquals("", crypto.decrypt(sealed.dropLast(3) + "AAA"))
        assertEquals("", crypto.decrypt(NoteCrypto.PREFIX + "not-base64!!"))
    }
}
