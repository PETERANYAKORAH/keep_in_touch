package app.kinkeep.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemGuideTest {
    @Test fun detectsPhoneMakersAndTheirSubBrands() {
        assertEquals(Oem.XIAOMI, OemGuide.detect("Xiaomi", "Redmi"))
        assertEquals(Oem.XIAOMI, OemGuide.detect("Xiaomi", "POCO"))
        assertEquals(Oem.SAMSUNG, OemGuide.detect("samsung", "samsung"))
        assertEquals(Oem.OPPO, OemGuide.detect("realme", "realme"))
        assertEquals(Oem.OPPO, OemGuide.detect("OnePlus", null))
        assertEquals(Oem.VIVO, OemGuide.detect("vivo", "iQOO"))
        assertEquals(Oem.HUAWEI, OemGuide.detect("HONOR", "honor"))
        assertEquals(Oem.TRANSSION, OemGuide.detect("TECNO", "TECNO"))
        assertEquals(Oem.TRANSSION, OemGuide.detect("INFINIX", "Infinix"))
        assertEquals(Oem.TRANSSION, OemGuide.detect("itel", "itel"))
    }

    @Test fun unknownOrMissingMakersFallBackToGenericAdvice() {
        assertEquals(Oem.OTHER, OemGuide.detect("Google", "google"))
        assertEquals(Oem.OTHER, OemGuide.detect(null, null))
    }
}

class ReminderHealthTest {
    private val hour = 3_600_000L
    private val now = 100 * hour

    @Test fun staleWhenRemindersAreOnButHaveNotRunForOverThreeHours() {
        assertTrue(ReminderHealth.isStale(true, 0L, now - 4 * hour, now))
        assertFalse(ReminderHealth.isStale(true, 0L, now - 2 * hour, now))
    }

    @Test fun notStaleWhenOffPausedOrNeverRun() {
        assertFalse(ReminderHealth.isStale(false, 0L, now - 10 * hour, now))
        assertFalse(ReminderHealth.isStale(true, now + hour, now - 10 * hour, now))
        assertFalse(ReminderHealth.isStale(true, 0L, 0L, now))
    }
}
