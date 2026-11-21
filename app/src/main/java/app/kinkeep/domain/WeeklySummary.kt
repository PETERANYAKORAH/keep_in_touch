package app.kinkeep.domain

import java.time.DayOfWeek

object WeeklySummary {
    /** Sunday, at or after the reminder time, outside quiet hours, and not already sent this week. */
    fun shouldSend(
        day: DayOfWeek, nowHour: Int, defaultHour: Int, quiet: QuietWindow,
        lastSentEpochDay: Long, todayEpochDay: Long,
    ): Boolean = day == DayOfWeek.SUNDAY &&
        !quiet.contains(nowHour) &&
        nowHour >= defaultHour &&
        todayEpochDay - lastSentEpochDay >= 6
}
