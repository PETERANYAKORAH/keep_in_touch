package app.kinkeep.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainHelpersTest {
    @Test fun nigerianNumberFormatsMatch() {
        val a = PhoneNormalizer.matchKey("+234 803 123 4567")
        assertEquals(a, PhoneNormalizer.matchKey("0803 123 4567"))
        assertEquals(a, PhoneNormalizer.matchKey("00234 803 123 4567"))
    }

    @Test fun usNumberFormatsMatch() {
        assertEquals(PhoneNormalizer.matchKey("(415) 555-2671"), PhoneNormalizer.matchKey("+1 415 555 2671"))
    }

    @Test fun differentNumbersDoNotMatch() {
        assertNotEquals(PhoneNormalizer.matchKey("0803 123 4567"), PhoneNormalizer.matchKey("0803 123 4568"))
    }

    @Test fun shortCodesBlanksAndSenderNamesAreIgnored() {
        assertNull(PhoneNormalizer.matchKey("112"))
        assertNull(PhoneNormalizer.matchKey("  "))
        assertNull(PhoneNormalizer.matchKey(null))
        assertNull(PhoneNormalizer.matchKey("MTN-NG"))
    }

    @Test fun circleSuggestion() {
        assertEquals(Circle.CLOSE, CircleSuggester.suggest(starred = true, interactionsLast90Days = 0))
        assertEquals(Circle.CLOSE, CircleSuggester.suggest(false, 8))
        assertEquals(Circle.FRIENDS, CircleSuggester.suggest(false, 3))
        assertEquals(Circle.IGNORED, CircleSuggester.suggest(false, 2))
    }
}
