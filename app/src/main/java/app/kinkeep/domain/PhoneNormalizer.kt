package app.kinkeep.domain

/**
 * Offline phone matching without a heavy library.
 * Two numbers match when their last 9 digits match, so "+234 803 123 4567", "0803 123 4567"
 * and "00234 803 123 4567" are the same person. Trade-off: numbers from different countries that
 * share the same last 9 digits would collide; this is very rare. Swap in libphonenumber later if needed.
 */
object PhoneNormalizer {
    private const val MIN_DIGITS = 6   // ignore short codes and service numbers
    private const val KEY_LENGTH = 9

    fun matchKey(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.length < MIN_DIGITS) return null
        return digits.takeLast(KEY_LENGTH)
    }
}
