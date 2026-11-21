package app.kinkeep.domain

import java.time.Instant
import java.time.ZoneId

object ReminderTiming {
    /** Median hour of day of the given timestamps, or null with too few samples to learn a habit. */
    fun typicalHour(timestamps: List<Long>, minSamples: Int = 3, hourOf: (Long) -> Int): Int? {
        if (timestamps.size < minSamples) return null
        val hours = timestamps.map(hourOf).sorted()
        return hours[hours.size / 2]
    }

    fun startOfToday(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfTomorrow(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
