package app.kinkeep.domain

/** First-import suggestion so the dashboard is not flooded with every contact on the phone. */
object CircleSuggester {
    fun suggest(starred: Boolean, interactionsLast90Days: Int): Circle = when {
        starred -> Circle.CLOSE
        interactionsLast90Days >= 8 -> Circle.CLOSE
        interactionsLast90Days >= 3 -> Circle.FRIENDS
        else -> Circle.IGNORED   // not tracked until the user adds them
    }
}
