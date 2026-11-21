package app.kinkeep.domain

import kotlin.random.Random

/** Warm, shame-free wording. Templates are rotated so the same line is not reused within 14 days when avoidable. */
object ReminderCopy {
    const val AVOID_DAYS = 14

    val overdue = listOf(
        "{name} would probably love to hear from you.",
        "A quick hello to {name} could brighten both your days.",
        "It has been a little while. Why not say hi to {name}?",
        "Thinking of {name}? Let them know.",
        "A two-minute call can mean a lot to {name}.",
        "Your next good conversation might be with {name}.",
        "{name} is one tap away. A short call is enough.",
        "No pressure, just a friendly nudge to check in with {name}.",
        "Say hello to {name} today. Even a short message counts.",
        "Catch up with {name}. You will both be glad you did.",
        "A voice is better than a text. Give {name} a quick ring?",
        "Remember something funny? Share it with {name}.",
        "{name} would love a check-in from you.",
        "A little hello goes a long way. Try {name}.",
        "It is a good moment to reconnect with {name}.",
        "Ask {name} how their week is going.",
        "Small calls build strong bonds. {name} is next on your list.",
        "Is {name} on your mind? Let them hear from you.",
        "Take two minutes for {name} today.",
        "A friendly voice makes a good day better. Call {name}.",
        "Reach out to {name}. They will appreciate being remembered.",
        "How is {name} doing? Find out.",
        "Pick up the phone for {name}. It does not need to be long.",
        "Your people matter. Start with {name}.",
        "A quick message to {name} keeps the connection warm.",
        "Today is a good day to call {name}.",
        "Surprise {name} with a hello.",
        "Keep the bond with {name} strong. A short chat will do.",
        "{name} might be waiting for a reason to smile. Be it.",
        "Time for a catch-up with {name}?",
        "Share a bit of your day with {name}.",
        "Looking for someone to call? {name} would be happy to hear you.",
    )

    val missed = listOf(
        "You missed a call from {name}. A quick call back would mean a lot.",
        "{name} tried to reach you. Call them back when you can.",
        "Missed call from {name}. Return it while it is fresh.",
        "{name} called earlier. Give them a ring back?",
        "A call from {name} went unanswered. Let them know you are there.",
    )

    val birthday = listOf(
        "It is {name}'s birthday today. A quick call or message will make their day.",
        "Birthday time for {name}! Send your wishes.",
        "{name} celebrates today. Say happy birthday.",
        "Today is {name}'s birthday. A voice call beats a text.",
        "Do not forget {name}'s birthday. A warm wish goes a long way.",
        "{name}'s special day is today. Reach out and celebrate with them.",
    )

    fun templates(kind: ReminderKind): List<String> = when (kind) {
        ReminderKind.OVERDUE -> overdue
        ReminderKind.MISSED_CALL -> missed
        ReminderKind.BIRTHDAY -> birthday
    }

    data class Pick(val index: Int, val text: String)

    fun pick(kind: ReminderKind, firstName: String, history: Map<Int, Long>, now: Long, random: Random = Random.Default): Pick {
        val list = templates(kind)
        val cutoff = now - AVOID_DAYS * ScoringEngine.DAY_MS
        val fresh = list.indices.filter { (history[it] ?: Long.MIN_VALUE) < cutoff }
        val index = if (fresh.isNotEmpty()) fresh[random.nextInt(fresh.size)] else list.indices.minBy { history[it] ?: 0L }
        return Pick(index, list[index].replace("{name}", firstName))
    }

    fun firstName(displayName: String): String = displayName.trim().substringBefore(' ').ifBlank { displayName }

    /** History is stored as "index:timestamp" pairs separated by commas. */
    fun parseHistory(csv: String?): Map<Int, Long> =
        csv.orEmpty().split(',').mapNotNull { part ->
            val bits = part.split(':')
            val idx = bits.getOrNull(0)?.toIntOrNull()
            val ts = bits.getOrNull(1)?.toLongOrNull()
            if (idx != null && ts != null) idx to ts else null
        }.toMap()

    fun formatHistory(history: Map<Int, Long>, now: Long): String {
        val cutoff = now - AVOID_DAYS * ScoringEngine.DAY_MS
        return history.filterValues { it >= cutoff }.entries.joinToString(",") { "${it.key}:${it.value}" }
    }
}
