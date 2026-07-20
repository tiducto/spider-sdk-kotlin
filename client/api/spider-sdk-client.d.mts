type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
/** @deprecated  */
export declare const initHook: {
    get(): any;
};
export declare class FeedFreshness {
    private constructor();
    get feedTimestampEpochMs(): Nullable<number>;
    get staleSeconds(): Nullable<number>;
}
export declare namespace FeedFreshness {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => FeedFreshness;
    }
}
export declare class LiveVehicle {
    private constructor();
    get tripId(): Nullable<string>;
    get routeId(): Nullable<string>;
    get vehicleId(): Nullable<string>;
    get label(): Nullable<string>;
    get latitude(): Nullable<number>;
    get longitude(): Nullable<number>;
    get bearing(): Nullable<number>;
    get speed(): Nullable<number>;
    get stopId(): Nullable<string>;
    get currentStatus(): Nullable<string>;
    get occupancy(): Nullable<string>;
    get timestampEpochMs(): Nullable<number>;
}
export declare namespace LiveVehicle {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => LiveVehicle;
    }
}
export declare class LiveVehicleUpdate {
    private constructor();
    get vehicle(): Nullable<LiveVehicle>;
    get freshness(): FeedFreshness;
}
export declare namespace LiveVehicleUpdate {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => LiveVehicleUpdate;
    }
}
export declare class VehiclePositions {
    private constructor();
    get vehicles(): Array<LiveVehicle>;
    get missing(): Array<string>;
    get freshness(): FeedFreshness;
}
export declare namespace VehiclePositions {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => VehiclePositions;
    }
}
export declare class StopTimeUpdate {
    private constructor();
    get stopId(): Nullable<string>;
    get stopSequence(): Nullable<number>;
    get arrivalDelay(): Nullable<number>;
    get departureDelay(): Nullable<number>;
    get scheduleRelationship(): Nullable<string>;
}
export declare namespace StopTimeUpdate {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => StopTimeUpdate;
    }
}
export declare class TripDelay {
    private constructor();
    get tripId(): Nullable<string>;
    get routeId(): Nullable<string>;
    get delaySeconds(): Nullable<number>;
    get scheduleRelationship(): Nullable<string>;
    get stopTimeUpdates(): Array<StopTimeUpdate>;
}
export declare namespace TripDelay {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => TripDelay;
    }
}
export declare class TripDelays {
    private constructor();
    get delays(): Array<TripDelay>;
    get missing(): Array<string>;
    get freshness(): FeedFreshness;
    delayFor(tripId: string): Nullable<TripDelay>;
}
export declare namespace TripDelays {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => TripDelays;
    }
}
export declare class AlertActivePeriod {
    private constructor();
    get startEpochMs(): Nullable<number>;
    get endEpochMs(): Nullable<number>;
}
export declare namespace AlertActivePeriod {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => AlertActivePeriod;
    }
}
export declare class AlertInformedEntity {
    private constructor();
    get agencyId(): Nullable<string>;
    get routeId(): Nullable<string>;
    get tripId(): Nullable<string>;
    get stopId(): Nullable<string>;
}
export declare namespace AlertInformedEntity {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => AlertInformedEntity;
    }
}
export declare class ServiceAlert {
    private constructor();
    get id(): Nullable<string>;
    get cause(): Nullable<string>;
    get effect(): Nullable<string>;
    get severityLevel(): Nullable<string>;
    get headerText(): Nullable<string>;
    get descriptionText(): Nullable<string>;
    get url(): Nullable<string>;
    get activePeriods(): Array<AlertActivePeriod>;
    get informedEntities(): Array<AlertInformedEntity>;
}
export declare namespace ServiceAlert {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ServiceAlert;
    }
}
export declare class ServiceAlerts {
    private constructor();
    get alerts(): Array<ServiceAlert>;
    get freshness(): FeedFreshness;
}
export declare namespace ServiceAlerts {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => ServiceAlerts;
    }
}
export declare class SpiderResult<T> {
    private constructor();
    get isSuccess(): boolean;
    get data(): Nullable<T>;
    get error(): Nullable<SpiderError>;
}
export declare namespace SpiderResult {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new <T>() => SpiderResult<T>;
    }
}
export declare class SpiderError {
    private constructor();
    get code(): string;
    get message(): Nullable<string>;
    get httpStatus(): Nullable<number>;
}
export declare namespace SpiderError {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SpiderError;
    }
}
export declare class RouteLocation {
    private constructor();
    get kind(): string;
    get stopId(): Nullable<string>;
    get lat(): Nullable<number>;
    get lon(): Nullable<number>;
}
export declare namespace RouteLocation {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => RouteLocation;
    }
}
export declare function stopLocation(id: string): RouteLocation;
export declare function coordinateLocation(lat: number, lon: number): RouteLocation;
export declare class LatLon {
    private constructor();
    get lat(): number;
    get lon(): number;
}
export declare namespace LatLon {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => LatLon;
    }
}
export declare class Leg {
    private constructor();
    get mode(): Nullable<string>;
    get startScheduled(): string;
    get endScheduled(): string;
    get fromName(): Nullable<string>;
    get toName(): Nullable<string>;
    get routeShortName(): Nullable<string>;
    get routeLongName(): Nullable<string>;
    get headsign(): Nullable<string>;
    get distanceMeters(): Nullable<number>;
    get durationSeconds(): Nullable<number>;
    get tripGtfsId(): Nullable<string>;
    get bikesAllowed(): Nullable<string>;
    get accessibilityScore(): Nullable<number>;
    get fromWheelchair(): Nullable<string>;
    get toWheelchair(): Nullable<string>;
    get geometry(): Array<LatLon>;
}
export declare namespace Leg {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => Leg;
    }
}
export declare class Itinerary {
    private constructor();
    get start(): Nullable<string>;
    get end(): Nullable<string>;
    get durationSeconds(): number;
    get waitingTimeSeconds(): Nullable<number>;
    get numberOfTransfers(): number;
    get accessibilityScore(): Nullable<number>;
    get legs(): Array<Leg>;
}
export declare namespace Itinerary {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => Itinerary;
    }
}
export declare class RouteEdge {
    private constructor();
    get cursor(): string;
    get itinerary(): Itinerary;
}
export declare namespace RouteEdge {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => RouteEdge;
    }
}
export declare class RoutePageInfo {
    private constructor();
    get startCursor(): Nullable<string>;
    get endCursor(): Nullable<string>;
    get hasNextPage(): boolean;
    get hasPreviousPage(): boolean;
    get searchWindowUsed(): Nullable<string>;
}
export declare namespace RoutePageInfo {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => RoutePageInfo;
    }
}
export declare class RoutingError {
    private constructor();
    get code(): string;
    get description(): string;
    get inputField(): Nullable<string>;
}
export declare namespace RoutingError {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => RoutingError;
    }
}
export declare class Route {
    private constructor();
    get edges(): Array<RouteEdge>;
    get pageInfo(): RoutePageInfo;
    get routingErrors(): Array<RoutingError>;
    get searchDateTime(): Nullable<string>;
}
export declare namespace Route {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => Route;
    }
}
export declare class Departure {
    private constructor();
    get scheduledTimeEpochMs(): number;
    get realtimeTimeEpochMs(): Nullable<number>;
    get isRealtime(): boolean;
    get realtimeState(): Nullable<string>;
    get headsign(): Nullable<string>;
    get tripGtfsId(): Nullable<string>;
    get routeShortName(): Nullable<string>;
    get routeLongName(): Nullable<string>;
    get mode(): Nullable<string>;
}
export declare namespace Departure {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => Departure;
    }
}
export declare class TripStop {
    private constructor();
    get gtfsId(): string;
    get name(): string;
    get lat(): Nullable<number>;
    get lon(): Nullable<number>;
    get scheduledArrivalEpochMs(): Nullable<number>;
    get scheduledDepartureEpochMs(): Nullable<number>;
    get realtimeArrivalEpochMs(): Nullable<number>;
    get realtimeDepartureEpochMs(): Nullable<number>;
    get isRealtime(): boolean;
    get wheelchairBoarding(): Nullable<string>;
}
export declare namespace TripStop {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => TripStop;
    }
}
export declare class TripDetails {
    private constructor();
    get gtfsId(): string;
    get routeShortName(): Nullable<string>;
    get routeLongName(): Nullable<string>;
    get mode(): Nullable<string>;
    get headsign(): Nullable<string>;
    get directionId(): Nullable<string>;
    get bikesAllowed(): Nullable<string>;
    get stops(): Array<TripStop>;
    get geometry(): Array<LatLon>;
}
export declare namespace TripDetails {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => TripDetails;
    }
}
export declare class SpiderClient {
    constructor(baseUrl: string, apiKey: string);
    get routing(): SpiderRouting;
    get stops(): SpiderStops;
    get realtime(): SpiderRealtime;
    get contractVersion(): string;
}
export declare namespace SpiderClient {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SpiderClient;
    }
}
export declare class SpiderRouting {
    private constructor();
    plan(from: RouteLocation, to: RouteLocation, departAtEpochMs?: Nullable<number>, first?: number): Promise<SpiderResult<Route>>;
    planArriveBy(from: RouteLocation, to: RouteLocation, arriveByEpochMs: number, first?: number): Promise<SpiderResult<Route>>;
    nextPage(route: Route, first?: number): Promise<Nullable<SpiderResult<Route>>>;
    previousPage(route: Route, first?: number): Promise<Nullable<SpiderResult<Route>>>;
    departures(stopId: string, numberOfDepartures?: number, startTimeEpochMs?: Nullable<number>, timeRangeSeconds?: number): Promise<SpiderResult<Array<Departure>>>;
    trip(tripId: string, serviceDate?: Nullable<string>): Promise<SpiderResult<TripDetails>>;
}
export declare namespace SpiderRouting {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SpiderRouting;
    }
}
export declare class SpiderStops {
    private constructor();
    search(nameQuery?: Nullable<string>, country?: Nullable<string>, region?: Nullable<string>, district?: Nullable<string>, city?: Nullable<string>, suburb?: Nullable<string>): Promise<SpiderResult<Array<Stop>>>;
}
export declare namespace SpiderStops {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SpiderStops;
    }
}
export declare class SpiderRealtime {
    private constructor();
    vehicles(tripIds: Array<string>): Promise<SpiderResult<VehiclePositions>>;
    vehicleForTrip(tripId: string): Promise<SpiderResult<LiveVehicleUpdate>>;
    delays(tripIds: Array<string>): Promise<SpiderResult<TripDelays>>;
    alerts(): Promise<SpiderResult<ServiceAlerts>>;
}
export declare namespace SpiderRealtime {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => SpiderRealtime;
    }
}
export declare class Stop {
    private constructor();
    get gtfsId(): string;
    get name(): string;
    get lat(): Nullable<number>;
    get lon(): Nullable<number>;
    get wheelchairBoarding(): Nullable<string>;
    get country(): Nullable<string>;
    get region(): Nullable<string>;
    get district(): Nullable<string>;
    get city(): Nullable<string>;
    get suburb(): Nullable<string>;
}
export declare namespace Stop {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => Stop;
    }
}