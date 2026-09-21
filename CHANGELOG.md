# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions track the Spider API contract:
the `major.minor` mirror the contract version and the trailing number is the SDK patch.

## [Unreleased]

### Added

- **Streaming trip planning** — `SpiderRouting.planStream(...)` streams itineraries over Server-Sent
  Events (the `plan-stream` route) as the router sweeps the search window, emitting a `Flow` of
  `PlanStreamEvent` (`Chunk` / `Page` / `Done` / `Failure`) instead of one batched page. Built on
  Ktor's client `SSE` plugin. `targetResults` sets a soft floor and `maxWindow` caps the sweep;
  `after` / `before` continue from a prior `Page`'s cursors. Distinct from the existing `planUntil`,
  which window-walks batch calls.
- Realtime **delays** on streamed itineraries: each `Chunk`'s legs carry the same estimated times and
  delay fields (`startDelay` / `endDelay` / `isRealtime` / `realtimeState`) as the one-shot `plan`.

### Changed

- **Realtime `delays` now resolves per trip instance** (breaking). A GTFS-RT delay is bound to a
  `(tripId, serviceDate)` instance, so `SpiderRealtime.delays` takes the service date each trip runs on —
  `delays(byServiceDate: Map<String, List<String>>)` (or `delays(tripIds, serviceDate)` for a single day) —
  and returns `TripDelays.groups: List<ServiceDateDelays>`, looked up per instance via
  `delayFor(tripId, serviceDate)`. `serviceDate` is the GTFS service date `YYYYMMDD`, taken from the plan
  leg (not the departure clock time — GTFS times can exceed 24:00). `pollDelays` mirrors the new
  signatures. The old flat `delays(tripIds)` is removed. Fixes cross-service-day delay bleed and the
  midnight-overlap ambiguity.

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
