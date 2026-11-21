package app.kinkeep.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Relationship Health Score (0-100). Pure Kotlin, no Android dependencies, fully unit-testable.
 *
 * score = 0.50 * recency + 0.25 * frequency + 0.15 * quality + 0.10 * reciprocity - missedCallPenalty
 *
 * recency     : how long since the last meaningful contact, relative to the target cadence.
 * frequency   : weighted contacts in the last 90 days vs. how many the cadence expects.
 * quality     : talk minutes in the last 90 days vs. 5 minutes per expected contact.
 * reciprocity : balance of who initiates (50/50 is best).
 * penalty     : 10 points per unreturned missed call in the last 30 days (max 20).
 */
object ScoringEngine {
    const val DAY_MS = 86_400_000L
    const val WINDOW_DAYS = 90
    const val MEANINGFUL_WEIGHT = 0.4
    private const val MISSED_LOOKBACK_DAYS = 30

    fun weight(i: Interaction): Double = when {
        i.direction == Direction.MISSED -> 0.0
        i.type == InteractionType.MANUAL -> 1.0
        i.type == InteractionType.CALL -> if (i.durationSec >= 60) 1.0 else 0.3
        i.type == InteractionType.SMS -> 0.4
        else -> 0.0
    }

    fun categoryFor(score: Int): HealthCategory =
        HealthCategory.entries.first { score >= it.minScore }

    fun score(cadenceDays: Int, interactions: List<Interaction>, now: Long): HealthResult {
        require(cadenceDays > 0) { "Ignored contacts (cadence 0) must be filtered out before scoring" }

        val lastMeaningful = interactions
            .filter { weight(it) >= MEANINGFUL_WEIGHT }
            .maxOfOrNull { it.timestamp }
        val daysSince = lastMeaningful?.let { (now - it) / DAY_MS.toDouble() }

        // Recency: 100 at 0 days, 75 when exactly due, 0 once 3x overdue.
        val recency = when {
            daysSince == null -> 0.0
            else -> {
                val r = daysSince / cadenceDays
                if (r <= 1.0) 100.0 - 25.0 * r else max(0.0, 75.0 - 37.5 * (r - 1.0))
            }
        }

        val windowStart = now - WINDOW_DAYS * DAY_MS
        val recent = interactions.filter { it.timestamp in windowStart..now && it.direction != Direction.MISSED }
        val expected = WINDOW_DAYS.toDouble() / cadenceDays

        val frequency = min(1.0, recent.sumOf { weight(it) } / expected) * 100.0

        val talkMinutes = recent.filter { it.type == InteractionType.CALL }.sumOf { it.durationSec } / 60.0
        val quality = min(1.0, talkMinutes / (expected * 5.0)) * 100.0

        val reciprocity = if (recent.isEmpty()) 0.0 else {
            val outShare = recent.count { it.direction == Direction.OUTGOING }.toDouble() / recent.size
            (1.0 - abs(outShare - 0.5) * 2.0) * 100.0
        }

        val unreturned = countUnreturnedMissed(interactions, now)
        val penalty = min(20.0, unreturned * 10.0)

        val raw = 0.50 * recency + 0.25 * frequency + 0.15 * quality + 0.10 * reciprocity - penalty
        val score = raw.coerceIn(0.0, 100.0).roundToInt()

        return HealthResult(
            score = score,
            category = categoryFor(score),
            daysSinceLastContact = daysSince?.toInt(),
            unreturnedMissedCalls = unreturned,
        )
    }

    /** A missed call counts as unreturned if nothing answered or outgoing happened after it. */
    fun countUnreturnedMissed(interactions: List<Interaction>, now: Long): Int {
        val cutoff = now - MISSED_LOOKBACK_DAYS * DAY_MS
        return interactions
            .filter { it.direction == Direction.MISSED && it.timestamp >= cutoff }
            .count { missed -> interactions.none { weight(it) > 0.0 && it.timestamp > missed.timestamp } }
    }
}
