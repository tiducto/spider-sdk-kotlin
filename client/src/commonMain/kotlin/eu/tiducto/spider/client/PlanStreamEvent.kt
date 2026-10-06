package eu.tiducto.spider.client

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
     *
     * [routingErrors] mirrors [Route.routingErrors]: why the search found nothing (or less), such as
     * `OUTSIDE_SERVICE_PERIOD`, or `LOCATION_NOT_FOUND` with [InputField.FROM], [InputField.TO] or
     * [InputField.VIA]. Empty when there were none.
     */
    data class Done(
        val pageInfo: RoutePageInfo,
        val routingErrors: ImmutableList<RoutingError> = persistentListOf(),
    ) : PlanStreamEvent

    /**
     * Terminal failure — an invalid request ([SpiderError.BadRequest], whether the SDK or the API rejected
     * it), a transport/HTTP problem (a stream cut before [Done] is [SpiderError.Network]), or a decoding
     * error. [error] is the same [SpiderError] taxonomy the one-shot calls return. A search that simply finds
     * nothing ends in [Done] with [Done.routingErrors] instead.
     */
    data class Failure(val error: SpiderError) : PlanStreamEvent
}
