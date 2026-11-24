# spider-sdk-kotlin

Kotlin Multiplatform SDK for **Spider** — the managed transit API by Tiducto. Trip planning, stop search, and live realtime data behind one typed client that speaks the published API contract and attaches auth for you.

## Supported targets

JVM · Android (compileSdk 36, minSdk 30) · iOS (`iosArm64`, `iosSimulatorArm64`, `iosX64`) · macOS (`macosArm64`, `macosX64`) · JS/IR (`browser`) · Wasm/JS (`browser`, `nodejs`)

## Install

Published to Maven Central — no credentials or private registry needed.

```kotlin
repositories {
    mavenCentral()
    maven("https://tiducto.github.io/spider-sdk-kotlin/")
}

dependencies {
    implementation("eu.tiducto:spider-sdk-kotlin-client:1.2.0")
}
```

The second repository is our own backup on GitHub Pages: it serves the same signed artifacts as Maven Central, and every release lands there first.

A Kotlin Multiplatform consumer resolves the right platform artifact automatically.

### Snapshots

Every merge to `main` is published as a snapshot to a separate repository, so you opt in on purpose:

```kotlin
repositories {
    maven("https://tiducto.github.io/spider-sdk-kotlin/snapshots/")
}

dependencies {
    implementation("eu.tiducto:spider-sdk-kotlin-client:1.10.1-SNAPSHOT")
}
```

The snapshot version is the next release: `version.properties`' `<contract>.<patch>-SNAPSHOT` while that version is unreleased, else the patch after it (released `1.10.0` → `1.10.1-SNAPSHOT`). Only the newest build of each snapshot version is kept, and a snapshot version disappears once its release is tagged.

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

Every call returns a `SpiderResult` you branch on before reading `data`; the SDK never throws for a failed call. Tune a surface with the optional config block, e.g. `SpiderClient(baseUrl, apiKey) { realtime { autoRetry { maxAttempts = 3 } } }`.

## What's in it

- **`client.routing`** — trip planning, departures, and single-trip lookups.
- **`client.stops`** — text/autocomplete search, geo queries (nearest / bounding box), and lookup by GTFS id.
- **`client.places`** — one search over stops, addresses, municipalities and points of interest.
- **`client.realtime`** — poll-based live vehicle positions, trip updates, and alerts (no push connections).

**Full API reference and guides → [docs.tiducto.eu](https://docs.tiducto.eu).** Product overview → [tiducto.eu](https://tiducto.eu).

## Contract

The wire contract (operations, request and response shapes) is owned by [`tiducto/spider-contract`](https://github.com/tiducto/spider-contract). Every operation sits on a versioned path, `/<surface>/v1/<operation>` (such as `/routing/v1/plan`), which the SDK calls for you. Its models are generated and committed into **`:client`** as `internal` wire types (the repo carries the classes, not the spec) and mapped to the public domain types. To refresh after the contract changes, run the **Pull contract updates** GitHub workflow (or `scripts/generate-contract.sh` locally, which needs Node.js and the GitHub CLI `gh`). Maintainers: see [`RELEASING.md`](RELEASING.md).

## License

Apache-2.0 — see [LICENSE](LICENSE).
