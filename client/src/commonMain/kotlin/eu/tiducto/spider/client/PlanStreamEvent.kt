package eu.tiducto.spider.client

import kotlinx.collections.immutable.ImmutableList

/**
 * One event from [SpiderRouting.planStream] (and its [SpiderRouting.planStreamNext] /
 * [SpiderRouting.planStreamPrevious] continuations). The router sweeps the search window and pushes
 * itineraries as they finalize: zero or more [Result]s, then a terminal [Done] carrying the continuation
 * cursors. A [Failure] is terminal and takes the place of the rest.
 */
sealed interface PlanStreamEvent {

    /**
     * A batch of finalized itineraries as the search frontier advances. Each [Itinerary]'s legs carry the
     * scheduled times plus the realtime delay fields ([Leg.startEstimated] / [Leg.endEstimated] /
     * [Leg.startDelay] / [Leg.endDelay] / [Leg.isRealtime] / [Leg.realtimeState]) — the same delay handling
     * the one-shot [SpiderRouting.plan] applies.
     */
    data class Result(val itineraries: ImmutableList<Itinerary>) : PlanStreamEvent

    /**
     * Terminal event carrying the continuation cursors, mirroring [Route.pageInfo]. When
     * [RoutePageInfo.hasNextPage] is set, call [SpiderRouting.planStreamNext] with `after` =
     * [RoutePageInfo.endCursor] to stream the next window; when [RoutePageInfo.hasPreviousPage] is set,
     * call [SpiderRouting.planStreamPrevious] with `before` = [RoutePageInfo.startCursor].
     */
    data class Done(val pageInfo: RoutePageInfo) : PlanStreamEvent

    /**
     * Terminal failure — a transport/HTTP problem, a decoding error, or a server `error` event (e.g. an
     * invalid request). [error] is the same [SpiderError] taxonomy the one-shot calls return.
     */
    data class Failure(val error: SpiderError) : PlanStreamEvent
}
