package eu.tiducto.spider.client

/**
 * The single wire-contract version this SDK speaks. **One version for the whole pack** — routing,
 * stop search, and Realtime — because there is exactly one [SpiderClient].
 *
 * [VERSION] is stamped from the contract itself: `scripts/generate-contract.sh` reads the spec's
 * `info.version` into [CONTRACT_VERSION] (a generated file), so the wire version this SDK claims always
 * tracks the contract it was generated from — it cannot drift.
 *
 * Every request carries [VERSION] in the [HEADER]. It is informational (telemetry): the SDK never checks
 * a version the gateway declares back.
 */
internal object SpiderContract {

    /**
     * The wire-contract version, shared by all three surfaces — [CONTRACT_VERSION], stamped from the
     * contract's `info.version`. The published artifact version derives from the same value
     * (`<contract.version>.<sdk.patch>`), so contract, docs, and SDK share one lineage.
     */
    const val VERSION: String = CONTRACT_VERSION

    /** Sent on every request. */
    const val HEADER: String = "x-spider-contract-version"
}
