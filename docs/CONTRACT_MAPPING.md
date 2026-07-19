# Production contract ↔ spider-client SDK map

How this SDK maps onto the **production Spider gateway** (Kong), what currently mismatches, and
what the **contract must register/add** to serve the SDK. Direction is **contract ← SDK**: the SDK's
queries are canonical; the contract is (re)created to support everything the SDK needs.

> **Live-validated end-to-end** against the production gateway (`https://api.transitapi.eu`, env 7 =
> hikari/production, Brno GTFS) on 2026-07-14, from the real app on-device: Plan, Departures, and Trip
> all return `200` with real data through the persisted-query transport; Stops (`/stops/search`) `200`;
> unknown persisted id → 403; no key → 401. The SDK now speaks production over a **plain Ktor +
> kotlinx.serialization** client — **Apollo has been removed** (see §2).

## What used to mismatch (all resolved)

The SDK originally spoke the `local/` Caddy stack (raw GraphQL at `/otp/gtfs/v1`, `Bearer` auth, no
project/env), which the production Kong gateway rejected on these counts. All are now fixed:

| # | Concern | Production gateway | Fixed to |
|---|---------|-------------------------------|-----------|
| 1 | **Auth** | `apikey: <key>` header (Kong key-auth) | `apikey` header in both clients |
| 2 | **OTP transport** | Persisted-query POST `{"id":"<sha256>","variables":{…}}` | plain Ktor POST of `{id,variables}` (no Apollo) |
| 3 | **OTP routes** | Per-op `/{project}/{env}/otp/plan\|departures\|trip` | per-op URL from `PersistedQueries` |
| 4 | **project/env** | Numeric ids in the path (`/9/7/…`) | folded into the customer's `baseUrl` |
| 5 | **Stops** | `/{project}/{env}/stops/search`, `apikey`, Kong injects Meili key | `MeiliClient` posts `$baseUrl/stops/search` + `apikey` |
| 6 | **Realtime** | `/{project}/{env}/realtime/*` (deployed) | `RealtimeClient` GETs `$baseUrl/realtime/{vehicles,vehicles/by-trip/{id},delays,alerts}` + `apikey` |

## Contract module layout & versioning (all three surfaces)

The `:contract` module carries the wire shapes, split by ownership:

| Package | Surface | Owner | Drift test |
|---------|---------|-------|-----------|
| `contract.routing` | OTP | **generated** from spider-contract `openapi.json` (`generate-contract.sh`, wiped/rewritten on regen) | `OtpWireContractTest` |
| `contract.meili` | Meili stop search | **hand-written**, mirrors the `stops_env_{envId}` index (`seed-stops.sh`) | `MeiliWireContractTest` |
| `contract.realtime` | GTFS-RT | **hand-written**, mirrors the realtime gateway serializer | `RealtimeWireContractTest` |

The hand-written packages sit outside `routing/` on purpose — the generator's `rm -rf` only touches
`routing/`. If a surface later moves to codegen, delete its hand-written package and let it regenerate.

**One version for the whole pack.** There is a single `SpiderContract.VERSION` (in `:client`) covering
OTP + Meili + Realtime, sent on every request as `x-spider-contract-version` and exposed as
`SpiderClient.contractVersion`. It is the honest REST analog of OTP's persisted-query id: a *declared*
version, not a per-operation content-hash. **Enforcement is fail-fast:** `ContractGuard` reads the
version the gateway declares on each response and, on an incompatible MAJOR, throws
`SpiderContractMismatchError` — a `kotlin.Error` that bypasses `SpiderResult` and crashes, because an
incompatible contract is a build/deploy error, not a recoverable per-call failure. It is **dormant**
until the gateway echoes the header (Kong ignores it today), then enforces automatically.

## Persisted-query binding (the core of the OTP contract)

Enforcement keys on `sha256(canonicalize(queryText))` where `canonicalize` = `\r\n`→`\n`, `\r`→`\n`,
then `trim` (`ContractHashing` in spider-services). The contract must register the SDK's **exact**
`.graphql` text so the ids below match what the SDK sends.

| Operation | SDK document | Prod route | Persisted id (sha256 of the doc) |
|-----------|--------------|------------|----------------------------------|
| Plan | `graphql/PlanConnection.graphql` | `/{p}/{e}/otp/plan` | `f19608964d423831b485ccc878cb25eff56c720585d4423ee617c864e2b3102e` |
| Departures | `graphql/StopDepartures.graphql` | `/{p}/{e}/otp/departures` | `70a644fe3c6b2cbf5b2d70cef8230c1428bea6357ae1766772162d86469563d0` |
| Trip | `graphql/TripQuery.graphql` | `/{p}/{e}/otp/trip` *(new — see below)* | `e8959a8d47a8e8437ee3ec740cd9c3e28bd401efdd236dde0502559daea53920` |

These differ from the contract's **current** ids (Plan `f694c4a3…`, Departures `ba91bb2e…`) — the
documents have diverged; the current contract queries are simpler/older. Adopt the SDK's.

## What the contract must add / change (contract ← SDK)

1. **Adopt the SDK's Plan & Departures queries verbatim.** The SDK's `PlanConnection` carries fields
   the app needs that the current contract lacks: `cursor`/`pageInfo` (pagination), `routingErrors`,
   `searchDateTime`, `waitingTime`, `accessibilityScore`, `wheelchairBoarding`, `bikesAllowed`, and
   `via`/`before`/`after` inputs. Register the SDK text → id becomes `f19608…`.
2. **Departures query shape decision.** The SDK uses **`stoptimesWithoutPatterns`** (flat, + a
   `station` fallback via fragment); the current exposed surface uses **`stoptimesForPatterns`**
   (grouped). This needs a decision. If we adopt the SDK's:
   - spider-services `BrunoCollectionReader.detectOperation` must also recognize
     `stoptimesWithoutPatterns` (today it only maps `stoptimesForPatterns` → DEPARTURES, and would
     error on the SDK's query).
   - OTP's allowed-fields / `maxNumberOfResultFields` cap must permit the `station` fallback.
3. **Expose a third operation: `Trip`.** The SDK has `trip()` (trip detail: stoptimesForDate +
   geometry) for the app's trip screens, but the exposed OTP surface is "exactly two operations"
   (plan, departures). Adding trip means: a new Kong route `/{p}/{e}/otp/trip`, a persisted-query
   entry, operation detection for the `trip` root field, and OTP config allowing those fields. This
   is a genuine surface expansion (spider-services docs are strict about the closed set) — worth it
   for trip detail, but a deliberate product/security call.
4. **Stops index doc fields must match.** SDK `MeiliStop` reads `gtfsId, name, lat, lon,
   country, region, district, city, suburb`. Keep the `stops_env_{envId}` builder in sync
   (`seed-stops.sh` is the reference; cross-repo contract).

## How the SDK transport works now (spider-client)

The OTP client (`OtpClient.kt`) is a plain **Ktor** `HttpClient` + **kotlinx.serialization**. For each
operation it:

1. builds the typed request variables (`*Variables`/`*Input` classes from the generated `:contract`
   module),
2. POSTs `{"id":"<persisted-id>","variables":{…}}` to `$baseUrl/otp/<plan|departures|trip>` with the
   `apikey` header — the id + route come from the `PersistedQueries` registry (the SDK owns the ids;
   they are the sha256 of its `.graphql` docs),
3. parses the standard GraphQL `{data, errors}` envelope into the generated `:contract` payload models
   (`PlanConnectionData`, `StopDeparturesData`, `TripData`, …), then maps them to the domain types
   (`Route`, `Departure`, `TripDetails`).

The wire models are generated from `tiducto/spider-contract`'s `openapi.json` into the `:contract`
module (`scripts/generate-contract.sh` / the **Generate contract module** workflow) and committed. Their
enums tolerate unrecognized upstream values (`enumUnknownDefaultCase`) so a mode/state OTP adds later
maps to `UNKNOWN_DEFAULT_OPEN_API` instead of failing the parse. The `.graphql` files under
`src/commonMain/graphql/` are **not compiled** — they're kept as the canonical query documents the
persisted ids are hashed from (the contract registers the same text).

Stops go through `MeiliClient` → `$baseUrl/stops/search` + `apikey` (Kong rewrites to the env's Meili
index and injects the Meili key). project/env are folded into the customer's `baseUrl` (e.g.
`https://api.transitapi.eu/9/7`); no separate params.

## Status — done

- ✅ Auth → `apikey` (both clients).
- ✅ OTP transport → plain Ktor persisted-query POST; **Apollo dependency + codegen removed**.
- ✅ Per-op routes + Stops route.
- ✅ Contract registers the SDK's exact Plan/Departures/Trip documents (ids above); Kong reprovisioned.
- ✅ `Trip` exposed as a third operation (deliberate surface expansion).
- ✅ Live-validated on env 7 from the app (Plan/Departures/Trip/Stops = 200; 403 unknown id; 401 no key).
- ⏭️ Local `local/` Caddy stack still mirrors the *old* raw-GraphQL shape — a persisted-query shim (or
  target-gated transport) would let it mirror prod. Not needed for the remote; left as-is.
