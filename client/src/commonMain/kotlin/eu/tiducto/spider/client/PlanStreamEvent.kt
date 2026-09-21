package eu.tiducto.spider.client

import kotlinx.collections.immutable.ImmutableList

/**
 * One event from [SpiderRouting.planStream]. The router sweeps the search window forward and pushes
 * itineraries as they finalize: zero or more [Chunk]s, then a [Page] with the continuation cursors, then
 * a terminal [Done]. A [Failure] is terminal and takes the place of the rest.
 */
sealed interface PlanStreamEvent {

    /**
     * A batch of finalized itineraries as the search frontier advances. Each [Itinerary]'s legs carry the
     * scheduled times plus the realtime delay fields ([Leg.startEstimated] / [Leg.endEstimated] /
     * [Leg.startDelay] / [Leg.endDelay] / [Leg.isRealtime] / [Leg.realtimeState]) — the same delay handling
     * the one-shot [SpiderRouting.plan] applies. [frontierSeconds] is how far (seconds from the search
     * start) the window has swept; [found] is the running count discovered and [finalized] the count
     * committed so far.
     */
    data class Chunk(
        val frontierSeconds: Long,
        val found: Int,
        val finalized: Int,
        val itineraries: ImmutableList<Itinerary>,
    ) : PlanStreamEvent

    /**
     * Continuation cursors for the stream, mirroring [Route.pageInfo]. Re-call [SpiderRouting.planStream]
     * with the same inputs plus `after` = [RoutePageInfo.endCursor] to stream the next window (or `before`
     * = [RoutePageInfo.startCursor] for the previous one).
     */
    data class Page(val pageInfo: RoutePageInfo) : PlanStreamEvent

    /**
     * Terminal summary once the sweep stops: how many [iterations] ran, the window reached in
     * [windowSeconds], the total [resultCount], and why it [stoppedBy] (e.g. `targetResults` or `maxWindow`).
     */
    data class Done(
        val iterations: Int,
        val windowSeconds: Long,
        val resultCount: Int,
        val stoppedBy: String,
    ) : PlanStreamEvent

    /**
     * Terminal failure — a transport/HTTP problem, a decoding error, or a server `error` event (e.g. an
     * invalid request). [error] is the same [SpiderError] taxonomy the one-shot calls return.
     */
    data class Failure(val error: SpiderError) : PlanStreamEvent
}
