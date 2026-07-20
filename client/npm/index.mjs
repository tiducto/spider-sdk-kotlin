// Hand-written facade layer for the published @tiducto/spider-sdk-client package — the package entry
// point (package.json main/module), sitting in front of the Kotlin/JS bundle.
//
// Kotlin/JS @JsExport can't hoist companion (or object) members to bare statics on an exported class:
// the generated bundle only offers `SpiderLocation.Companion.coordinate(...)`. This shim re-exports the
// full generated surface and adds a clean `Location` object whose `coordinate`/`stop` delegate to those
// factories, so TS/JS consumers write `Location.coordinate(lat, lon)` / `Location.stop(id)` — matching
// the Kotlin API's `Location.Coordinate` / `Location.Stop` and the docs.
import { SpiderLocation } from './spider-sdk-client.mjs';

export * from './spider-sdk-client.mjs';

export const Location = {
  coordinate: (latitude, longitude) => SpiderLocation.Companion.coordinate(latitude, longitude),
  stop: (id) => SpiderLocation.Companion.stop(id),
};
