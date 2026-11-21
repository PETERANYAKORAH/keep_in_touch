package app.kinkeep.domain

object Streaks {
    const val DEFAULT_GOAL = 3
    const val WEEKS = 52
    private const val WEEK_MS = 7 * ScoringEngine.DAY_MS

    /** People you had a meaningful conversation with in each rolling 7-day period. Index 0 is the latest. */
    fun distinctPeoplePerWeek(history: Map<Long, List<Interaction>>, now: Long, weeks: Int = WEEKS): List<Int> {
        val out = IntArray(weeks)
        for (list in history.values) {
            val weeksSeen = HashSet<Int>()
            for (i in list) {
                if (ScoringEngine.weight(i) < ScoringEngine.MEANINGFUL_WEIGHT) continue
                val age = now - i.timestamp
                if (age < 0) continue
                val w = (age / WEEK_MS).toInt()
                if (w < weeks) weeksSeen += w
            }
            weeksSeen.forEach { out[it]++ }
        }
        return out.toList()
    }

    /**
     * Consecutive weeks that met the goal. This week counts once it has met the goal;
     * until then the streak is judged from last week, so it is never broken mid-week.
     */
    fun weeklyStreak(peoplePerWeek: List<Int>, goal: Int): Int {
        if (peoplePerWeek.isEmpty() || goal <= 0) return 0
        var i = if (peoplePerWeek[0] >= goal) 0 else 1
        var streak = 0
        while (i < peoplePerWeek.size && peoplePerWeek[i] >= goal) { streak++; i++ }
        return streak
    }
}
