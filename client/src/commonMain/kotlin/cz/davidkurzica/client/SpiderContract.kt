package cz.davidkurzica.client

/**
 * The single wire-contract version this SDK speaks. **One version for the whole pack** — routing,
 * stop search, and Realtime — because there is exactly one [SpiderClient]. Bump [VERSION]
 * whenever *any* surface's wire shape changes; every surface moves together.
 *
 * This spine is deliberately hand-written and lives in `:client`, not in the generated `:contract`
 * module: the contract *version* and its *enforcement* are the SDK's own concern, so they stay
 * independent of however the model classes happen to be produced (openapi-generator today, possibly a
 * custom generator later). A generator only ever emits data classes; it never touches this file.
 *
 * Every request carries [VERSION] in the [HEADER]. [ContractGuard] enforces it against whatever the
 * gateway declares on the way back — see there for the fail-fast philosophy.
 */
internal object SpiderContract {

    /**
     * The wire-contract version, shared by all three surfaces. Independent of the Maven artifact
     * version (`1.0.0-SNAPSHOT`) — this tracks the *wire shapes*, not the release. Compatibility is
     * by MAJOR component (see [ContractGuard]): additive minor/patch changes are tolerated by the
     * clients' `ignoreUnknownKeys` decoders; a MAJOR bump is a breaking wire change.
     */
    const val VERSION: String = "1.0.0"

    /** Sent on every request; read back off every response once the gateway starts declaring it. */
    const val HEADER: String = "x-spider-contract-version"
}

/**
 * Fail-fast guard for the wire contract. A version mismatch means **this SDK build is fundamentally
 * incompatible with the deployment it is talking to** — a build/deploy error, not a recoverable
 * runtime failure. So it is raised as [SpiderContractMismatchError] (a [kotlin.Error], not an
 * [Exception]) and deliberately **bypasses [SpiderResult]**: the per-call `runCatching` blocks only
 * catch [Exception], so this propagates straight through and crashes. That is the point — an
 * incompatible pairing must surface loudly in testing/rollout, never degrade silently to an
 * `Error` result the caller might swallow.
 *
 * **Dormant until the gateway participates.** Today the gateway does not echo [SpiderContract.HEADER],
 * so [check] is a no-op ([declaredByGateway] is null). The moment the gateway starts returning the
 * version it enforces, this guard activates automatically — no SDK change required.
 */
internal object ContractGuard {

    /**
     * @param declaredByGateway the value of [SpiderContract.HEADER] on the response, or null if the
     *   gateway did not declare one (the current, version-unaware gateway → no-op).
     * @throws SpiderContractMismatchError if the gateway declares an incompatible MAJOR version.
     */
    fun check(declaredByGateway: String?) {
        if (declaredByGateway == null) return
        if (!compatible(SpiderContract.VERSION, declaredByGateway)) {
            throw SpiderContractMismatchError(expected = SpiderContract.VERSION, actual = declaredByGateway)
        }
    }

    // Same MAJOR = compatible. Everything the SDK can't parse (a differently-shaped version string)
    // reads as a different major and therefore incompatible — fail closed.
    private fun compatible(sdk: String, gateway: String): Boolean =
        sdk.substringBefore('.') == gateway.substringBefore('.')
}

/**
 * Thrown when the gateway declares a wire-contract version incompatible with [SpiderContract.VERSION].
 *
 * Intentionally a [kotlin.Error]: it signals an unrecoverable build/deploy incompatibility that the
 * app is **not** expected to catch and continue from. It is never wrapped in a [SpiderResult] — a
 * failed call returns [SpiderResult.Error]; an incompatible *contract* crashes. If the app catches it
 * at the top level at all, it should be to show a hard "please update" state and log, not to retry.
 */
class SpiderContractMismatchError internal constructor(
    val expected: String,
    val actual: String,
) : Error("Spider contract mismatch: this SDK speaks $expected but the gateway declared $actual")
