// Types for the hand-written facade (see index.mjs). Re-exports the generated surface and adds the
// clean `Location`: a value carrying the `coordinate`/`stop` factories, plus a type alias for the
// opaque handle so `plan(origin: Location, destination: Location, …)` still type-checks.
import type { SpiderLocation } from './spider-sdk-client.mjs';

export * from './spider-sdk-client.mjs';

export type Location = SpiderLocation;
export declare const Location: {
  coordinate(latitude: number, longitude: number): SpiderLocation;
  stop(id: string): SpiderLocation;
};
