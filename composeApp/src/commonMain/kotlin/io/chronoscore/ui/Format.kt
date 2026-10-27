package io.chronoscore.ui

/** Local date and time for library rows, e.g. "21 Oct · 14:32". */
expect fun formatDateTime(epochMillis: Long): String

/** Clock text: "0:42", "12:03", "1:05:09". Negative values (overtime) get a leading minus. */
fun formatDuration(millis: Long): String {
    val sign = if (millis < 0) "−" else ""
    val total = kotlin.math.abs(millis) / 1000
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return sign + if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}

/** A score with a typographic minus ("−5"); with [plus], positive values get a "+" too. */
fun formatScore(value: Int, plus: Boolean = false): String = when {
    value < 0 -> "−${-value}"
    plus && value > 0 -> "+$value"
    else -> "$value"
}

/** UTC calendar date "2026-10-21" for file names. */
fun isoDate(epochMillis: Long): String {
    // Days since 1970-01-01 → civil date (Howard Hinnant's algorithm).
    val z = epochMillis.floorDiv(86_400_000L) + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
