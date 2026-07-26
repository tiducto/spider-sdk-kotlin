# OTP GTFS GraphQL — reference notes

Saved subset of the OTP dev-2.x GTFS GraphQL API, scoped to what this client uses today (planConnection + supporting types). Source: https://docs.opentripplanner.org/api/dev-2.x/graphql-gtfs/

These notes exist so we don't have to re-fetch the upstream docs every time we extend a query or model. The queries and the OTP schema live in the contract (`spider-contract/src/routing/`, alongside `otp-schema.graphqls`), not here — update these notes whenever new fields are pulled in there.

## Endpoint

- HTTP path: `/otp/gtfs/v1` (e.g. `http://localhost:8080/otp/gtfs/v1`)
- Interactive explorer: `/graphiql`
- Activated by default; can be disabled via OTP config.

## planConnection (query)

GraphQL Cursor Connections-style itinerary search.

```graphql
planConnection(
  after: String
  before: String
  dateTime: PlanDateTimeInput
  destination: PlanLabeledLocationInput!
  first: Int
  flex: FlexRequest
  itineraryFilter: PlanItineraryFilterInput
  last: Int
  locale: Locale
  modes: PlanModesInput
  origin: PlanLabeledLocationInput!
  preferences: PlanPreferencesInput
  searchWindow: Duration
  via: [PlanViaLocationInput!]
): PlanConnection
```

Required: `origin`, `destination`. Everything else has sensible defaults (e.g. `dateTime` defaults to now, `modes` to all modes with WALK access/egress).

## Inputs (subset we use)

### PlanDateTimeInput

| Field | Type | Notes |
|---|---|---|
| `earliestDeparture` | `OffsetDateTime` | Itineraries won't depart before this. |
| `latestArrival` | `OffsetDateTime` | Itineraries won't arrive after this. |

Constraint: only one of the two should be set.

### PlanLabeledLocationInput

| Field | Type | Notes |
|---|---|---|
| `label` | `String` | Optional; echoed back on itineraries. |
| `location` | `PlanLocationInput!` | Required. |

### PlanLocationInput

Either a stop or coordinates:

| Field | Type | Notes |
|---|---|---|
| `stopLocation` | `PlanStopLocationInput` | `{ stopLocationId: String! }` |
| `coordinate` | `PlanCoordinateInput` | `{ latitude: Float!, longitude: Float! }` |

## Output types (subset we use)

### PlanConnection

| Field | Type |
|---|---|
| `edges` | `[PlanEdge]` |
| `pageInfo` | `PlanPageInfo!` |
| `routingErrors` | `[RoutingError!]!` |
| `searchDateTime` | `OffsetDateTime` |

### PlanEdge

| Field | Type |
|---|---|
| `cursor` | `String!` |
| `node` | `Itinerary!` |

### PlanPageInfo

| Field | Type |
|---|---|
| `startCursor` | `String` |
| `endCursor` | `String` |
| `hasNextPage` | `Boolean!` |
| `hasPreviousPage` | `Boolean!` |
| `searchWindowUsed` | `Duration` |

### Itinerary

| Field | Type | Notes |
|---|---|---|
| `start` | `OffsetDateTime` | Departure time with TZ. |
| `end` | `OffsetDateTime` | Arrival time with TZ. |
| `duration` | `Long!` | **Seconds.** Not an ISO Duration string. |
| `waitingTime` | `Long` | **Seconds.** |
| `numberOfTransfers` | `Int!` | Excludes stay-seated transfers. |
| `legs` | `[Leg]!` | |
| `walkDistance`, `walkTime`, `elevationGained`, `elevationLost`, `generalizedCost`, `accessibilityScore`, `fares` (deprecated), `emissionsPerPerson`, `systemNotices`, `arrivedAtDestinationWithRentedBicycle`, `startTime`/`endTime` (deprecated) | various | Not used by this client. |

### Leg

| Field | Type | Notes |
|---|---|---|
| `mode` | `Mode` | e.g. `WALK`, `BUS`, `RAIL`, `TRAM`, … |
| `start` | `LegTime!` | Non-null. |
| `end` | `LegTime!` | Non-null. |
| `from` | `Place!` | Non-null. |
| `to` | `Place!` | Non-null. |
| `route` | `Route` | Transit legs only. |
| `agency` | `Agency` | Transit legs only. |
| `trip` | `Trip` | Transit legs only. |
| `headsign` | `String` | Transit legs only. |
| `distance` | `Float` | Meters. |
| `duration` | `Float` | **Seconds.** Not an ISO Duration string. |
| `transitLeg` | `Boolean` | |
| `realTime`, `realtimeState`, `rentedBike`, `walkingBike`, `interlineWithPreviousLeg`, `accessibilityScore`, `stopCalls`, `legGeometry`, `steps`, `nextLegs`, `previousLegs`, `alerts`, fare/booking fields | various | Not used yet. |

### LegTime

| Field | Type |
|---|---|
| `scheduledTime` | `OffsetDateTime!` |
| `estimated` | `RealTimeEstimate` |

### RoutingError

| Field | Type | Notes |
|---|---|---|
| `code` | `RoutingErrorCode!` | Enum. |
| `description` | `String!` | Human-readable. Clients should translate based on `code`, not display this raw. |
| `inputField` | `InputField` | Optional — which input the client should adjust. |

`RoutingErrorCode` values seen in the wild: `LOCATION_NOT_FOUND`, `NO_STOPS_IN_RANGE`, `NO_TRANSIT_CONNECTION`, `NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW`, `OUTSIDE_BOUNDS`, `OUTSIDE_SERVICE_PERIOD`, `SYSTEM_ERROR`, `WALKING_BETTER_THAN_TRANSIT`.

`InputField` values: `DATE_TIME`, `FROM`, `TO`.

## stoptimesForPatterns (Stop field)

Used by `client.routing.departures(...)`. Lives on `type Stop`; returns upcoming departures grouped by pattern.

```graphql
Stop.stoptimesForPatterns(
  numberOfDepartures: Int,
  startTime: Long,         # epoch seconds; defaults to now
  timeRange: Int,          # seconds window; default ~86400
  omitNonPickups: Boolean
): [StoptimesInPattern!]!
```

### StoptimesInPattern

| Field | Type |
|---|---|
| `pattern` | `Pattern!` |
| `stoptimes` | `[Stoptime!]!` |

### Pattern (fields we use)

| Field | Type | Notes |
|---|---|---|
| `code` | `String!` | Pattern id. |
| `headsign` | `String` | Vehicle headsign. |
| `directionId` | `Int` | 0, 1, or -1 (irrelevant). |
| `route` | `Route!` | Has `shortName`, `longName`. |

### Stoptime

| Field | Type | Notes |
|---|---|---|
| `serviceDay` | `Long` | **Epoch seconds** for the start of the GTFS service day (00:00 of the service date, in OTP's timezone). |
| `scheduledArrival` | `Int` | **Seconds since service day start.** Can exceed 86400 for after-midnight trips. |
| `scheduledDeparture` | `Int` | Same encoding as `scheduledArrival`. |
| `realtimeArrival` | `Int` | Same encoding, optional. |
| `realtimeDeparture` | `Int` | Same encoding, optional. |
| `realtime` | `Boolean` | Whether realtime data was applied. |
| `realtimeState` | `RealtimeState` | `SCHEDULED`, `UPDATED`, `CANCELED`, `ADDED`, `MODIFIED`. |
| `headsign` | `String` | May differ from pattern headsign on a per-trip basis. |
| `trip` | `Trip` | `Trip.gtfsId` is the canonical trip identifier. |

**Absolute time:** `Instant.fromEpochSeconds(serviceDay + scheduledDeparture)`. The client does this conversion so callers see a proper `Instant`, not the OTP wire encoding.

## Scalars

- `OffsetDateTime` — ISO-8601 with timezone offset, e.g. `2026-05-13T14:30:00+02:00`.
- `Duration` — ISO-8601 duration, e.g. `PT30M`, `PT2H`. **Note:** despite the name, `Itinerary.duration`, `Itinerary.waitingTime`, and `Leg.duration` are NOT this scalar — they are `Long`/`Float` seconds. Only `PlanPageInfo.searchWindowUsed` and the `searchWindow` query arg use the actual `Duration` scalar.
- `Long` — 64-bit integer. Not built into Apollo; we map to `kotlin.Long`.
