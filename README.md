# spider-sdk-kotlin

Kotlin Multiplatform SDK for **Spider** — the managed transit API by Tiducto. Trip planning, stop search, and live realtime data behind one typed client that ships the exact queries the gateway allows and attaches auth for you.

## Supported targets

JVM · Android (compileSdk 36, minSdk 30) · iOS (`iosArm64`, `iosSimulatorArm64`, `iosX64`) · macOS (`macosArm64`, `macosX64`) · JS/IR (`browser`) · Wasm/JS (`browser`, `nodejs`)

## Install

Published to Maven Central — no credentials or private registry needed.

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("eu.tiducto:spider-sdk-kotlin-client:0.1.0")
}
```

A Kotlin Multiplatform consumer resolves the right platform artifact automatically.

## Quickstart

```kotlin
val client = SpiderClient(baseUrl = "https://your-env-slug.api.tiducto.eu", apiKey = "your-api-key")

when (val result = client.routing.plan(
    origin = Location.Stop("U123Z1"),
    destination = Location.Stop("U456Z2"),
    time = RouteTime.DepartAt(Clock.System.now()),  // leave now…
    searchWindow = 60.minutes,                       // …and scan the next 60 minutes
)) {
    is SpiderResult.Success -> result.data.edges.forEach { println(it.itinerary) }
    is SpiderResult.Error -> println(result.error)
}
```

The recommended query pins a time and a window — a departure time (`RouteTime.DepartAt`) or an arrival deadline (`RouteTime.ArriveBy`) together with `searchWindow` — rather than asking for _N_ results from "now". Widen the window for sparse or intercity routes, and page through adjacent windows with `planNext` / `planPrevious`.

Every call returns a `SpiderResult` you branch on before reading `data`; only a contract-version mismatch throws. Tune a surface with the optional config block, e.g. `SpiderClient(baseUrl, apiKey) { realtime { autoRetry { maxAttempts = 3 } } }`.

## What's in it

- **`client.routing`** — trip planning, departures, and single-trip lookups.
- **`client.stops`** — text/autocomplete search, geo queries (nearest / bounding box), and lookup by GTFS id.
- **`client.realtime`** — poll-based live vehicle positions, delays, and alerts (no push connections).

**Full API reference and guides → [docs.tiducto.eu](https://docs.tiducto.eu).** Product overview → [tiducto.eu](https://tiducto.eu).

## Contract

The wire contract (persisted GraphQL queries, routes, response shapes) is owned by [`tiducto/spider-contract`](https://github.com/tiducto/spider-contract). Its models are generated and committed into **`:client`** as `internal` wire types (the repo carries the classes, not the spec) and mapped to the public domain types. To refresh after the contract changes, run the **Generate contract module** GitHub workflow (or `scripts/generate-contract.sh` locally, which needs Docker). Maintainers: see [`RELEASING.md`](RELEASING.md).

## License

Apache-2.0 — see [LICENSE](LICENSE).
