package eu.tiducto.spider.client

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

private const val DEFAULT_TARGET_RESULTS = 10

// Each cursor step pulls a whole search window: the SDK sends no page-size count, so the server returns up to
// its own default itinerary cap per page (large enough that the next cursor advances by the FULL window, not
// by a few results — verified: nextEdt = currentEdt + searchWindow only when that cap didn't crop the window).

/**
 * Streams itineraries by stepping the search forward one [searchWindow] at a time, until [targetResults]
 * itineraries have been collected or [maxTraversal] of time has been traversed. Each step pulls a whole
 * window in one search, so a busy window yields more — [targetResults] is a soft floor, the window that
 * reaches it is emitted whole. Cold and lazy: cancel/`take` stops stepping and skips the remaining OTP
 * searches. Each emission is one step's [SpiderResult] (a terminal Error ends the stream), mirroring the
 * non-throwing poll helpers in [pollVehicles].
 *
 * The step is [searchWindow] and it is fixed for the whole walk — OTP locks the window into the paging
 * cursor after the first search and ignores it thereafter, so a wider step means a wider [searchWindow]
 * here, not on a continuation. For a plain one-shot batch, a single [SpiderRouting.plan] is the primitive.
 */
fun SpiderRouting.planUntil(
    origin: Location,
    destination: Location,
    time: RouteTime = RouteTime.DepartAt(Clock.System.now()),
    targetResults: Int = DEFAULT_TARGET_RESULTS,
    searchWindow: Duration = 1.hours,
    maxTraversal: Duration = 6.hours,
    via: List<ViaLocation> = emptyList(),
    allowedTransitModes: Set<TransitMode>? = null,
    maxTransfers: Int? = null,
    wheelchairAccessible: Boolean = false,
): Flow<SpiderResult<Route>> {
    val routing = this
    return flow {
        val first = routing.plan(
            origin = origin, destination = destination, time = time, via = via,
            allowedTransitModes = allowedTransitModes, maxTransfers = maxTransfers,
            searchWindow = searchWindow, wheelchairAccessible = wheelchairAccessible,
        )
        emit(first)
        val page = (first as? SpiderResult.Success)?.data ?: return@flow
        stepThrough(routing, page, forward = true, stepCount(maxTraversal, searchWindow) - 1, targetResults, page.edges.size)
    }
}

/** Streaming form of [SpiderRouting.planNext]: steps forward from [prev], one (fixed) search window per step. */
fun SpiderRouting.planNextUntil(
    prev: Route,
    targetResults: Int = DEFAULT_TARGET_RESULTS,
    maxTraversal: Duration = 6.hours,
): Flow<SpiderResult<Route>> {
    val routing = this
    return flow { stepThrough(routing, prev, forward = true, stepCount(maxTraversal, prev.request.searchWindow), targetResults, 0) }
}

/** Streaming form of [SpiderRouting.planPrevious]: steps backward from [prev], one (fixed) search window per step. */
fun SpiderRouting.planPreviousUntil(
    prev: Route,
    targetResults: Int = DEFAULT_TARGET_RESULTS,
    maxTraversal: Duration = 6.hours,
): Flow<SpiderResult<Route>> {
    val routing = this
    return flow { stepThrough(routing, prev, forward = false, stepCount(maxTraversal, prev.request.searchWindow), targetResults, 0) }
}

// Step from [start] one search window at a time, accumulating itinerary count. Stop at [targetResults], when
// a step has no next page, or on an Error. [collectedSoFar] seeds the count (planUntil already emitted step 1).
private suspend fun FlowCollector<SpiderResult<Route>>.stepThrough(
    routing: SpiderRouting,
    start: Route,
    forward: Boolean,
    remainingSteps: Int,
    targetResults: Int,
    collectedSoFar: Int,
) {
    if (collectedSoFar >= targetResults) return
    var prev = start
    var collected = collectedSoFar
    repeat(remainingSteps.coerceAtLeast(0)) {
        val res = (if (forward) routing.planNext(prev) else routing.planPrevious(prev)) ?: return
        emit(res)
        val page = (res as? SpiderResult.Success)?.data ?: return
        collected += page.edges.size
        if (collected >= targetResults) return
        prev = page
    }
}

// Each cursor step advances a full search window (the server returns a whole window per page), so the number
// of steps to traverse [maxTraversal] is a plain division by the (fixed) step — no searchWindowUsed parsing.
private fun stepCount(maxTraversal: Duration, step: Duration): Int =
    (maxTraversal / step.coerceAtLeast(1.minutes)).toInt().coerceAtLeast(1)
