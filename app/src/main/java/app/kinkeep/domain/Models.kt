package app.kinkeep.domain

enum class Circle(val label: String, val defaultCadenceDays: Int) {
    INNER("Inner Circle", 7),
    CLOSE("Close Friends & Family", 14),
    FRIENDS("Friends", 30),
    ACQUAINTANCES("Acquaintances", 90),
    IGNORED("Ignored", 0),
}

enum class InteractionType { CALL, SMS, MANUAL }

enum class Direction { INCOMING, OUTGOING, MISSED }

data class Interaction(
    val timestamp: Long,
    val type: InteractionType,
    val direction: Direction,
    val durationSec: Int = 0,
)

enum class HealthCategory(val label: String, val minScore: Int) {
    THRIVING("Thriving", 80),
    HEALTHY("Healthy", 60),
    NEEDS_ATTENTION("Needs Attention", 40),
    FADING("Fading", 20),
    DORMANT("Dormant", 0),
}

data class HealthResult(
    val score: Int,
    val category: HealthCategory,
    val daysSinceLastContact: Int?,
    val unreturnedMissedCalls: Int,
)
