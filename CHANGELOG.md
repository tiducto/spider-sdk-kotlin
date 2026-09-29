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
  `targetResults` sets a soft floor and `maxWindow` caps the sweep; `maxWindow` is optional and, left unset,
  the API applies its own default cap. `planStream` is the initial call;
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

### Changed

- **Typed routing enums** (breaking). `Leg.realtimeState` and `Departure.realtimeState` are `RealtimeState`,
  and `RoutingError.code` / `RoutingError.inputField` are `RoutingErrorCode` / `InputField`, instead of
  `String`. The enums are open: a value the API adds later maps to `UNKNOWN`, and a minor release may add
  entries, so keep an `else` branch when matching on them.
- A routing call whose query id the API no longer serves (a retired query) now reads as an SDK update: it is
  `SpiderError.Unauthorized` with `httpStatus` 403, `serverCode` `persisted_query_rejected`, and a message
  that says to update the SDK.
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
  an older SDK keeps working until a query it uses is retired (see Changed).

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
