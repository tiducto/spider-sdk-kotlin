# spider-sdk-kotlin

Kotlin Multiplatform SDK for the Spider transit API — trip planning, stop search, and live realtime data behind one typed client.

## Supported targets

- JVM
- Android (compileSdk 36, minSdk 30)
- iOS: `iosArm64`, `iosSimulatorArm64`, `iosX64`
- macOS: `macosArm64`, `macosX64`
- JS (IR): `browser`, `nodejs`
- Wasm/JS: `browser`, `nodejs`

## Install

Published to GitHub Packages. Add the repository and dependency:

```kotlin
repositories {
    maven("https://maven.pkg.github.com/tiducto/spider-sdk-kotlin") {
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN") // a PAT with read:packages
        }
    }
}

dependencies {
    implementation("eu.tiducto:spider-sdk-kotlin-client:1.0.0-SNAPSHOT")
}
```

## Usage

```kotlin
val client = SpiderClient(baseUrl = "https://api.example.eu/{project}/{env}", apiKey = "spk_…") {
    install(Routing)
    install(Stops)
    install(Realtime)
}

when (val result = client.routing.route(
    from = RouteLocation.StopId("U1146N175"),
    to = RouteLocation.StopId("U1378N2834"),
)) {
    is SpiderResult.Success -> result.data.edges.forEach { println(it.itinerary) }
    is SpiderResult.Error -> println(result.error)
}
```

Also available once installed: `client.stops.search { filter { name eq "Hlavní" } }` and
`client.realtime.vehicleForTrip(tripId)`.

## Contract

The wire contract (persisted GraphQL queries, routes, response shapes) is owned by
[`tiducto/spider-contract`](https://github.com/tiducto/spider-contract). Its `openapi.json` is compiled
into typed Kotlin models in the **`:contract`** module — the generated classes are committed (the module
carries the classes, not the spec, and has no codegen in its build). `:client` consumes them internally
and maps them to its domain types. See [`docs/CONTRACT_MAPPING.md`](docs/CONTRACT_MAPPING.md).

To refresh the models after the contract changes, run the **Generate contract module** GitHub workflow
(manual dispatch) — it pulls the spec, regenerates, and opens a PR. Locally: `scripts/generate-contract.sh`
(needs Docker). The models use typed enums that tolerate unrecognized upstream values (an
`UNKNOWN` case) rather than failing to parse.
