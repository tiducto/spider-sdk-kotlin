package eu.tiducto.spider.client

import kotlin.time.Instant

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
