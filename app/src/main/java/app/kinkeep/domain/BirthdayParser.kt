package app.kinkeep.domain

import java.util.Locale

object BirthdayParser {
    private val pattern = Regex("""^(?:\d{4}-|--)(\d{2})-(\d{2})$""")

    fun format(month: Int, day: Int): String = String.format(Locale.ROOT, "%02d-%02d", month, day)

    /** Accepts "1990-05-17" or "--05-17" (year unknown) and returns "05-17". */
    fun monthDay(raw: String?): String? {
        val m = pattern.matchEntire(raw?.trim().orEmpty()) ?: return null
        val month = m.groupValues[1].toInt()
        val day = m.groupValues[2].toInt()
        if (month !in 1..12 || day !in 1..31) return null
        return format(month, day)
    }
}
