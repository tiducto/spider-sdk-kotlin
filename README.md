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

### Kotlin (Gradle, GitHub Packages Maven)

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
    implementation("eu.tiducto:spider-sdk-kotlin-client:0.1.0")
}
```

### JavaScript / TypeScript (GitHub Packages npm)

The JS target ships as a typed npm package, `@tiducto/spider-sdk-client`, published by the
**publish npm** workflow (manual dispatch). Point npm at GitHub Packages for the `@tiducto` scope:

```
# .npmrc
@tiducto:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN} # a PAT with read:packages
```

```
npm install @tiducto/spider-sdk-client
```

```ts
import { SpiderClient, Location } from '@tiducto/spider-sdk-client'

const client = new SpiderClient('https://brno.api.transitapi.eu', apiKey)
const res = await client.routing.plan(
  Location.coordinate(49.19, 16.61),
  Location.stop('U1146N1'),
)
if (res.isSuccess) res.data!.edges.forEach((e) => console.log(e.itinerary))
```

The JS surface is an export-safe facade (the `eu.tiducto.spider.client.js` package): sealed types become
factory functions, `SpiderResult` becomes `{ isSuccess, data, error }`, times are epoch-millis
`number`s, enums are their name strings, and suspend functions return Promises. The names match the
Kotlin API but the shapes are JS-friendly; the rich Kotlin API is unchanged for JVM/Android/Apple
consumers. An origin/destination is built with `Location.coordinate(lat, lon)` or `Location.stop(id)`,
mirroring the Kotlin `Location.Coordinate` / `Location.Stop`. (`Location` is exposed by a small
hand-written facade shim in the npm package — `npm/index.mjs` — because Kotlin/JS `@JsExport` can't
hoist companion factories to bare statics on the exported class.)

## Usage

```kotlin
val client = SpiderClient(baseUrl = "https://brno.api.transitapi.eu", apiKey = "spk_…") {
    install(Routing)
    install(Stops)
    install(Realtime)
}

when (val result = client.routing.plan(
    origin = Location.Stop("U1146N175"),
    destination = Location.Stop("U1378N2834"),
)) {
    is SpiderResult.Success -> result.data.edges.forEach { println(it.itinerary) }
    is SpiderResult.Error -> println(result.error)
}
```

Also available once installed: `client.stops.search { filter { name eq "Hlavní" } }` and
`client.realtime.vehicleForTrip(tripId)`.

## Contract

The wire contract (persisted GraphQL queries, routes, response shapes) is owned by
[`tiducto/spider-contract`](https://github.com/tiducto/spider-contract). Its `dist/routing-openapi.json` is compiled
into typed Kotlin models in the **`:contract`** module — the generated classes are committed (the module
carries the classes, not the spec, and has no codegen in its build). `:client` consumes them internally
and maps them to its domain types. See [`docs/CONTRACT_MAPPING.md`](docs/CONTRACT_MAPPING.md).

To refresh the models after the contract changes, run the **Generate contract module** GitHub workflow
(manual dispatch) — it pulls the spec, regenerates, and opens a PR. Locally: `scripts/generate-contract.sh`
(needs Docker). The models use typed enums that tolerate unrecognized upstream values (an
`UNKNOWN` case) rather than failing to parse.
