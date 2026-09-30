# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions track the Spider API contract:
the `major.minor` mirror the contract version and the trailing number is the SDK patch.

## [Unreleased]

### Added

- **Connection pre-warm** — `SpiderClient.warmup()` opens the TLS connection to the per-env API host
  ahead of the first real call. Cold connection setup (radio wake, DNS, TCP, TLS) is ~0.6s on mobile and
  otherwise lands on the first trip-planning call. `warmup()` issues one keyless `GET /ping` through the
  routing surface's own connection pool (the pool `plan`/`planStream` reuse), returns the measured elapsed
  `Duration`, and is best-effort — it never throws, and a `404` (before the gateway `/ping` route ships)
  still warms the connection. Recommended at app start and on return-to-foreground; safe to fire-and-forget.
- **Streaming trip planning** — `SpiderRouting.planStream(...)` streams itineraries over Server-Sent
  Events (the `plan-stream` route) as the router sweeps the search window, emitting a `Flow` of
  `PlanStreamEvent` — `Result(itineraries)` as batches finalize, a terminal `Done(pageInfo, routingErrors)`
  carrying the continuation cursors and any routing errors (shaped as in `plan`, empty when none), or a
  terminal `Failure(error)` (delivered as an event, not thrown). Built on Ktor's client `SSE` plugin.
  `targetResults` (how many itineraries the sweep aims for) and `maxWindow` (how far it may search, at least
  2 hours) are required, with no SDK default. `planStream` is the initial call;
  `planStreamNext(..., after = Done.pageInfo.endCursor)` and
  `planStreamPrevious(..., before = Done.pageInfo.startCursor)` continue it (each repeats the full parameter
  list, so `targetResults` / `maxWindow` can vary per continuation).
- Realtime **delays** on streamed itineraries: each `Result`'s legs carry the same estimated times and
  delay fields (`startDelay` / `endDelay` / `isRealtime` / `realtimeState`) as the one-shot `plan`.
- `Departure.serviceDate` and `TripDetails.serviceDate`: the GTFS service date (`YYYY-MM-DD`) the trip runs
  on. A departure after midnight on a night line belongs to the previous day's service; pass this value to
  `trip()` and `delays` so they look up the right day. A malformed date passed to either returns
  `SpiderError.BadRequest` (field `serviceDate`) without sending a request.
- `TransitMode.CABLE_CAR`, `GONDOLA`, `FUNICULAR` and `SNOW_AND_ICE`. These modes used to map to `UNKNOWN`.
- `SpiderError.QueryRetired` (`SpiderErrorCode.QUERY_RETIRED`, wire name `query_retired`): the persisted query
  a call sends is retired and the API no longer serves it (HTTP 410). It reports the query's state; it is not
  an `Unauthorized`.
- Plan-limit errors, one per state:
  - `SpiderError.PlanningLimitReached` (`SpiderErrorCode.PLANNING_LIMIT_REACHED`, wire name
    `planning_limit_reached`): the project has used the trip-planning searches its plan includes, so only `plan`
    and `planStream` are refused. Departures, trips, stop search and realtime still answer.
  - `SpiderError.AgreementInactive` (`SpiderErrorCode.AGREEMENT_INACTIVE`, wire name `agreement_inactive`): the
    project has no active agreement, so every call made with the key is refused.

  The body's code decides, whatever the HTTP status; a 403 without one of these codes stays `Unauthorized`.
  `message` is the API's (`trip planning limit reached`, `agreement is not active`), and `httpStatus` and
  `serverCode` are carried as for other errors. A `planStream` refused this way ends in `Failure` with that error.
- `Stop.modes`: the modes of the routes serving a stop, as `TransitMode` (an unrecognised mode is `UNKNOWN`),
  and a `modes` filter on stop search (`search { modes = setOf(TransitMode.TRAM) }`) matching stops served by
  any of the given modes (`UNKNOWN` is ignored). `Stop.code` (GTFS `stop_code`) and `Stop.locationType`
  (GTFS `location_type`), and `Stop.wheelchairBoarding` is now filled in from the stop's GTFS value.
- Display fields, null when the feed doesn't provide them. Colours are raw GTFS hex without `#` (e.g. `FF0000`):
  - `Leg.routeGtfsId`, `routeColor`, `routeTextColor`, `fromPlatformCode`, `toPlatformCode`, `fromZoneId`,
    `toZoneId`
  - `Departure.routeGtfsId`, `routeColor`, `routeTextColor`, `stopGtfsId`, `platformCode`,
    `wheelchairAccessible`
  - `TripDetails.routeGtfsId`, `routeColor`, `routeTextColor`, `wheelchairAccessible`
  - `TripStop.platformCode`, `zoneId`
- `WheelchairBoarding.UNKNOWN` and `BikesAllowed.UNKNOWN` for a value this SDK version doesn't recognise. No
  information (`NO_INFORMATION`) stays `null`.
- Client-side checks of the fixed platform limits. Each returns `SpiderError.BadRequest` naming only the field,
  without sending a request: stream `maxWindow` under 2 hours, departures `timeRange` not above zero or over
  24 hours, more than 50 realtime `tripIds` (counted across all service dates; none returns an empty result
  without a request), stop search `limit` outside 1–50, and a via location with no or more than 10 stop ids or
  a `minimumWaitTime` outside 0–24 hours. Limits the environment sets (search window, result count, departures
  count, via count) are checked by the API, which returns the same `BadRequest`.
- A `planStream` the gateway rejects before streaming (a JSON `BAD_REQUEST` body instead of an event stream)
  ends in `Failure(SpiderError.BadRequest)` with the field, like `plan`.
- An HTTP 400 from any surface is `SpiderError.BadRequest`; when its message reads `<field> is out of range`,
  `<field> is required` or `<field> is invalid`, `field` is set to that field.

### Changed

- **Typed routing enums** (breaking). `Leg.realtimeState` and `Departure.realtimeState` are `RealtimeState`,
  and `RoutingError.code` / `RoutingError.inputField` are `RoutingErrorCode` / `InputField`, instead of
  `String`. The enums are open: a value the API adds later maps to `UNKNOWN`, and a minor release may add
  entries, so keep an `else` branch when matching on them.
- **Enum constants are `SCREAMING_SNAKE_CASE`** (breaking): `WheelchairBoarding.POSSIBLE` / `NOT_POSSIBLE`,
  `BikesAllowed.ALLOWED` / `NOT_ALLOWED`, and `SpiderLogLevel.NONE` / `ERROR` / `INFO` / `HEADERS` / `BODY`
  (were `Possible`, `NotPossible`, `Allowed`, `NotAllowed`, `None`, `Error`, `Info`, `Headers`, `Body`).
  Rename the references. A serialized `Itinerary` or `Leg` now carries the new names.
- **`planStream`, `planStreamNext` and `planStreamPrevious` require `targetResults` and `maxWindow`**
  (breaking). Pass both, e.g. `targetResults = 5, maxWindow = 2.hours`.
- `departures` always sends `numberOfDepartures` (default 30) and `timeRange` (default 24 hours); a
  `timeRange` out of range is rejected instead of clamped.
- Stop search always sends `limit`. `StopRequest.limit` and the `limit` of `near` / `within` are an `Int`
  defaulting to 20 (was nullable, unset by default).
- An unknown persisted-query id (gateway `403 persisted_query_rejected`) is `SpiderError.Unauthorized` with
  that `serverCode`, and its message is the gateway's.
- A stop search's free text also matches the stop's code, town and district (server-side).
- `plan` sends `searchWindow` as given (ISO-8601, e.g. `PT1H`) and the API checks it; a window under a minute
  is no longer widened to one minute.
- A malformed `serviceDate` reads `serviceDate is invalid`, naming only the field.
- **Realtime `delays` now resolves per trip instance** (breaking). A GTFS-RT delay is bound to a
  `(tripId, serviceDate)` instance, so `SpiderRealtime.delays` takes the service date each trip runs on —
  `delays(byServiceDate: Map<String, List<String>>)` (or `delays(tripIds, serviceDate)` for a single day) —
  and returns `TripDelays.groups: List<ServiceDateDelays>`, looked up per instance via
  `delayFor(tripId, serviceDate)`. `serviceDate` is the GTFS service date `YYYY-MM-DD`, taken from the plan
  leg or departure (not the departure clock time — GTFS times can exceed 24:00). `pollDelays` mirrors the new
  signatures. The old flat `delays(tripIds)` is removed. Fixes cross-service-day delay bleed and the
  midnight-overlap ambiguity.

### Removed

- The contract-version check and `SpiderContractMismatchError`. The SDK still sends
  `x-spider-contract-version` on every request, but never fails a call over the version the API declares:
  an older SDK keeps working until a query it uses is retired (then `SpiderError.QueryRetired`).

### Fixed

- `departures()` no longer drops rows whose headsign equals the stop name. Those were real departures
  (for example a line heading to another stand of the same station), and dropping them made boards come back
  shorter than requested. The API now leaves out a trip's final stop itself.
- `autoRetry` now times retries the same way as the other SDKs. On a retried `429` or `5xx`, a
  `Retry-After` in seconds (fractions allowed) replaces the backoff. Otherwise it waits 1s, 2s, 4s… up to
  10s. Either delay gets up to 25 % jitter.
- `maxTransfers` now maps to the router's boarding count (`maximumTransfers = transfers + 1`). The
  router indexes legs with leg 0 as the initial access (walk, or nothing), so passing the caller's
  transfer count verbatim made `maxTransfers` 0 and 1 behave identically. Now `0` means direct,
  `1` allows one transfer, and so on.

## [0.1.0] - 2026-08-22

Initial public pre-release; targets Spider API contract 0.1.

**Stability:** this is a pre-1.0 release. While the version stays below `1.0.0`, any `0.x` minor
bump may introduce breaking changes to the public API. Pin an exact version and review the
changelog before upgrading.

### Covered surfaces

- **Trip planning** — multi-leg journeys via `planConnection`.
- **Stop departures** — upcoming departures for a stop, combining static schedule and realtime delays.
- **Single-trip lookup** — the stops and times of one trip.
- **Stop search** — text/autocomplete and geographic (nearest / bounding-box / radius) stop queries.
- **Realtime** — live vehicle positions, delays, and service alerts.
