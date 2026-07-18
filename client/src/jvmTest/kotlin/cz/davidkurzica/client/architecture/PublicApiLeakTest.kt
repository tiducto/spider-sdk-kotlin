package cz.davidkurzica.client.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.type.KoTypeDeclaration
import kotlin.test.Test

/**
 * Guards the public API of `:client`. Every type that appears in a
 * public function or property signature must live under one of the
 * [allowedPackagePrefixes] — adopting a new dependency that ends up in the
 * public surface fails this test until either the type is wrapped behind a
 * domain type or the new package is explicitly allowed here.
 *
 * [deniedPackagePrefixes] overrides the allowlist for specific subpackages
 * (e.g. a future codegen output package under `cz.davidkurzica.client.`),
 * so generated wire types can stay denied even though their parent package
 * is allowed. Empty today — the SDK hand-writes its wire DTOs as `private`.
 */
class PublicApiLeakTest {

    private val allowedPackagePrefixes = listOf(
        "kotlin.",
        "kotlinx.",
        "cz.davidkurzica.client.",
    )

    private val deniedPackagePrefixes = emptyList<String>()

    @Test
    fun `public API only references allowed package prefixes`() {
        val scope = Konsist.scopeFromProduction(moduleName = "client", sourceSetName = "commonMain")
        val violations = mutableListOf<String>()

        scope.files
            .filterNot { it.path.contains("/build/generated/") }
            .forEach { file ->
                val where = "${file.name}:"

                // Star imports defeat the FQN-via-imports resolver below — flag any
                // whose package falls outside the allowlist so the leak test stays
                // verifiable. Star imports from allowed packages (e.g. kotlinx.*)
                // are fine because nothing they pull in can be a leak by definition.
                file.imports
                    .filter { it.isWildcard }
                    .forEach { imp ->
                        // Konsist drops the trailing `.*` from wildcard imports — `imp.name`
                        // here is the package itself. Normalize with a trailing dot so the
                        // prefix matches line up with the FQN-style allow/deny entries.
                        val pkgWithDot = "${imp.name}."
                        val denied = deniedPackagePrefixes.any { pkgWithDot.startsWith(it) }
                        val notAllowed = allowedPackagePrefixes.none { pkgWithDot.startsWith(it) }
                        if (denied || notAllowed) {
                            violations += "$where star import `${imp.name}.*` is outside allowed prefixes — " +
                                "leak test cannot verify types from this package"
                        }
                    }

                file.functions(includeNested = true)
                    .filter { it.hasPublicOrDefaultModifier }
                    .forEach { fn ->
                        fn.returnType?.let { type ->
                            violationFqn(type, file)?.let { fqn ->
                                violations += "$where fun ${fn.name}(...) returns $fqn"
                            }
                        }
                        fn.parameters.forEach { param ->
                            violationFqn(param.type, file)?.let { fqn ->
                                violations += "$where fun ${fn.name}(... ${param.name}: $fqn ...)"
                            }
                        }
                    }

                file.properties(includeNested = true)
                    .filter { it.hasPublicOrDefaultModifier }
                    .forEach { prop ->
                        prop.type?.let { type ->
                            violationFqn(type, file)?.let { fqn ->
                                violations += "$where val ${prop.name}: $fqn"
                            }
                        }
                    }
            }

        check(violations.isEmpty()) {
            buildString {
                appendLine("client public API references ${violations.size} disallowed type(s):")
                violations.forEach { appendLine("  - $it") }
                appendLine()
                append(
                    "Either wrap the type in a domain type, make the declaration `internal`, " +
                        "or extend allowedPackagePrefixes in PublicApiLeakTest (deliberate decision).",
                )
            }
        }
    }

    /**
     * Returns the fully-qualified name of [type] if it falls outside the
     * allowlist (or inside the denylist); null if it's allowed.
     *
     * Resolution uses the file's imports. A type with no matching import
     * resolves implicitly to the file's own package or `kotlin(.collections).*`,
     * both of which are on the allowlist, so the absence of a match is
     * treated as allowed.
     */
    private fun violationFqn(type: KoTypeDeclaration, file: KoFileDeclaration): String? {
        val fqn = resolveFqn(type, file) ?: return null
        if (deniedPackagePrefixes.any { fqn.startsWith(it) }) return fqn
        if (allowedPackagePrefixes.none { fqn.startsWith(it) }) return fqn
        return null
    }

    private fun resolveFqn(type: KoTypeDeclaration, file: KoFileDeclaration): String? {
        val shortName = type.name.substringBefore('<').substringAfterLast('.')
        return file.imports.firstOrNull { imp ->
            imp.name.substringAfterLast('.') == shortName
        }?.name
    }
}
