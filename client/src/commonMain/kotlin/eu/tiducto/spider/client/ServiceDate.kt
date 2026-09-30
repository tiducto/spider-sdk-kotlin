package eu.tiducto.spider.client

import kotlin.time.Instant

// serviceDay is "noon minus 12h" local time on the service date, so +12h is local noon, which is the same
// UTC calendar day for any zone within ±11h, DST included.
internal fun serviceDateOf(serviceDay: Long): String =
    Instant.fromEpochSeconds(serviceDay + 43_200).toString().substringBefore('T')

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

// Round-trips through Instant so impossible dates (2026-02-30) fail too, not just the wrong shape.
internal fun isServiceDate(value: String): Boolean =
    ISO_DATE.matches(value) &&
        runCatching { Instant.parse("${value}T00:00:00Z").toString().startsWith(value) }.getOrDefault(false)

internal fun requireServiceDate(value: String) {
    if (!isServiceDate(value)) {
        throw SpiderTransportException.BadRequest("serviceDate", "serviceDate is invalid")
    }
}
