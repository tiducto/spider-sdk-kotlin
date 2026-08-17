package eu.tiducto.spider.client

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Streams itineraries page by page, extending the search forward (later departures) until [maxSearchWindow]
 * of time has been searched or OTP runs out of pages. Cold and lazy: it only fetches as far as the collector
 * consumes, so `.take(n)` stops paging after n pages and never runs the extra OTP searches. Each emission is
 * one page's [SpiderResult] — a terminal [SpiderResult.Error] ends the stream — mirroring the non-throwing
 * poll helpers in [pollVehicles]. For a plain big batch, prefer one [SpiderRouting.plan] with a wider
 * [searchWindow]; reach for this only when you want the pages as they arrive.
 */
fun SpiderRouting.planUntil(
    origin: Location,
    destination: Location,
    time: RouteTime = RouteTime.DepartAt(Clock.System.now()),
    maxSearchWindow: Duration = 4.hours,
    pageSize: Int = SpiderRouting.DEFAULT_FIRST,
    via: List<ViaLocation> = emptyList(),
    allowedTransitModes: Set<TransitMode>? = null,
    maxTransfers: Int? = null,
    searchWindow: Duration = 1.hours,
    wheelchairAccessible: Boolean = false,
): Flow<SpiderResult<Route>> {
    val routing = this
    return flow {
        val first = routing.plan(
            origin = origin, destination = destination, time = time, first = pageSize, via = via,
            allowedTransitModes = allowedTransitModes, maxTransfers = maxTransfers,
            searchWindow = searchWindow, wheelchairAccessible = wheelchairAccessible,
        )
        emit(first)
        val page = (first as? SpiderResult.Success)?.data ?: return@flow
        pageThrough(routing, page, forward = true, extraPages = pageBudget(maxSearchWindow, searchWindow) - 1, pageSize)
    }
}

/** Streaming form of [SpiderRouting.planNext]: later departures continuing after [prev], page by page. */
fun SpiderRouting.planNextUntil(
    prev: Route,
    maxSearchWindow: Duration = 4.hours,
    pageSize: Int = SpiderRouting.DEFAULT_FIRST,
): Flow<SpiderResult<Route>> {
    val routing = this
    return flow { pageThrough(routing, prev, forward = true, pageBudget(maxSearchWindow, prev.request.searchWindow), pageSize) }
}

/** Streaming form of [SpiderRouting.planPrevious]: earlier departures before [prev], page by page. */
fun SpiderRouting.planPreviousUntil(
    prev: Route,
    maxSearchWindow: Duration = 4.hours,
    pageSize: Int = SpiderRouting.DEFAULT_FIRST,
): Flow<SpiderResult<Route>> {
    val routing = this
    return flow { pageThrough(routing, prev, forward = false, pageBudget(maxSearchWindow, prev.request.searchWindow), pageSize) }
}

// Emit successive pages from [start] in one direction. A null page (nothing more) or an Error ends the stream.
private suspend fun FlowCollector<SpiderResult<Route>>.pageThrough(
    routing: SpiderRouting,
    start: Route,
    forward: Boolean,
    extraPages: Int,
    pageSize: Int,
) {
    var prev = start
    repeat(extraPages.coerceAtLeast(0)) {
        val res = (if (forward) routing.planNext(prev, pageSize) else routing.planPrevious(prev, pageSize)) ?: return
        emit(res)
        prev = (res as? SpiderResult.Success)?.data ?: return
    }
}

// The fixed per-page window makes the max-window bound a deterministic page count — no searchWindowUsed parsing.
private fun pageBudget(maxSearchWindow: Duration, perPage: Duration): Int =
    (maxSearchWindow / perPage.coerceAtLeast(1.minutes)).toInt().coerceAtLeast(1)
